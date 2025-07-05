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
        private lateinit var sellPriceFile: File
        private lateinit var shopRatioFile: File
        private lateinit var stockFile: File
        private lateinit var marketFile: File
        private lateinit var miscConfig: FileConfiguration
        private lateinit var sellPriceConfig: FileConfiguration
        private lateinit var shopRatioConfig: FileConfiguration
        private lateinit var stockConfig: FileConfiguration
        private lateinit var marketConfig: FileConfiguration

        /**
         * 플러그인 실행 시 작동, 플러그인 데이터 폴더 생성
         */
        fun setup() {
            if (!File(plugin.dataFolder.toString() + File.separator + "financial").exists()) {
                File(plugin.dataFolder.toString() + File.separator + "financial").mkdirs()
            }
            setupMisc()
            setupSellPrice()
            setupShopRatio()
            setupStock()
            setupMarket()
        }

        /**
         * misc.yml 생성, miscConfiguration 지정
         */
        private fun setupMisc() {
            miscFile = File(plugin.dataFolder.toString() + File.separator + "financial", "misc.yml")
            if (!miscFile.exists()) {
                try {
                    miscFile.createNewFile()
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            miscConfig = YamlConfiguration.loadConfiguration(miscFile)
        }

        fun getMiscConfig(): FileConfiguration {
            return miscConfig
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

        /**
         * sellPrice.yml 생성, sellPriceConfiguration 지정
         */
        private fun setupSellPrice() {
            sellPriceFile = File(plugin.dataFolder.toString() + File.separator + "financial", "sellPrice.yml")
            if (!sellPriceFile.exists()) {
                try {
                    sellPriceFile.createNewFile()
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            sellPriceConfig = YamlConfiguration.loadConfiguration(sellPriceFile)
        }

        fun getSellPriceConfig(): FileConfiguration {
            return sellPriceConfig
        }

        /**
         * saveMisc
         */
        fun saveSellPrice() {
            try {
                sellPriceConfig.save(sellPriceFile)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }

        /**
         * shopRatio.yml 생성, shopRatioConfiguration 지정
         */
        private fun setupShopRatio() {
            shopRatioFile = File(plugin.dataFolder.toString() + File.separator + "financial", "shopRatio.yml")
            if (!shopRatioFile.exists()) {
                try {
                    shopRatioFile.createNewFile()
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            shopRatioConfig = YamlConfiguration.loadConfiguration(shopRatioFile)
        }

        fun getShopRatioConfig(): FileConfiguration {
            return shopRatioConfig
        }

        /**
         * saveMisc
         */
        fun saveShopRatio() {
            try {
                shopRatioConfig.save(shopRatioFile)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }

        /**
         * stock.yml 생성, stockConfiguration 지정
         */
        private fun setupStock() {
            stockFile = File(plugin.dataFolder.toString() + File.separator + "financial", "stock.yml")
            if (!stockFile.exists()) {
                try {
                    stockFile.createNewFile()
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            stockConfig = YamlConfiguration.loadConfiguration(stockFile)
        }

        fun getStockConfig(): FileConfiguration {
            return stockConfig
        }

        /**
         * saveMisc
         */
        fun saveStock() {
            try {
                stockConfig.save(stockFile)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }


        /**
         * market.yml 생성, marketConfiguration 지정
         */
        private fun setupMarket() {
            marketFile = File(plugin.dataFolder.toString() + File.separator + "financial", "market.yml")
            if (!marketFile.exists()) {
                try {
                    marketFile.createNewFile()
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            marketConfig = YamlConfiguration.loadConfiguration(marketFile)
        }

        fun getMarketConfig(): FileConfiguration {
            return marketConfig
        }

        /**
         * saveMisc
         */
        fun saveMarket() {
            try {
                marketConfig.save(marketFile)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }

        fun makePlayerData(player: Player) {
            val uuid = player.uniqueId
            val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
            val config = YamlConfiguration.loadConfiguration(file)
            config["name"] = player.name
            config["money"] = plugin.config.getLong("InitialMoney")
            config["LEGENDARY.Count"] = 0
            config["LEGENDARY.Ceiling"] = 0
            config["EPIC.Count"] = 0
            config["EPIC.Ceiling"] = 0
            config["AbilityEnemy.TAMED"] = 0
            config["AbilityEnemy.NON_AGGRESSIVE"] = 2
            config["AbilityEnemy.NEUTRAL"] = 1
            config["AbilityEnemy.AGGRESSIVE"] = 2
            config["AbilityEnemy.PLAYER"] = 1
            savePlayerData(player, config)
        }


        fun getPlayerData(player: OfflinePlayer): FileConfiguration {
            val uuid = player.uniqueId
            val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
            return YamlConfiguration.loadConfiguration(file)
        }
        fun getPlayerData(player: Player): FileConfiguration {
            val uuid = player.uniqueId
            val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
            return YamlConfiguration.loadConfiguration(file)
        }
        fun getPlayerData(uuid: String): FileConfiguration {
            val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
            return YamlConfiguration.loadConfiguration(file)
        }


//        fun savePlayerData(player: Player, config: FileConfiguration) {
//            try {
//                val uuid = player.uniqueId
//                val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
//                config.save(file)
//            } catch (e: Exception) {
//                println("\u001B[31m" + e.message + "\u001B[0m")
//            }
//        }
        fun savePlayerData(player: OfflinePlayer, config: FileConfiguration) {
            try {
                val uuid = player.uniqueId
                val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
                config.save(file)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }
        fun savePlayerData(uuid: String, config: FileConfiguration) {
            try {
                val file = File(plugin.dataFolder.toString() + File.separator + "playerData", "$uuid.yml")
                config.save(file)
            } catch (e: Exception) {
                println("\u001B[31m" + e.message + "\u001B[0m")
            }
        }

        /**
         * String 형태의 플레이어이름으로 해당 유저의 Player 객체 반환
         * @param[name] displayName in String
         * @return Player, 못찾으면 null
         */
        fun findPlayerByName(name: String): Player? {
            for (player in Bukkit.getOfflinePlayers() + Bukkit.getOnlinePlayers()) {
                if (player.name.equals(name, true)) {
                    return player.player
                }
            }
            return null
        }


        /**
         * String 형태의 플레이어이름으로 해당 유저의 config 반환
         * @param[name] displayName in String
         * @return FileConfiguration, 못찾으면 null
         */
        fun getPlayerDataByName(name: String): FileConfiguration? {
            for (file in File(plugin.dataFolder.toString() + File.separator + "playerData").listFiles()!!) {
                val config = YamlConfiguration.loadConfiguration(file)
                if (config.getString("name")!!.equals(name, true)) {
                    return config
                }
            }
            return null
        }


        /**
         * String 형태의 플레이어 이름으로 해당 유저의 uuid 반환
         * @param[name] displayName in String
         * @return String, 못찾으면 null
         */
        fun getUUIDByName(name: String): String? {
            for (file in File(plugin.dataFolder.toString() + File.separator + "playerData").listFiles()!!) {
                val config = YamlConfiguration.loadConfiguration(file)
                if (config.getString("name")!!.equals(name, true)) {
                    return file.nameWithoutExtension
                }
            }
            return null
        }
    }
}
