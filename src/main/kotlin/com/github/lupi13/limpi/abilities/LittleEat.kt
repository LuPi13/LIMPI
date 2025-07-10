package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerItemConsumeEvent

object LittleEat : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("소식가", NamedTextColor.GRAY),
    codeName = "little_eat",
    material = Material.DRIED_KELP,
    description = listOf(
        Component.text("무엇을 먹든 허기가 가득 찹니다.", NamedTextColor.WHITE),
        Component.text("대신 허기가 2배 빠르게 줄어듭니다.", NamedTextColor.WHITE)
    )
) {

    @EventHandler
    fun onEat(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (player.ability != this) return

        player.foodLevel = 22
    }

    @EventHandler
    fun onFoodChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability != this) return

        event.foodLevel = player.foodLevel - 2
    }
}