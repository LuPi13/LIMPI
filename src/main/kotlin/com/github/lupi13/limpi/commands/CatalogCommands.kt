package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.economy.CatalogPrices
import com.github.lupi13.limpi.events.SellEvents
import com.github.lupi13.limpi.events.ShopEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.logging.Level

class CatalogCommands(private val plugin: Plugin) {
    fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val kind = args[0].lowercase(Locale.ROOT)
        try {
            if (args.size == 1) {
                require(sender is Player && sender.hasPermission("limpi.financial.$kind")) { "플레이어 권한이 필요합니다." }
                if (!FileManager.delivery.claim(sender)) return true
                if (kind == "sell") SellEvents.openSellGUI(sender) else ShopEvents.openShopGUI(sender)
                return true
            }
            require(sender.hasPermission("limpi.financial.admin.catalog")) { "권한이 부족합니다." }
            val action = args[1].lowercase(Locale.ROOT)
            require((action == "set" && args.size == 4) || (action == "remove" && args.size == 3)) {
                "/financial $kind set <아이템> <${if (kind == "sell") "비음수 Long 금액" else "비음수 유한 비율"}> 또는 remove <아이템>"
            }
            val material = Functions.toMaterial(args[2])
            require(material.isItem && !material.isAir) { "올바른 아이템 material을 지정하세요." }
            val name = material.name.lowercase(Locale.ROOT)
            val config = if (kind == "sell") FileManager.getSellPriceConfig() else FileManager.getShopRatioConfig()
            val value: Any? = if (action == "remove") null else if (kind == "sell") {
                CatalogPrices.sell(requireNotNull(args[3].toLongOrNull()) { "Long 범위 정수로 입력하세요." }).units
            } else {
                val ratio = CatalogPrices.ratio(requireNotNull(args[3].toDoubleOrNull()) { "비율은 숫자로 입력하세요." })
                CatalogPrices.shop(CatalogPrices.sell(FileManager.getSellPriceConfig().get(name)), ratio)
                ratio.toDouble()
            }
            config.set(name, value)
            if (kind == "sell") FileManager.saveSellPrice() else FileManager.saveShopRatio()
            sender.sendMessage(Component.text("$kind $name 설정을 저장했습니다: ${value ?: "삭제"}", NamedTextColor.GREEN))
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "설정 조건을 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Catalog $kind command failed", failure)
            sender.sendMessage(Component.text("저장/화면을 처리하지 못했습니다. 관리자 로그를 확인하세요.", NamedTextColor.RED))
        }
        return true
    }
}
