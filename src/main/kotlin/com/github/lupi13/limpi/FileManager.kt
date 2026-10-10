package com.github.lupi13.limpi

import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.YamlFiles
import com.github.lupi13.limpi.storage.YamlPlayerRepository
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Level

/** Compatibility facade while feature services replace direct YAML access. */
class FileManager {
    companion object {
        private lateinit var plugin: Plugin
        private lateinit var repository: PlayerRepository
        private val financial = mutableMapOf<String, YamlConfiguration>()
        val players: PlayerRepository get() = repository

        fun initialize(owner: Plugin) {
            plugin = owner
            repository = YamlPlayerRepository(owner.dataFolder.toPath().resolve("playerData"))
            financial.clear()
        }

        fun setup() {
            for (name in listOf("misc", "sellPrice", "shopRatio", "stock", "market")) {
                financial[name] = YamlFiles.read(financialPath(name))
            }
        }

        fun getMiscConfig(): FileConfiguration = financial.getValue("misc")
        fun getSellPriceConfig(): FileConfiguration = financial.getValue("sellPrice")
        fun getShopRatioConfig(): FileConfiguration = financial.getValue("shopRatio")
        fun getStockConfig(): FileConfiguration = financial.getValue("stock")
        fun getMarketConfig(): FileConfiguration = financial.getValue("market")

        fun saveMisc() = saveFinancial("misc")
        fun saveSellPrice() = saveFinancial("sellPrice")
        fun saveShopRatio() = saveFinancial("shopRatio")
        fun saveStock() = saveFinancial("stock")
        fun saveMarket() = saveFinancial("market")

        /** Idempotent initialization: existing money, abilities and unknown fields are preserved. */
        fun makePlayerData(player: Player) = persist("initialize ${player.uniqueId}") {
            repository.ensure(player.uniqueId, player.name, plugin.config.getLong("InitialMoney"))
            repository.pin(player.uniqueId)
        }

        fun getPlayerData(player: OfflinePlayer): FileConfiguration = repository.snapshot(player.uniqueId)
        fun getPlayerData(player: Player): FileConfiguration = repository.snapshot(player.uniqueId)
        fun getPlayerData(uuid: String): FileConfiguration = repository.snapshot(UUID.fromString(uuid))

        fun savePlayerData(player: OfflinePlayer, config: FileConfiguration) = persist("save ${player.uniqueId}") {
            repository.save(player.uniqueId, config)
        }
        fun savePlayerData(uuid: String, config: FileConfiguration) = persist("save $uuid") {
            repository.save(UUID.fromString(uuid), config)
        }

        fun findPlayerByName(name: String): Player? = Bukkit.getOnlinePlayers().firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }

        fun getPlayerDataByName(name: String): FileConfiguration? = repository.findByName(name)?.let(repository::snapshot)
        fun getUUIDByName(name: String): String? = repository.findByName(name)?.toString()

        fun releasePlayer(player: Player) = repository.release(player.uniqueId)
        fun close() {
            if (::repository.isInitialized) repository.close()
            financial.clear()
        }

        private fun financialPath(name: String): Path = plugin.dataFolder.toPath().resolve("financial/$name.yml")
        private fun saveFinancial(name: String) = persist("save financial/$name.yml") {
            try {
                YamlFiles.write(financialPath(name), financial.getValue(name))
            } catch (failure: Exception) {
                financial[name] = YamlFiles.read(financialPath(name))
                throw failure
            }
        }

        private inline fun persist(description: String, action: () -> Unit) {
            try {
                action()
            } catch (failure: Exception) {
                plugin.logger.log(Level.SEVERE, "Unable to $description; operation was not completed", failure)
                throw failure
            }
        }
    }
}
