package com.github.lupi13.limpi

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

class Functions {
    companion object {
        private var plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)


        public fun getOnlinePlayers(): List<Player> {
            var playerList = mutableListOf<Player>()
            plugin.server.onlinePlayers.forEach {playerList.add(it)}

            return playerList
        }

        public fun getPlayers(): List<Player> {
            var playerList = mutableListOf<Player>()
            getOnlinePlayers().forEach {playerList.add(it)}
            plugin.server.offlinePlayers.forEach {playerList.add(it as Player)}

            return playerList
        }
    }

}