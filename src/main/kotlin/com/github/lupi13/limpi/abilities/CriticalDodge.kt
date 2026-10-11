package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.combat.DamageContext
import com.github.lupi13.limpi.quests.DodgeCliche
import com.github.lupi13.limpi.quests.Quest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.UUID

object CriticalDodge : DodgeVariant(
    grade = Grade.LEGENDARY,
    displayName = Component.text("치명적인 회피", NamedTextColor.RED),
    codeName = "critical_dodge",
    material = Material.TARGET,
    description = listOf(
        Component.text("회피", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("회피 직후의 첫 근접 공격이 강해지고,", NamedTextColor.WHITE),
        Component.text("그 공격으로 처치하면 쿨타임이 줄어듭니다.", NamedTextColor.WHITE)
    )
) {
    override val unlockQuest: Quest get() = DodgeCliche

    override val details: List<Component> by settingsCached {
        listOf(
            inheritLine(),
            Component.text("회피 후 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("window") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초 내의 첫 근접 공격이 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage_multiplier")}", NamedTextColor.GREEN))
                .append(Component.text("배의 피해를 입힙니다.", NamedTextColor.WHITE)),
            Component.text("이 공격으로 대상을 처치하면 쿨타임이 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("kill_cooldown_reduction") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초 줄어듭니다.", NamedTextColor.WHITE)),
            cooldownLine()
        )
    }

    /** 회피 후 아직 강화 공격을 사용하지 않은 플레이어. */
    private val armed: MutableSet<UUID> = mutableSetOf()
    /** HIGH에서 강화한 공격 이벤트. MONITOR에서 처치 여부를 확인한 뒤 비웁니다. */
    private var pending: EntityDamageByEntityEvent? = null

    override fun afterDodge(player: Player) {
        armed.add(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        if (DamageContext.current != null) return
        val player = event.damager as? Player ?: return
        if (player.ability != this) return
        if (!armed.remove(player.uniqueId)) return
        if (!dodgedWithin(player.uniqueId, config!!.getInt("window"))) return
        event.damage *= config!!.getDouble("damage_multiplier")
        player.world.playSound(player.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.5f, 1.5f)
        pending = event
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAttackResult(event: EntityDamageByEntityEvent) {
        if (pending !== event) return
        pending = null
        if (event.isCancelled) return
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (event.finalDamage < target.health) return
        val last = lastDodgeTime[player.uniqueId] ?: return
        lastDodgeTime[player.uniqueId] = last - config!!.getInt("kill_cooldown_reduction")
    }

    override fun release(owner: UUID) {
        super.release(owner)
        armed.remove(owner)
    }

    override fun stop() {
        super.stop()
        armed.clear()
        pending = null
    }
}
