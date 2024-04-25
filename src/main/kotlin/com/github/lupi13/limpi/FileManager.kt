package com.github.lupi13.limpi

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin.getPlugin
import java.io.File

class FileManager {
    companion object {
        private var plugin: Plugin = getPlugin(LIMPI::class.java)
        private lateinit var miscFile: File
        lateinit var miscConfig: FileConfiguration

        /**
         * 플러그인 실행 시 작동, 플러그인 데이터 폴더 생성
         */
        fun setup() {
            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdirs()
            }
            setupMisc()
        }

        /**
         * misc.yml 생성, miscConfiguration 지정
         */
        private fun setupMisc() {
            miscFile = File(Bukkit.getServer().pluginManager.getPlugin("LIMPI")!!.dataFolder, "misc.yml")
            if (!miscFile.exists()) {
                try {
                    miscFile.createNewFile()
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            miscConfig = YamlConfiguration.loadConfiguration(miscFile)
        }

        /**
         * saveMisc
         */
        fun saveMisc() {
            try {
                miscConfig.save(miscFile)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }


        fun makePlayerData(player: Player) {
            val uuid = player.uniqueId
            val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
            val config = YamlConfiguration.loadConfiguration(file)
            config["name"] = player.displayName
            config["money"] = 10000L
            savePlayerData(player, config)
        }


        fun getPlayerData(player: OfflinePlayer): FileConfiguration {
            val uuid = player.uniqueId
            val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
            return YamlConfiguration.loadConfiguration(file)
        }


        fun savePlayerData(player: Player, config: FileConfiguration) {
            try {
                val uuid = player.uniqueId
                val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
                config.save(file)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }

        /**
         * String 형태의 플레이어이름으로 해당 유저의 config 반환
         * @param[name] displayname in String
         * @return 플레이어정보파일의 FileConfiguration, 못찾으면 null
         */
        fun findPlayerData(name: String): FileConfiguration? {
            for (player in Bukkit.getOfflinePlayers()) {
                if (player.name.equals(name)) {
                    return getPlayerData(player)
                }
            }
            return null
        }
    }
}
