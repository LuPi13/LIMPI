package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityDamage
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.NoHurt
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.QuestConditions
import com.github.lupi13.limpi.quests.QuestManager
import com.github.lupi13.limpi.quests.WhyDidYouDoThat
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Hanging
import org.bukkit.entity.Item
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.hanging.HangingBreakEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.random.Random

/**
 * 차지샷과 차지샷+가 공유하는 록 버스터: 충전, 선형 피해, 탄환.
 * 하위 능력은 [onChargeTick], [initialTicks], [damage], [onKill], [extraDetails]로 차이를 더합니다.
 */
abstract class ChargeShotAbility(
    grade: Grade,
    displayName: Component,
    codeName: String,
    description: List<Component>,
) : Ability(
    grade = grade,
    acquisition = AcquisitionType.QUEST,
    displayName = displayName,
    codeName = codeName,
    material = Material.LIGHT_BLUE_DYE,
    description = description,
    needFile = true,
    restrictedSlot = 40
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("왼손이 ", NamedTextColor.WHITE)
                .append(Component.text("록 버스터", NamedTextColor.YELLOW))
                .append(Component.text("로 고정됩니다.", NamedTextColor.WHITE)),
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 충전을 시작하고, 다시 눌러 발사합니다.", NamedTextColor.WHITE)),
            Component.text("최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("max_charge_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초간 충전하여 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 최대 ", NamedTextColor.WHITE))
                .append(Component.text("${maxDamage()}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 줍니다.", NamedTextColor.WHITE)),
            Component.text("충전 중에는 이동 속도가 ", NamedTextColor.WHITE)
                .append(Component.text("${Math.round(config!!.getDouble("charging_slowness") * 100)}", NamedTextColor.GREEN))
                .append(Component.text("%", NamedTextColor.WHITE))
                .append(Component.text(" 느려집니다.", NamedTextColor.WHITE)),
            Component.text("발사 후 쿨타임: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
        ) + extraDetails()
    }

    /** 상세 정보 끝에 붙는 능력별 설명. */
    protected open fun extraDetails(): List<Component> = emptyList()

    override val activeItem: ItemStack by lazy {
        val item = ItemStack(Material.TURTLE_SCUTE)
        val meta = item.itemMeta
        meta.itemModel = NamespacedKey("minecraft", "light_blue_dye")
        meta.itemName(Component.text("록 버스터", NamedTextColor.AQUA))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 충전, 사격하세요.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
        ))
        if (glint) meta.setEnchantmentGlintOverride(true)
        item.itemMeta = meta
        item
    }

    private fun maxDamage(): Double = config!!.getDouble("max_damage")

    /** Linear in the charge: max_damage × min(ticks, max_charge_ticks) / max_charge_ticks. */
    protected open fun damage(settings: com.github.lupi13.limpi.config.AbilitySettings, ticks: Int): Double {
        val maxCharge = settings.getInt("max_charge_ticks")
        return settings.getDouble("max_damage") * minOf(ticks, maxCharge) / maxCharge
    }

    /** 충전 중인 플레이어: 효과와 충전 틱. 효과가 닫히면(사망, 능력 변경 등) 발사하지 않고 충전이 취소됩니다. */
    protected class Charge(val effect: AbilityEffect, val settings: com.github.lupi13.limpi.config.AbilitySettings, var ticks: Int = 0)
    protected val charging: MutableMap<UUID, Charge> = mutableMapOf()
    /** 마지막 발사 시각(ms). 쿨타임 동안에는 새 충전을 시작하지 않아, 키를 누르고 있어도 연사되지 않습니다. */
    protected val lastShot: MutableMap<UUID, Long> = mutableMapOf()

    /** 매 충전 틱(틱 증가와 효과음 뒤)에 호출됩니다. false면 이 틱의 게이지 표시를 건너뜁니다(충전을 끝낸 경우). */
    protected open fun onChargeTick(player: Player, charge: Charge): Boolean = true
    /** 새 충전이 시작될 때의 충전 틱. */
    protected open fun initialTicks(player: Player): Int = 0
    /** 이 능력의 탄환이 대상을 처치했을 때. */
    protected open fun onKill(player: Player) {}
    /** 발사할 때(충전 틱 [ticks]로 피해가 정해진 직후). */
    protected open fun onFire(player: Player, settings: com.github.lupi13.limpi.config.AbilitySettings, ticks: Int) {}

    /** 충전 효과음(틱 → 음높이). 원작의 상승 아르페지오입니다. */
    private val melody = mapOf(
        1 to 0.667420f, 3 to 0.83371f, 4 to 0.916855f, 5 to 1f,
        7 to 0.707017f, 9 to 0.9147395f, 10 to 1.01860075f, 11 to 1.122462f,
        13 to 0.749154f, 15 to 1.0045375f, 16 to 1.13222925f, 17 to 1.259921f,
        19 to 0.793071f, 21 to 1.0639555f, 22 to 1.19939775f, 23 to 1.33484f,
        25 to 0.840896f, 27 to 1.127555f, 28 to 1.2708845f, 29 to 1.414214f,
        31 to 0.943874f, 33 to 1.2656375f, 34 to 1.42651925f, 35 to 1.587401f
    )
    /** 완충 후 반복되는 음(틱 % 6 → 음높이). */
    private val loop = mapOf(1 to 1f, 3 to 1.3408965f, 4 to 1.51134475f, 5 to 1.681793f)
    private companion object { const val PITCH_SHIFT = 0.167420f }

    private val gaugeColors = listOf(
        NamedTextColor.GRAY, NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, NamedTextColor.BLUE,
        NamedTextColor.AQUA, NamedTextColor.DARK_GREEN, NamedTextColor.GREEN, NamedTextColor.YELLOW,
        NamedTextColor.GOLD, NamedTextColor.RED, NamedTextColor.DARK_RED
    )

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this || FileManager.delivery.blocked(player.uniqueId)) return
        event.isCancelled = true
        val current = charging[player.uniqueId]
        if (current != null && !current.effect.scope.closed) { fire(player, current); return }
        val cooldown = config!!.getInt("cooldown_ticks") * 50
        val last = lastShot[player.uniqueId]
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            AbilityManager.showCooldown(player, cooldown, last)
            return
        }
        startCharge(player)
    }

    private fun startCharge(player: Player) {
        val config = requireNotNull(config)
        val id = player.uniqueId
        val maxCharge = config.getInt("max_charge_ticks")
        val effect = AbilityRuntime.begin(player, codeName)
        val charge = Charge(effect, config, initialTicks(player))
        charging[id] = charge
        effect.own { if (charging[id] === charge) charging.remove(id) }
        val slowness = config.getDouble("charging_slowness")
        if (slowness > 0) effect.attribute(Attribute.MOVEMENT_SPEED, -slowness, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        effect.repeat { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            val ticks = ++charge.ticks
            playChargeSound(owner, ticks)
            if (!onChargeTick(owner, charge)) return@repeat

            val amp = minOf(ticks, maxCharge)
            val filled = (amp * 40L / maxCharge).toInt().coerceIn(0, 40)
            val color = gaugeColors[(filled / 4).coerceIn(0, gaugeColors.size - 1)]
            owner.sendActionBar(Component.text("CHARGE 【${"┃".repeat(filled)}${"·".repeat(40 - filled)}】 $amp", color, TextDecoration.BOLD))

            val visual = minOf(40, ticks)
            val dust = Particle.DustOptions(Color.fromRGB(visual + 125 + Random.nextInt(60), 245 + Random.nextInt(10), 245 + Random.nextInt(10)),
                (visual * 0.01f).coerceAtLeast(0.01f))
            owner.world.spawnParticle(Particle.DUST, offHand(owner), minOf(10, ticks / 4 + 1), 0.05, 0.05, 0.05, 0.01, dust, true)
        }
    }

    private fun playChargeSound(player: Player, ticks: Int) {
        val note = melody[ticks]
        if (note != null) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, note - PITCH_SHIFT)
            return
        }
        if (ticks < 36) return
        val pitch = loop[ticks % 6] ?: return
        val volume = (1f - (ticks - 40) / 60f).coerceIn(0f, 1f)
        if (volume > 0f) player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BIT, volume, pitch - PITCH_SHIFT)
    }

    /** 왼손 앞(눈 위치에서 앞 0.2, 왼쪽 0.2, 아래 0.1)의 위치. */
    private fun offHand(player: Player): Location {
        val direction = player.location.direction
        val right = direction.clone().crossProduct(Vector(0, 1, 0))
        if (right.lengthSquared() < 1.0e-8) right.setX(1.0) // 수직으로 볼 때
        right.normalize()
        val up = right.clone().crossProduct(direction).normalize()
        return player.eyeLocation.add(direction.clone().multiply(0.2)).add(right.multiply(-0.2)).add(up.multiply(-0.1))
    }

    private fun fire(player: Player, charge: Charge) {
        val config = charge.settings
        val ticks = charge.ticks
        charge.effect.close() // 충전 종료(감속 해제)
        lastShot[player.uniqueId] = System.currentTimeMillis()
        val damage = damage(config, ticks)
        onFire(player, config, ticks)
        val lifetime = config.getInt("lifetime_ticks")
        val size = damage / 20.0 + 0.5
        val step = player.location.direction.multiply(size)
        val world = player.world
        var position = offHand(player).toVector()
        world.playSound(player.location, Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f)

        val effect = AbilityRuntime.begin(player, codeName)
        var age = 0
        effect.repeat { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            if (++age >= lifetime) return@repeat scope.close()
            position = position.add(step)
            val location = position.toLocation(world)
            val dust = Particle.DustOptions(Color.fromRGB((damage * 4).toInt() + 125 + Random.nextInt(60), 245 + Random.nextInt(10), 245 + Random.nextInt(10)),
                (damage * 0.1 + 1.0).toFloat())
            val spread = damage / 80.0 + 0.125
            world.spawnParticle(Particle.DUST, location, (damage / 2.0).toInt() + 16, spread, spread, spread, 0.01, dust, true)
            world.playSound(location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, (damage * 0.15 + 0.5).toFloat())

            val box = BoundingBox.of(position, size / 2.0, size / 2.0, size / 2.0)
            var hit = false
            for (entity in world.getNearbyEntities(box)) {
                if (entity !is Damageable || entity == owner || !AbilityManager.isEnemy(owner, entity)) continue
                hit = true
                if (AbilityDamage.hit(owner, entity, codeName, damage) && entity.isDead) onKill(owner)
            }
            if (hit) {
                world.playSound(location, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 2f)
                return@repeat scope.close()
            }
            if (!location.block.isPassable) scope.close()
        }
    }

    override fun release(owner: UUID) {
        charging.remove(owner)?.effect?.close()
    }

    override fun resetCooldowns(owner: UUID) {
        lastShot.remove(owner)
    }

    override fun stop() {
        lastShot.clear()
        charging.values.toList().forEach { it.effect.close() }
        charging.clear()
    }
}

