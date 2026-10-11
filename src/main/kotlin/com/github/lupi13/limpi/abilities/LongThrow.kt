package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.LingeringPotion
import org.bukkit.entity.Player
import org.bukkit.entity.SplashPotion
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerDropItemEvent

object LongThrow : Ability(
    grade = Grade.COMMON,
    element = Element.UTILITY,
    displayName = Component.text("장거리 투척", NamedTextColor.WHITE),
    codeName = "long_throw",
    material = Material.RABBIT_HIDE,
    description = listOf(
        Component.text("아이템, ", NamedTextColor.WHITE).append(Component.text("투척용 물약", NamedTextColor.YELLOW)).append(Component.text(", ", NamedTextColor.WHITE)).append(Component.text("잔류형 물약", NamedTextColor.YELLOW)).append(Component.text("을", NamedTextColor.WHITE)),
        Component.text("매우 멀리 던집니다.", NamedTextColor.WHITE)),
    needFile = true
){


    @EventHandler(ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        if (player.ability != this) return

        val itemAmp = config!!.getDouble("item")

        event.itemDrop.velocity = player.location.direction.normalizeSafely().multiply(itemAmp)
    }


    @EventHandler(ignoreCancelled = true)
    fun onPotionLaunch(event: ProjectileLaunchEvent) {
        val entity = event.entity
        if (!(entity is SplashPotion || entity is LingeringPotion)) return
        if (event.entity.shooter == null) return
        if (entity.shooter !is Player) return

        val player = entity.shooter as Player
        if (player.ability != this) return

        val potionAmp = if (entity is SplashPotion) {
            config!!.getDouble("splash_potion")
        } else {
            config!!.getDouble("lingering_potion")
        }

        entity.velocity = player.location.direction.normalizeSafely().multiply(potionAmp)
    }
}
