package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.ExpAbsorber
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerLevelChangeEvent

object ExpRich : Ability(
    grade = Grade.MYTHIC,
    element = Element.NONE,
    displayName = Component.text("경험치 대가", NamedTextColor.YELLOW),
    codeName = "exp_rich",
    material = Material.NETHER_STAR,
    description = listOf(
        Component.text("경험치 획득량이 3배로 증가하며,", NamedTextColor.WHITE),
        Component.text("레벨업 시 2레벨을 추가로 상승시킵니다.", NamedTextColor.WHITE)
    )
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(ExpAbsorber.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    @EventHandler
    fun onPlayerGainExp(event: PlayerExpChangeEvent) {
        val player = event.player
        if (player.ability != this) return

        // 경험치 획득량을 2배로 증가
        event.amount *= 2
    }

    @EventHandler
    fun onLevelChange(event: PlayerLevelChangeEvent) {
        val player = event.player
        if (player.ability != this) return

        // 레벨업 시 2레벨을 추가로 상승
        player.level = event.newLevel + 2
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1f)
    }
}