package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.GiveAll
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.logging.Level

/**
 * 매의 눈 계열 공통 동작: 검 우클릭으로 주변 무작위 적 하나에 표식(발광)을 남기고,
 * 표식이 남은 적을 직접 처치하면 경험치를 늘리고 무작위 아이템을 추가로 떨어뜨립니다.
 * 추가 전리품은 사망 이벤트의 드롭 목록에만 더하며 인벤토리를 직접 건드리지 않습니다.
 * 설정: cooldown_ticks, horizontal_range, vertical_range, glow_ticks, exp_multiplier, extra_items, excluded_items(추가 아이템에서 뺄 아이템).
 */
abstract class HawkEyeAbility(
    grade: Grade,
    displayName: Component,
    codeName: String,
    description: List<Component>,
    acquisition: AcquisitionType,
    private val bonusSound: () -> Sound
) : Ability(
    grade = grade,
    displayName = displayName,
    codeName = codeName,
    material = Material.MUSIC_DISC_13,
    description = description,
    acquisition = acquisition,
    needFile = true
) {
    private val swords = listOf(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.COPPER_SWORD, Material.GOLDEN_SWORD,
        Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    )

    private class Mark(val target: UUID, val effect: AbilityEffect, val settings: com.github.lupi13.limpi.config.AbilitySettings)

    /** (월드, 설정의 제외 목록) → 후보. 월드의 활성 기능은 로드 중 바뀌지 않고, 설정을 다시 읽으면 목록이 달라진다. */
    private val candidatesByWorld = java.util.concurrent.ConcurrentHashMap<Pair<UUID, List<String>>, List<org.bukkit.inventory.ItemType>>()

    /** 플레이어 → 현재 표식. 발광 효과가 끝나면(시간 만료, 사망, 퇴장 등) 표식도 사라집니다. */
    private val marks: MutableMap<UUID, Mark> = mutableMapOf()

    /** 사망한 개체 → (처치자, 추가 아이템 수). 사망이 확정된 MONITOR 단계에서 퀘스트에 집계합니다. */
    private val pendingExtras: MutableMap<UUID, Pair<UUID, Int>> = mutableMapOf()

    /** 추가 아이템을 셀 퀘스트. null이면 집계하지 않습니다. 객체 초기화 순환을 피하려고 getter로 둡니다. */
    protected open val countedQuest: Quest? get() = null

    protected fun effectLines(): List<Component> = listOf(
        Component.text("검", NamedTextColor.YELLOW)
            .append(Component.text("을 우클릭하면 주변 ", NamedTextColor.WHITE))
            .append(Component.text("${config!!.getDouble("horizontal_range")}", NamedTextColor.GREEN))
            .append(Component.text("블럭 안의 무작위 ", NamedTextColor.WHITE))
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text(" 하나에게", NamedTextColor.WHITE)),
        Component.text("", NamedTextColor.WHITE)
            .append(Component.text("${config!!.getInt("glow_ticks") / 20.0}", NamedTextColor.GREEN))
            .append(Component.text("초", NamedTextColor.WHITE))
            .append(Component.text(" 동안 ", NamedTextColor.WHITE))
            .append(Component.text("발광", NamedTextColor.DARK_RED))
            .append(Component.text("을 부여합니다.", NamedTextColor.WHITE)),
        Component.text("발광", NamedTextColor.DARK_RED)
            .append(Component.text("이 부여된 ", NamedTextColor.WHITE))
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("을 직접 처치하면 경험치가 ", NamedTextColor.WHITE))
            .append(Component.text("${config!!.getInt("exp_multiplier")}", NamedTextColor.GREEN))
            .append(Component.text("배", NamedTextColor.WHITE))
            .append(Component.text("로 증가하며,", NamedTextColor.WHITE)),
        Component.text("무작위 아이템 ", NamedTextColor.WHITE)
            .append(Component.text("${config!!.getInt("extra_items")}", NamedTextColor.GREEN))
            .append(Component.text("개", NamedTextColor.WHITE))
            .append(Component.text("를 추가로 떨어뜨립니다.", NamedTextColor.WHITE)),
        Component.text("쿨타임: ", NamedTextColor.WHITE)
            .append(Component.text("${config!!.getInt("cooldown_ticks") / 20.0}", NamedTextColor.GREEN))
            .append(Component.text("초", NamedTextColor.WHITE))
            .append(Component.text(" (모든 ", NamedTextColor.WHITE))
            .append(Component.text("검", NamedTextColor.YELLOW))
            .append(Component.text(" 공유, 주변에 ", NamedTextColor.WHITE))
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("이 없으면 소모되지 않음)", NamedTextColor.WHITE))
    )

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || !event.action.isRightClick || event.useItemInHand() == Event.Result.DENY) return
        if (event.hand != EquipmentSlot.HAND) return
        val sword = event.item?.type ?: return
        if (sword !in swords) return
        if (FileManager.delivery.blocked(player.uniqueId) || player.isDead) return
        if (player.getCooldown(sword) > 0) return

        val horizontal = config!!.getDouble("horizontal_range")
        val vertical = config!!.getDouble("vertical_range")
        val target = player.getNearbyEntities(horizontal, vertical, horizontal)
            .filterIsInstance<LivingEntity>()
            .filter { it.isValid && !it.isDead && AbilityManager.isEnemy(player, it) }
            .randomOrNull()
        if (target == null) {
            player.sendActionBar(Component.text("주변에 적이 없습니다!", NamedTextColor.YELLOW))
            return
        }
        val cooldown = config!!.getInt("cooldown_ticks")
        swords.forEach { player.setCooldown(it, cooldown) }
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f)
        mark(player, target, config!!.getInt("glow_ticks"))
    }

    private fun mark(player: Player, target: LivingEntity, glowTicks: Int) {
        marks.remove(player.uniqueId)?.effect?.close()
        val effect = AbilityRuntime.begin(player, codeName)
        val mark = Mark(target.uniqueId, effect, requireNotNull(config))
        val owner = player.uniqueId
        marks[owner] = mark
        effect.own { if (marks[owner] === mark) marks.remove(owner) }
        try {
            effect.potion(target, PotionEffect(PotionEffectType.GLOWING, glowTicks, 0, true, false, false))
            // 첫 실행(glowTicks 뒤)에서 효과를 닫아 발광과 표식을 함께 정리합니다.
            effect.repeat(glowTicks.toLong(), 1) { it.close() }
        } catch (failure: Exception) {
            effect.close()
            throw failure
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onKill(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer ?: return
        val mark = marks[killer.uniqueId] ?: return
        if (mark.target != entity.uniqueId) return
        val config = mark.settings
        marks.remove(killer.uniqueId)
        mark.effect.close()
        if (killer.ability != this || FileManager.delivery.blocked(killer.uniqueId)) return

        val expMultiplier = config.getInt("exp_multiplier")
        val extraCount = config.getInt("extra_items")
        event.droppedExp = (event.droppedExp.toLong() * expMultiplier).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        // 원래 전리품은 늘리지 않습니다. 드롭 목록에는 몹이 주운 장비나 동물 상자 내용물도 들어 있어 복제가 됩니다.
        val extras = randomItems(entity.world, extraCount, config.getItemList("excluded_items"))
        event.drops.addAll(extras)
        if (extras.isNotEmpty()) {
            killer.playSound(killer.location, bonusSound(), 1f, 0.5f)
            if (countedQuest != null) pendingExtras[entity.uniqueId] = killer.uniqueId to extras.size
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onKillConfirmed(event: EntityDeathEvent) {
        val (owner, count) = pendingExtras.remove(event.entity.uniqueId) ?: return
        if (event.isCancelled) return
        val quest = countedQuest ?: return
        val player = plugin.server.getPlayer(owner) ?: return
        try {
            // 퀘스트 카운터는 호출마다 1씩 늘어나므로 추가 아이템 수만큼만 호출합니다.
            repeat(count) { QuestManager.progress(player, quest) }
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to record $codeName quest progress for ${player.name}", failure)
        }
    }

    /** 관리자 전용·생존에서 얻을 수 없는 아이템을 제외하고, 이 월드에서 활성화된 아이템 중 무작위로 고릅니다(중복 가능). */
    private fun randomItems(world: World, count: Int, configured: List<String>): List<ItemStack> {
        if (count <= 0) return emptyList()
        // A world's enabled features are fixed while it is loaded, so its candidate list is computed once per exclusion list.
        val candidates = candidatesByWorld.getOrPut(world.uid to configured) {
            Registry.ITEM.filter { type ->
                type.key.namespace == "minecraft" && type.key.key !in EXCLUDED_ITEMS && type.key.key !in configured &&
                    EXCLUDED_PATTERNS.none { it.matches(type.key.key) } &&
                    world.isEnabled(type)
            }
        }
        if (candidates.isEmpty()) return emptyList()
        return List(count) { candidates.random().createItemStack(1) }
    }

    override fun release(owner: UUID) { marks.remove(owner)?.effect?.close() }
    override fun stop() {
        marks.values.toList().forEach { it.effect.close() }
        marks.clear()
        pendingExtras.clear()
    }

    companion object {
        val GIVE_ALL_COUNTER get() = com.github.lupi13.limpi.quests.QuestProgressDefinitions.HAWK_EYE.counter
        val GIVE_ALL_TARGET get() = com.github.lupi13.limpi.quests.QuestProgressDefinitions.HAWK_EYE.target

        /** 원본의 제한 목록에 대응하는 관리자 전용/획득 불가 아이템 ID입니다. */
        /** 스폰 알과 좀벌레 블록(infested_*)도 생존에서 얻을 수 없습니다. */
        private val EXCLUDED_PATTERNS = listOf(Regex(".+_spawn_egg"), Regex("infested_.+"))

        private val EXCLUDED_ITEMS = setOf(
            "air", "spawner", "trial_spawner", "command_block", "chain_command_block", "repeating_command_block",
            "command_block_minecart", "structure_block", "structure_void", "jigsaw", "barrier", "debug_stick",
            "light", "test_block", "test_instance_block", "knowledge_book",
            // 생존에서 얻을 수 없는 아이템 (사용자 확정 2026-10-11)
            "bedrock", "end_portal_frame", "reinforced_deepslate", "budding_amethyst", "vault", "petrified_oak_slab",
            "farmland", "dirt_path", "chorus_plant", "frogspawn", "player_head", "dragon_egg",
            "suspicious_sand", "suspicious_gravel"
        )
    }
}

object HawkEye : HawkEyeAbility(
    grade = Grade.EPIC,
    displayName = Component.text("매의 눈", NamedTextColor.YELLOW),
    codeName = "hawk_eye",
    description = listOf(
        Component.text("검을 우클릭하면 주변 무작위 ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text(" 하나에게", NamedTextColor.WHITE)),
        Component.text("발광을 부여합니다. 발광이 부여된 적을 처치하면", NamedTextColor.WHITE),
        Component.text("전리품이 2배로 증가하며, 무작위", NamedTextColor.WHITE),
        Component.text("아이템 하나를 추가로 떨어뜨립니다.", NamedTextColor.WHITE),
        Component.text("주의! 플레이어의 전리품은 2배로 증가하지 않습니다.", NamedTextColor.YELLOW)
    ),
    acquisition = AcquisitionType.GACHA,
    bonusSound = { Sound.ITEM_ARMOR_EQUIP_LEATHER }
) {
    override val countedQuest: Quest get() = GiveAll

    override val relatedQuest: List<Quest> by lazy { listOf(GiveAll) }

    override val details: List<Component> by settingsCached { effectLines() }
}
