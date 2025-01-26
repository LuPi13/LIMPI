package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
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
        //player.sendMessage("joined.")
    }
}