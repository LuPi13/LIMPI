package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.economy.*
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
            val action = args[1].lowercase(Locale.ROOT)
            if (kind == "sell" && action == "price") { price(sender, args); return true }
            require(sender.hasPermission("limpi.financial.admin.catalog")) { "권한이 부족합니다!" }
            require((action == "set" && (args.size == 4 || (kind == "sell" && args.size == 6))) || (action == "remove" && args.size == 3)) {
                "잘못된 형식입니다. /financial $kind [set/remove]"
            }
            val material = Functions.toMaterial(args[2])
            if (!material.isItem || material.isAir) {
                sender.sendMessage(EconomyText.invalidNumber(args[2], "은(는) 올바르지 않은 아이템입니다!")); return true
            }
            val name = material.name.lowercase(Locale.ROOT)
            val config = if (kind == "sell") FileManager.getSellPriceConfig() else FileManager.getShopPriceConfig()
            val numbers = args.drop(3).map { it.toLongOrNull()?.takeIf { value -> value >= 0 } }
            numbers.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { sender.sendMessage(EconomyText.invalidNumber(args[3 + it])); return true }
            val value: Any? = when {
                action == "remove" -> null
                kind == "sell" && numbers.size == 3 -> {
                    val (start, half, floor) = numbers.map { it!! }
                    val rule = runCatching { SellRule.Decaying(start, half, floor) }.getOrNull()
                    if (rule == null) { sender.sendMessage(Component.text("시작 가격 ≥ 바닥 가격, 반감 개수 ≥ 1이어야 합니다.", NamedTextColor.RED)); return true }
                    mapOf("price" to start, "half" to half, "floor" to floor)
                }
                kind == "sell" -> numbers[0]!!
                else -> {
                    val sell = FileManager.getSellPriceConfig()
                    val highest = CatalogPrices.highestSell(SellCatalog.of(sell), name) { sell.get(it) }
                    if (numbers[0]!! < highest) {
                        sender.sendMessage(Component.text("구매 가격은 판매 가격(", NamedTextColor.RED).append(moneyDisplay(highest))
                            .append(Component.text("원)보다 낮을 수 없습니다.", NamedTextColor.RED))); return true
                    }
                    numbers[0]!!
                }
            }
            config.set(name, value)
            if (kind == "sell") FileManager.saveSellPrice() else FileManager.saveShopPrice()
            val label = if (kind == "sell") "판매가격" else "구매가격"
            sender.sendMessage(when {
                value == null -> Component.text(name, NamedTextColor.YELLOW)
                    .append(Component.text("의 ${label}을 삭제했습니다.", NamedTextColor.WHITE))
                value is Map<*, *> -> Component.text(name, NamedTextColor.YELLOW)
                    .append(Component.text("의 판매가격을 ", NamedTextColor.WHITE)).append(moneyDisplay(value["price"] as Long))
                    .append(Component.text("원에서 ", NamedTextColor.WHITE)).append(moneyDisplay(value["half"] as Long))
                    .append(Component.text("개마다 절반, 최저 ", NamedTextColor.WHITE)).append(moneyDisplay(value["floor"] as Long))
                    .append(Component.text("원으로 설정했습니다.", NamedTextColor.WHITE))
                else -> Component.text(name, NamedTextColor.YELLOW)
                    .append(Component.text("의 ${label}을 ", NamedTextColor.WHITE))
                    .append(moneyDisplay(value as Long))
                    .append(Component.text("원으로 설정했습니다.", NamedTextColor.WHITE))
            })
            if (kind == "sell") warnShop(sender, name)
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "잘못된 형식입니다.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Catalog $kind command failed", failure)
            throw failure // As before: Bukkit reports an internal command error to the sender.
        }
        return true
    }

    /** A sell price raised above the shop price takes that product out of the shop until an admin fixes it. */
    private fun warnShop(sender: CommandSender, name: String) {
        val shop = FileManager.getShopPriceConfig()
        if (!shop.contains(name)) return
        val sell = FileManager.getSellPriceConfig()
        val highest = runCatching { CatalogPrices.highestSell(SellCatalog.of(sell), name) { sell.get(it) } }.getOrNull() ?: return
        if (runCatching { CatalogPrices.shop(shop.get(name), highest) }.isSuccess) return
        sender.sendMessage(Component.text("구매 가격이 판매 가격보다 낮아 상점에서 빠집니다. /financial shop set으로 고쳐주세요.", NamedTextColor.RED))
    }

    /** /financial sell price [아이템]: what one more unit sells for now (the held item when omitted). */
    private fun price(sender: CommandSender, args: Array<out String>) {
        require(sender is Player && sender.hasPermission("limpi.financial.sell")) { "권한이 부족합니다!" }
        require(args.size in 2..3) { "잘못된 형식입니다. /financial sell price [아이템]" }
        val material = if (args.size == 3) Functions.toMaterial(args[2]) else sender.inventory.itemInMainHand.type
        if (!material.isItem || material.isAir) {
            sender.sendMessage(Component.text(if (args.size == 3) "${args[2]}은(는) 올바르지 않은 아이템입니다!" else "확인할 아이템을 손에 들거나 이름을 입력해주세요.", NamedTextColor.RED))
            return
        }
        val name = material.name.lowercase(Locale.ROOT)
        val catalog = SellEvents.catalog()
        val rule = catalog.rule(name)
        val now = System.currentTimeMillis()
        val one = catalog.quote(listOf(SaleLine(0, name, 1)), FileManager.sales.counters(sender.uniqueId), now, SellEvents.recoveryMillis()).total
        val message = Component.text(name, NamedTextColor.YELLOW).append(Component.text("의 현재 판매가격: ", NamedTextColor.WHITE))
            .append(moneyDisplay(one.units)).append(Component.text("원", NamedTextColor.WHITE))
        val base = if (rule is SellRule.Same) SellRule.parse(FileManager.getSellPriceConfig().get(rule.base)) else rule
        sender.sendMessage(if (base is SellRule.Decaying) message.append(Component.text(
            " (많이 팔수록 내려가며 ${plugin.config.get("SellRecoveryHours", 12)}시간마다 절반씩 회복됩니다)", NamedTextColor.GRAY)) else message)
    }
}
