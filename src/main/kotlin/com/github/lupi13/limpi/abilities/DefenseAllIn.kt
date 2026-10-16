package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

object DefenseAllIn : Ability(
    grade = Grade.RARE,
    displayName = Component.text("아픈 건 싫으니까 방어력에 올인하려고 합니다.", NamedTextColor.LIGHT_PURPLE),
    codeName = "defense_all_in",
    material = Material.NETHERITE_CHESTPLATE,
    description = listOf(
        Component.text("피격 후 무적시간이 증가합니다.", NamedTextColor.WHITE),
        Component.text("받는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
        Component.text("가하는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
    ),
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("피격 후 무적시간이 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getInt("noDamageMilliseconds") / 1000.0, NamedTextColor.GREEN))
                .append(Component.text("초로 증가합니다.", NamedTextColor.WHITE)),
            Component.text("받는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
            Component.text("가하는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
        )
    }


    val lastDamageTime = mutableMapOf<java.util.UUID, Long>()
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (player.ability != this) return
        if (config!!.getInt("noDamageMilliseconds") > 0 && lastDamageTime.containsKey(player.uniqueId) && (System.nanoTime() / 1_000_000 - lastDamageTime[player.uniqueId]!! < config!!.getInt("noDamageMilliseconds"))) {
            event.isCancelled = true
            return
        }

        event.setDamage(1.0)

    }


    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        if (event.damager is Player) {
            val player = event.damager as Player
            if (player.ability != this) return

            event.damage = 1.0
            event.setDamage(1.0)
        }

        else if (event.damager is Projectile) {
            if ((event.damager as Projectile).shooter !is Player) return
            val player = (event.damager as Projectile).shooter as Player
            if (player.ability != this) return

            event.damage = 1.0
            event.setDamage(1.0)
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun confirmed(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability == this && event.finalDamage > 0) lastDamageTime[player.uniqueId] = System.nanoTime() / 1_000_000
    }
    override fun release(owner: java.util.UUID) { lastDamageTime.remove(owner) }
    override fun stop() { lastDamageTime.clear() }

}
