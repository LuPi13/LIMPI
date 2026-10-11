package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.ShouldDodge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

object InvisibleDodge : DodgeVariant(
    grade = Grade.LEGENDARY,
    element = Element.MAGIC,
    displayName = Component.text("소멸의 걸음", NamedTextColor.DARK_GRAY),
    codeName = "invisible_dodge",
    material = Material.FROGSPAWN,
    description = listOf(
        Component.text("회피", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("회피 후 잠시 은신합니다.", NamedTextColor.WHITE),
        Component.text("공격하거나 피해를 입으면 은신이 풀립니다.", NamedTextColor.WHITE)
    )
) {
    override val unlockQuest: Quest get() = ShouldDodge
    override val relatedQuest: List<Quest> by lazy { listOf(com.github.lupi13.limpi.quests.IntoTheDark) }

    override val details: List<Component> by settingsCached {
        listOf(
            inheritLine(),
            Component.text("회피 후 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("invisible_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초 동안 ", NamedTextColor.WHITE))
                .append(Component.text("은신", NamedTextColor.BLUE))
                .append(Component.text("합니다.", NamedTextColor.WHITE)),
            Component.text("은신", NamedTextColor.BLUE)
                .append(Component.text(" 중에는 ", NamedTextColor.WHITE))
                .append(Component.text("갑옷", NamedTextColor.YELLOW))
                .append(Component.text("이 다른 플레이어에게 보이지 않고,", NamedTextColor.WHITE)),
            Component.text("주변 몹이 당신을 놓칩니다.", NamedTextColor.WHITE),
            Component.text("공격하거나 피해를 입으면 ", NamedTextColor.WHITE)
                .append(Component.text("은신", NamedTextColor.BLUE))
                .append(Component.text("이 풀립니다.", NamedTextColor.WHITE)),
            cooldownLine()
        )
    }

    private val active: MutableMap<UUID, AbilityEffect> = mutableMapOf()
    private val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    override fun afterDodge(player: Player) {
        val id = player.uniqueId
        // 어둠 속으로: 주변 5블록 안에 몬스터가 10마리 이상일 때 회피
        if (player.getNearbyEntities(5.0, 5.0, 5.0).count { it is org.bukkit.entity.Monster } >= 10) {
            com.github.lupi13.limpi.quests.QuestManager.clearQuests(player, com.github.lupi13.limpi.quests.IntoTheDark)
        }
        active.remove(id)?.close()
        val duration = config!!.getInt("invisible_ticks")
        val range = config!!.getDouble("target_clear_range")
        val effect = AbilityRuntime.begin(player, codeName)
        active[id] = effect
        effect.own {
            if (active[id] === effect) active.remove(id)
            effect.player()?.let { owner ->
                showArmor(owner)
                owner.playSound(owner.location, Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 1f)
            }
        }
        effect.potion(player, PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, true, false, true))
        var ticks = 0
        effect.repeat { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            if (ticks++ >= duration) return@repeat scope.close()
            // 갑옷은 실제로 벗기지 않고 다른 플레이어에게만 숨깁니다.
            val hidden = armorSlots.associateWith { ItemStack.empty() }
            owner.trackedBy.forEach { viewer -> viewer.sendEquipmentChange(owner, hidden) }
            owner.getNearbyEntities(range, range, range).forEach { entity ->
                if (entity is Mob && entity.target?.uniqueId == owner.uniqueId) entity.target = null
            }
        }
        player.world.playSound(player.location, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 2f)
    }

    private fun showArmor(owner: Player) {
        val equipment = owner.equipment
        val real = armorSlots.associateWith { equipment.getItem(it) }
        owner.trackedBy.forEach { viewer -> viewer.sendEquipmentChange(owner, real) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamaged(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        active[player.uniqueId]?.close()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val attacker = damager as? Player ?: (damager as? Projectile)?.shooter as? Player ?: return
        active[attacker.uniqueId]?.close()
    }

    override fun release(owner: UUID) {
        super.release(owner)
        active.remove(owner)?.close()
    }

    override fun stop() {
        super.stop()
        active.values.toList().forEach { it.close() }
        active.clear()
    }
}
