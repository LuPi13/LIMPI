package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.events.StockEvents
import com.github.lupi13.limpi.stock.StockCatalog
import com.github.lupi13.limpi.stock.StockCatalogEdit
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
                if (codes.isEmpty()) EconomyText.error(sender, "보유 주식이 없습니다!")
                for (code in codes.sorted()) {
                    val holding = FileManager.stocks.holding(data, code)
                    val catalog = FileManager.getStockConfig()
                    val listed = code in StockCatalog.codes(catalog)
                    sender.sendMessage(Component.text("${catalog.getString("$code.name") ?: code} ($code)", NamedTextColor.AQUA)
                        .append(Component.text(" ${holding.count}", NamedTextColor.GREEN))
                        .append(Component.text("주, 총투자금액: ", NamedTextColor.WHITE))
                        .append(moneyDisplay(holding.costBasis.units))
                        .append(Component.text("원", NamedTextColor.WHITE))
                        .append(Component.text(if (listed) "" else " (상장 중지)", NamedTextColor.GRAY)))
                }
            }
            "add" -> {
                val name = args[2]
                val code = args[3]
                if (name.equals("all", true)) { EconomyText.error(sender, "주식 이름으로 'all'은 사용할 수 없습니다."); return }
                if (!code.matches(Regex("[0-9A-Z]{4}"))) {
                    EconomyText.error(sender, "코드는 대문자 알파벳과 숫자로만 이루어진 4글자로 입력해주세요."); return
                }
                val stocks = FileManager.getStockConfig()
                if (StockCatalog.allCodes(stocks).size >= 54) { EconomyText.error(sender, "주식은 최대 54개까지 생성 가능합니다."); return }
                if (stocks.isSet(code)) { EconomyText.error(sender, "이미 존재하는 코드입니다."); return }
                if (StockCatalog.allCodes(stocks).any { stocks.getString("$it.name") == name }) {
                    EconomyText.error(sender, "이미 존재하는 주식입니다."); return
                }
                val material = Functions.toMaterial(args[4])
                if (!material.isItem || material.isAir) { EconomyText.error(sender, "올바르지 않은 아이템입니다."); return }
                val units = args[5].toLongOrNull()?.takeIf { it > 0 }
                if (units == null) {
                    sender.sendMessage(EconomyText.invalidNumber(args[5], "은(는) 올바르지 않은 가격입니다! 양수의 정수로 입력해주세요."))
                    return
                }
                FileManager.replaceStock(StockCatalogEdit.add(stocks, name, code, material.name, Money(units), Functions.getDateTime()))
                sender.sendMessage(Component.text("주식 ", NamedTextColor.WHITE)
                    .append(Component.text("${name}(${code})", NamedTextColor.AQUA))
                    .append(Component.text("을(를) 추가했습니다.", NamedTextColor.WHITE)))
            }
            else -> {
                val action = args[1].lowercase(java.util.Locale.ROOT)
                val target = args[2]
                val stocks = FileManager.getStockConfig()
                val all = target.equals("all", true)
                val candidates = if (action == "restore") StockCatalog.allCodes(stocks) else StockCatalog.codes(stocks)
                if (!all && target !in candidates) { EconomyText.error(sender, "존재하지 않는 주식입니다."); return }
                // Removing only stops the listing; player holdings are kept for restore.
                FileManager.replaceStock(StockCatalogEdit.edit(stocks, action, target, Functions.getDateTime()))
                if (all) {
                    sender.sendMessage(Component.text(when (action) {
                        "remove" -> "모든 주식을 삭제했습니다."
                        "restore" -> "모든 주식을 복구했습니다."
                        else -> "모든 주식의 현재 가격을 초기 가격으로 설정했습니다."
                    }, NamedTextColor.WHITE))
                } else {
                    sender.sendMessage(Component.text("주식 ", NamedTextColor.WHITE)
                        .append(Component.text(target, NamedTextColor.AQUA))
                        .append(Component.text(when (action) {
                            "remove" -> "을(를) 삭제했습니다."
                            "restore" -> "을(를) 복구했습니다."
                            else -> "의 현재 가격을 초기 가격으로 설정했습니다."
                        }, NamedTextColor.WHITE)))
                }
            }
        }
    }
}
