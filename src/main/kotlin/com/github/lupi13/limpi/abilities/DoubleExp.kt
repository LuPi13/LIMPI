package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerExpChangeEvent

object DoubleExp : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("경험치 보너스", NamedTextColor.YELLOW),
    codeName = "double_exp",
    material = Material.EXPERIENCE_BOTTLE,
    description = listOf(
        Component.text("경험치 획득량이 2배로 증가합니다.", NamedTextColor.WHITE))
) {
    @EventHandler
    fun onPlayerGainExp(event: PlayerExpChangeEvent) {
        val player = event.player
        if (player.ability != this) return

        // 경험치 획득량을 2배로 증가
        event.amount *= 2
    }
}