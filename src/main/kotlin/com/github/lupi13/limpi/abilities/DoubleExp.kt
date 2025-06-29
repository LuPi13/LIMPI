package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerExpChangeEvent

object DoubleExp : Ability(
    grade = Grade.EPIC,
    displayName = Component.text("경험치 보너스"),
    codeName = "double_exp",
    material = Material.EXPERIENCE_BOTTLE,
    description = listOf(
        Component.text("경험치 획득량이 2배로 증가합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)),
    restrictedSlot = null,
    attribute = Attribute.EXPLOSIVE
) {
    @EventHandler
    fun onPlayerGainExp(event: PlayerExpChangeEvent) {
        val player = event.player
        if (player.ability != this) return

        // 경험치 획득량을 2배로 증가
        event.amount *= 2
    }
}