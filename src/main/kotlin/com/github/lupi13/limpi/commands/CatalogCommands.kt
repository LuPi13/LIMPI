package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
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
                require(sender is Player && sender.hasPermission("limpi.financial.$kind")) { "권한이 부족합니다!" }
                if (!FileManager.delivery.claim(sender)) return true
                if (kind == "sell") SellEvents.openSellGUI(sender) else ShopEvents.openShopGUI(sender)
                return true
            }
            require(sender.hasPermission("limpi.financial.admin.catalog")) { "권한이 부족합니다!" }
            val action = args[1].lowercase(Locale.ROOT)
            require((action == "set" && args.size == 4) || (action == "remove" && args.size == 3)) {
                "잘못된 형식입니다. /financial $kind [set/remove]"
            }
            val material = Functions.toMaterial(args[2])
            if (!material.isItem || material.isAir) {
                sender.sendMessage(EconomyText.invalidNumber(args[2], "은(는) 올바르지 않은 아이템입니다!")); return true
            }
            val name = material.name.lowercase(Locale.ROOT)
            val config = if (kind == "sell") FileManager.getSellPriceConfig() else FileManager.getShopRatioConfig()
            val value: Any? = if (action == "remove") null else if (kind == "sell") {
                val units = args[3].toLongOrNull()?.takeIf { it >= 0 }
                if (units == null) { sender.sendMessage(EconomyText.invalidNumber(args[3])); return true }
                CatalogPrices.sell(units).units
            } else {
                val ratio = args[3].toDoubleOrNull()?.let { runCatching { CatalogPrices.ratio(it) }.getOrNull() }
                if (ratio == null) { sender.sendMessage(EconomyText.invalidNumber(args[3])); return true }
                CatalogPrices.shop(CatalogPrices.sell(FileManager.getSellPriceConfig().get(name)), ratio)
                ratio.toDouble()
            }
            config.set(name, value)
            if (kind == "sell") FileManager.saveSellPrice() else FileManager.saveShopRatio()
            val label = if (kind == "sell") "판매가격" else "구매가격비율"
            sender.sendMessage(when {
                value == null -> Component.text(name, NamedTextColor.YELLOW)
                    .append(Component.text("의 ${label}을 삭제했습니다.", NamedTextColor.WHITE))
                kind == "sell" -> Component.text(name, NamedTextColor.YELLOW)
                    .append(Component.text("의 판매가격을 ", NamedTextColor.WHITE))
                    .append(moneyDisplay(value as Long))
                    .append(Component.text("원으로 설정했습니다.", NamedTextColor.WHITE))
                else -> Component.text(name, NamedTextColor.YELLOW)
                    .append(Component.text("의 구매가격비율을 ", NamedTextColor.WHITE))
                    .append(moneyDisplay(value as Double))
                    .append(Component.text("배로 설정했습니다.", NamedTextColor.WHITE))
            })
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "잘못된 형식입니다.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Catalog $kind command failed", failure)
            sender.sendMessage(Component.text("처리 결과를 확인하지 못했습니다. 관리자에게 문의하세요.", NamedTextColor.RED))
        }
        return true
    }
}
