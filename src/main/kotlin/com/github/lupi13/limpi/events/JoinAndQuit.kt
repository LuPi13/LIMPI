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
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

class JoinAndQuit: Listener {
    val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        val player: Player = event.player
        try {
            FileManager.makePlayerData(player)
        } catch (failure: Exception) {
            // A malformed/future profile or DB failure must not leave a player active without a canonical profile.
            FileManager.delivery.reject(player, failure)
            return
        }
        player.sendMessage(welcome(player, plugin.config.getString("HelpUrl")))
        // 환영 메시지 뒤에 미수령/전환 기록을 복구합니다. 복구 대기 여부와 관계없이 환영 메시지는 표시합니다.
        FileManager.delivery.claim(player, freshLogin = true)
        com.github.lupi13.limpi.quests.MasterSmith.check(player)
        com.github.lupi13.limpi.commands.Notice.announceUnread(plugin, player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        // Gathered quest counts are written while the profile is still pinned.
        com.github.lupi13.limpi.quests.QuestManager.flush(event.player.uniqueId)
        FileManager.releasePlayer(event.player)
        FileManager.delivery.release(event.player.uniqueId)
    }

    companion object {
        /** One line: the greeting and a single help link (the web site, or /limpi help when HelpUrl is blank). */
        fun welcome(player: Player, helpUrl: String?): Component {
            val home = com.github.lupi13.limpi.commands.WebHelp.home(helpUrl)
            val link = Component.text("[도움말 보기]", NamedTextColor.LIGHT_PURPLE)
                .hoverEvent(HoverEvent.showText(Component.text(if (home != null) "클릭하면 브라우저에서 엽니다." else "클릭하여 명령 실행", NamedTextColor.WHITE)))
                .clickEvent(if (home != null) ClickEvent.openUrl(home) else ClickEvent.runCommand("/limpi help"))
            return Component.text("환영합니다, ", NamedTextColor.WHITE).append(Functions.playerDisplay(player))
                .append(Component.text("님! ", NamedTextColor.WHITE)).append(link)
        }
    }
}
