package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.stock.StockCatalog
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.logging.Level

open class CommandCompletion(private val plugin: Plugin, private val index: CommandIndex, private val module: String) : TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return try {
            index.complete(args.toList(), plugin.config.getBoolean(module), sender is Player, sender::hasPermission) { kind ->
                when (kind) {
                    "online" -> plugin.server.onlinePlayers.map { it.name }
                    "players" -> FileManager.players.knownNames()
                    "abilities" -> AbilityManager.abilities.map { it.codeName } + "none"
                    "materials" -> Material.entries.filter { it.isItem && !it.isAir }.map { it.name }
                    "stocks" -> StockCatalog.codes(FileManager.getStockConfig()) + "all"
                    "archivedStocks" -> StockCatalog.allCodes(FileManager.getStockConfig()).filter { FileManager.getStockConfig().get("$it.listed") == false } + "all"
                    "listings" -> FileManager.market.available().filter {
                        it.state == com.github.lupi13.limpi.market.ListingState.ACTIVE &&
                            (sender.hasPermission("limpi.financial.admin.market") || (sender as? Player)?.uniqueId == it.seller)
                    }.map { it.code }
                    "deliveryDecision" -> listOf("delivered", "retry")
                    "removalDecision" -> listOf("removed", "retained")
                    "transitionDecision" -> listOf("applied", "retained")
                    else -> emptyList()
                }
            }
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to complete ${command.name} for ${sender.name}", failure)
            emptyList()
        }
    }
}
