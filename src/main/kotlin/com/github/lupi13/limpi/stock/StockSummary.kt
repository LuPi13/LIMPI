package com.github.lupi13.limpi.stock

import java.math.BigInteger

/** [previous] is null before a listed stock has moved for the first time; it then counts as unchanged. */
data class StockMove(val name: String, val current: Long, val previous: Long?) {
    private val before get() = previous?.takeIf { it > 0 } ?: current
    val direction: Int get() = current.compareTo(before)
    val changeTenths: Long get() = StockSummary.changeTenths(BigInteger.valueOf(current) - BigInteger.valueOf(before), BigInteger.valueOf(before))
}

/** [best] only ever rose and [worst] only ever fell, so a market moving one way shows just one of them. */
data class MarketSummary(val rising: Int, val falling: Int, val best: StockMove?, val worst: StockMove?)

/** Quantities and prices of one held, still listed stock. */
data class StockPosition(val count: Long, val costBasis: Long, val price: Long)

data class PortfolioSummary(val value: BigInteger, val cost: BigInteger) {
    val profit: BigInteger get() = value - cost
    /** Null without a cost to compare with. */
    val profitTenths: Long? get() = if (cost.signum() == 0) null else StockSummary.changeTenths(profit, cost)
}

object StockSummary {
    /** Signed tenths of a percent, cut toward zero: a move of 42 on 1000 is 42. */
    fun changeTenths(change: BigInteger, base: BigInteger): Long {
        require(base.signum() > 0)
        return (change * BigInteger.valueOf(1000) / base).min(BigInteger.valueOf(Long.MAX_VALUE)).max(BigInteger.valueOf(Long.MIN_VALUE)).toLong()
    }

    /** Sign left out, for the ▲▼ in front: 42 and -42 are both "4.2". */
    fun percentText(tenths: Long): String {
        val size = BigInteger.valueOf(tenths).abs()
        val (whole, rest) = size.divideAndRemainder(BigInteger.TEN)
        return "$whole.$rest"
    }

    /** On a tie the stock listed first wins, so the same prices always show the same names. */
    fun market(moves: List<StockMove>): MarketSummary {
        val rising = moves.filter { it.direction > 0 }
        val falling = moves.filter { it.direction < 0 }
        return MarketSummary(rising.size, falling.size, rising.maxByOrNull { it.changeTenths }, falling.minByOrNull { it.changeTenths })
    }

    /** What a sale would still cost in fees is not taken off; the cost already holds the fees of buying. */
    fun portfolio(positions: List<StockPosition>): PortfolioSummary = positions.fold(PortfolioSummary(BigInteger.ZERO, BigInteger.ZERO)) { sum, position ->
        require(position.count >= 0 && position.costBasis >= 0 && position.price >= 0)
        PortfolioSummary(sum.value + BigInteger.valueOf(position.count) * BigInteger.valueOf(position.price), sum.cost + BigInteger.valueOf(position.costBasis))
    }
}
