package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityTargetLivingEntityEvent

object FriendlyCreeper : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("우호적 크리퍼", NamedTextColor.GREEN),
    codeName = "friendly_creeper",
    material = Material.CREEPER_HEAD,
    description = listOf(
        Component.text("크리퍼가 나를 적대하지 않습니다.", NamedTextColor.WHITE))
) {
    @EventHandler
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        val target = event.target
        if (target == null || (event.target !is Player)) return
        val player = target as Player
        if (player.ability != this) return

        val entity = event.entity
        if (entity.type != EntityType.CREEPER) return
        event.isCancelled = true
    }
}