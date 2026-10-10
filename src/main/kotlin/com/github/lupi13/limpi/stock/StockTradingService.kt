package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class StockTrade { BUY, SELL }
data class StockTradeResult(val balance: Money, val holding: StockHolding, val total: Money,
    val transaction: UUID, val replayed: Boolean)

class StockTradingService(private val store: StateStore, private val players: PlayerRepository,
                          private val quote: (String) -> Money?) {
    fun holding(data: FileConfiguration, code: String): StockHolding {
        require(code.matches(Regex("[0-9A-Z]{4}")))
        require(data.get("stock") == null || data.get("stock") is ConfigurationSection) { "Invalid stock portfolio" }
        require(data.get("stock.$code") == null || data.get("stock.$code") is ConfigurationSection) { "Invalid holding $code" }
        fun integer(path: String): Long {
            val raw = data.get(path) ?: return 0
            require(raw is Int || raw is Long) { "Invalid integral $path" }
            return (raw as Number).toLong().also { require(it >= 0) { "Negative $path" } }
        }
        val count = integer("stock.$code.count")
        return StockHolding(count, Money(integer("stock.$code.costBasis")))
    }

    fun trade(request: UUID, owner: UUID, code: String, price: Money, quantity: Long, direction: StockTrade): StockTradeResult {
        require(code.matches(Regex("[0-9A-Z]{4}")) && price.units > 0 && quantity > 0)
        val committed = store.execute(request, BankService.fingerprint("STOCK_TRADE", owner.toString(), code,
            price.units.toString(), quantity.toString(), direction.name)) { tx ->
            require(quote(code) == price) { "가격이 변경되었거나 종목이 삭제되었습니다. 새 화면을 확인하세요." }
            val data = Accounts.read(tx, owner)
            val previous = holding(data, code)
            val balance = Accounts.balance(data)
            val total = price.times(quantity)
            val nextBalance = when (direction) {
                StockTrade.BUY -> balance - total
                StockTrade.SELL -> balance + total
            }
            val nextHolding = when (direction) {
                StockTrade.BUY -> previous.buy(price, quantity)
                StockTrade.SELL -> previous.sell(quantity)
            }
            val rawProfit = data.get("stock.$code.realizedProfit")
            require(rawProfit == null || rawProfit is Int || rawProfit is Long) { "Invalid integral realized profit" }
            val previousProfit = (rawProfit as? Number)?.toLong() ?: 0L
            val profit = if (direction == StockTrade.SELL) Math.subtractExact(total.units, (previous.costBasis - nextHolding.costBasis).units) else 0L
            val nextProfit = Math.addExact(previousProfit, profit)
            data.set("stock.$code.count", nextHolding.count)
            data.set("stock.$code.costBasis", nextHolding.costBasis.units)
            data.set("stock.$code.averagePrice", nextHolding.averagePrice)
            data.set("stock.$code.realizedProfit", nextProfit)
            Accounts.write(tx, owner, data, nextBalance)
            YamlConfiguration().apply {
                set("schemaVersion", 1); set("actor", owner.toString()); set("code", code); set("price", price.units)
                set("quantity", quantity); set("direction", direction.name); set("total", total.units)
                set("before.balance", balance.units); set("before.count", previous.count); set("before.costBasis", previous.costBasis.units)
                set("after.balance", nextBalance.units); set("after.count", nextHolding.count); set("after.costBasis", nextHolding.costBasis.units)
                set("before.realizedProfit", previousProfit); set("profit", profit); set("after.realizedProfit", nextProfit)
            }.saveToString()
        }
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        val receipt = YamlFiles.parse(committed.entry.receipt)
        return StockTradeResult(Money(receipt.getLong("after.balance")),
            StockHolding(receipt.getLong("after.count"), Money(receipt.getLong("after.costBasis"))),
            Money(receipt.getLong("total")), request, committed.replayed)
    }
}
