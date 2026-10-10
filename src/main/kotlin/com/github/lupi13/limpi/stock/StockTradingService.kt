package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.player.PlayerRepository
import org.bukkit.configuration.file.FileConfiguration
import java.util.UUID

enum class StockTrade { BUY, SELL }
data class StockTradeResult(val balance: Money, val holding: StockHolding, val total: Money)

class StockTradingService(private val players: PlayerRepository) {
    fun holding(data: FileConfiguration, code: String): StockHolding {
        require(code.matches(Regex("[0-9A-Z]{4}")))
        val count = data.getLong("stock.$code.count")
        val basis = if (data.contains("stock.$code.costBasis")) Money(data.getLong("stock.$code.costBasis"))
            else Money(data.getLong("stock.$code.averagePrice")).times(count)
        return StockHolding(count, basis)
    }

    fun trade(id: UUID, code: String, price: Money, quantity: Long, direction: StockTrade): StockTradeResult {
        require(price.units > 0 && quantity > 0)
        val data = players.snapshot(id)
        val previous = holding(data, code)
        val balance = Money(data.getLong("money"))
        val total = price.times(quantity)
        val updated = when (direction) {
            StockTrade.BUY -> StockTradeResult(balance - total, previous.buy(price, quantity), total)
            StockTrade.SELL -> StockTradeResult(balance + total, previous.sell(quantity), total)
        }
        data.set("money", updated.balance.units)
        data.set("stock.$code.count", updated.holding.count)
        data.set("stock.$code.costBasis", updated.holding.costBasis.units)
        data.set("stock.$code.averagePrice", updated.holding.averagePrice)
        players.save(id, data)
        return updated
    }
}
