package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

class JoinAndQuit: Listener {
    val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player: Player = event.player
        if (!player.hasPlayedBefore()) {
            FileManager.makePlayerData(player)

        }
        player.sendMessage(Component.text("환영합니다, ", NamedTextColor.WHITE)
            .append(Functions.playerDisplay(player))
            .append(Component.text("님!\n").color(NamedTextColor.WHITE))
            .append(Component.text("/financial help", NamedTextColor.LIGHT_PURPLE)
                .hoverEvent(HoverEvent.showText(Component.text("클릭하여 명령 실행", NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.runCommand("/financial help")))
            .append(Component.text(", ", NamedTextColor.WHITE))
            .append(Component.text("/ability help", NamedTextColor.LIGHT_PURPLE)
            .hoverEvent(HoverEvent.showText(Component.text("클릭하여 명령 실행", NamedTextColor.WHITE)))
            .clickEvent(ClickEvent.runCommand("/ability help")))
            .append(Component.text(" 명령어를 입력하여 도움말을 확인하세요.", NamedTextColor.WHITE)))
        player.sendMessage(Component.text("보라색 ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text("글씨를 눌러 바로 실행할 수도 있습니다!", NamedTextColor.WHITE)))
    }
}