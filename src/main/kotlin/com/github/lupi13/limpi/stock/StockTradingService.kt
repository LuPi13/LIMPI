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
data class StockRepairResult(val holding: StockHolding, val transaction: UUID, val replayed: Boolean)
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
        val basis = if (data.isSet("stock.$code.costBasis")) Money(integer("stock.$code.costBasis"))
            else Money(integer("stock.$code.averagePrice")).times(count)
        return StockHolding(count, basis)
    }

    /**
     * Administrator repair for a preserved malformed legacy holding (for example a negative averagePrice).
     * The previous raw values stay in the ledger receipt; realizedProfit and unknown fields are not touched.
     */
    fun repair(request: UUID, actor: String, owner: UUID, code: String, count: Long, costBasis: Long): StockRepairResult {
        require(actor.isNotBlank() && code.matches(Regex("[0-9A-Z]{4}"))) { "종목 코드는 4자리 대문자/숫자입니다." }
        val holding = StockHolding(count, Money(costBasis))
        val committed = store.execute(request, BankService.fingerprint("STOCK_REPAIR", actor, owner.toString(), code,
            count.toString(), costBasis.toString())) { tx ->
            val data = Accounts.read(tx, owner)
            require(data.get("stock") == null || data.get("stock") is ConfigurationSection) { "Invalid stock portfolio" }
            require(data.get("stock.$code") == null || data.get("stock.$code") is ConfigurationSection) { "Invalid holding $code" }
            fun raw(field: String) = data.get("stock.$code.$field")?.toString()
            val before = listOf("count", "costBasis", "averagePrice").associateWith(::raw)
            data.set("stock.$code.count", holding.count)
            data.set("stock.$code.costBasis", holding.costBasis.units)
            data.set("stock.$code.averagePrice", holding.averagePrice)
            tx.put(SqliteProfilePersistence.key(owner), data.saveToString())
            YamlConfiguration().apply {
                set("schemaVersion", 1); set("operation", "STOCK_REPAIR"); set("actor", actor); set("owner", owner.toString()); set("code", code)
                before.forEach { (field, value) -> set("before.$field", value) }
                set("after.count", holding.count); set("after.costBasis", holding.costBasis.units)
            }.saveToString()
        }
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        val receipt = YamlFiles.parse(committed.entry.receipt)
        return StockRepairResult(StockHolding(receipt.getLong("after.count"), Money(receipt.getLong("after.costBasis"))), request, committed.replayed)
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
