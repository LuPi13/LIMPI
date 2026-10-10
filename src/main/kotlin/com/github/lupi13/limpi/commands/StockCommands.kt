package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.events.StockEvents
import com.github.lupi13.limpi.stock.StockCatalog
import com.github.lupi13.limpi.stock.StockCatalogEdit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class StockCommands {
    fun execute(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase(java.util.Locale.ROOT)) {
            null -> StockEvents.openStockGUI(sender as Player)
            "show" -> StockEvents.showBook(sender as Player)
            "holdings" -> {
                val data = FileManager.players.snapshot((sender as Player).uniqueId)
                val codes = data.getConfigurationSection("stock")?.getKeys(false).orEmpty().filter { it.matches(Regex("[A-Z0-9]{4}")) }
                if (codes.isEmpty()) sender.sendMessage("보유한 주식이 없습니다.")
                for (code in codes.sorted()) {
                    val holding = FileManager.stocks.holding(data, code)
                    val catalog = FileManager.getStockConfig()
                    val listed = code in StockCatalog.codes(catalog)
                    sender.sendMessage("${catalog.getString("$code.name") ?: code} ($code): ${holding.count}주, 취득 원가 ${holding.costBasis.units}원, 평균 ${holding.averagePrice}원, 실현 손익 ${data.get("stock.$code.realizedProfit") ?: 0}원${if (listed) "" else " (상장 중지)"}")
                }
            }
            "add" -> {
                val material = Functions.toMaterial(args[4]); require(material.isItem && !material.isAir) { "올바른 아이템 material을 지정하세요." }
                val price = Money(requireNotNull(args[5].toLongOrNull()) { "가격은 Long 범위 양수 정수로 입력하세요." })
                FileManager.replaceStock(StockCatalogEdit.add(FileManager.getStockConfig(), args[2], args[3], material.name, price, Functions.getDateTime()))
                sender.sendMessage("주식 ${args[2]}(${args[3]})을 추가했습니다.")
            }
            else -> {
                val action = args[1].lowercase(java.util.Locale.ROOT)
                FileManager.replaceStock(StockCatalogEdit.edit(FileManager.getStockConfig(), action, args[2], Functions.getDateTime()))
                sender.sendMessage("주식 ${args[2]}: $action 저장 완료. 플레이어 보유 기록은 유지됩니다.")
            }
        }
    }
}