object ChargeShot : ChargeShotAbility(
    grade = Grade.RARE,
    displayName = Component.text("차지샷", NamedTextColor.AQUA),
    codeName = "charge_shot",
    description = listOf(
        Component.text("왼손이 록 버스터로 고정됩니다.", NamedTextColor.WHITE),
        Component.text("")
            .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
            .append(Component.text("키를 눌러 충전을 시작하고,", NamedTextColor.WHITE)),
        Component.text("다시 눌러 발사합니다.", NamedTextColor.WHITE),
        Component.text("충전 중에는 이동 속도가 느려집니다.", NamedTextColor.WHITE)
    )
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(NoHurt.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val relatedQuest: List<Quest> by lazy { listOf(WhyDidYouDoThat) }

    private const val OVERLOAD_POWER = 4.0f

    /** 과충전 폭발 중인 플레이어. 이 폭발 동안 땅에 떨어진 아이템과 액자·그림은 지킵니다. */
    private var overloading: UUID? = null

    override fun onChargeTick(player: Player, charge: Charge): Boolean {
        if (charge.ticks == 1) QuestManager.recordUse(player, WhyDidYouDoThat)
        if (charge.ticks < QuestConditions.CHARGE_SHOT_OVERLOAD_TICKS) return true
        // 도대체 왜 그러셨어요...: 5분 충전하면 퀘스트를 확정한 뒤 그 자리에서 터집니다.
        charge.effect.close()
        QuestManager.clearQuests(player, WhyDidYouDoThat)
        overload(player)
        return false
    }

    /** 지형 파괴·점화 없이, 적 구분 없이 주변 모두에게 피해를 주는 폭발. 폭발을 견디면 같은 폭발 피해로 쓰러집니다. */
    private fun overload(player: Player) {
        val location = player.location.add(0.0, 1.0, 0.0)
        val world = player.world
        overloading = player.uniqueId
        try {
            world.createExplosion(player, location, OVERLOAD_POWER, false, false, false)
            if (!player.isDead) player.damage(1_000_000.0, DamageSource.builder(DamageType.PLAYER_EXPLOSION)
                .withCausingEntity(player).withDirectEntity(player).build())
        } finally { overloading = null }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun protectDrops(event: EntityDamageEvent) {
        if (overloading != null && (event.entity is Item || event.entity is Hanging)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun protectHanging(event: HangingBreakEvent) { if (overloading != null) event.isCancelled = true }
}
