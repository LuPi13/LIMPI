package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class StockPricePolicy(
    val minimumRatio: Double,
    val maximumRatio: Double,
    val standardDeviation: Double,
    val meanReversion: Double,
) {
    init {
        require(minimumRatio.isFinite() && minimumRatio > 0)
        require(maximumRatio.isFinite() && maximumRatio >= minimumRatio)
        require(standardDeviation.isFinite() && standardDeviation >= 0)
        require(meanReversion.isFinite() && meanReversion in 0.0..1.0)
    }

    fun next(initial: Long, current: Long, gaussian: Double): Long {
        require(initial > 0 && current > 0 && gaussian.isFinite())
        val bias = 1.0 - current.toDouble() / initial.toDouble()
        val multiplier = (gaussian * (1 + bias * meanReversion)).coerceIn(minimumRatio, maximumRatio)
        require(multiplier.isFinite())
        return BigDecimal.valueOf(current).multiply(BigDecimal.valueOf(multiplier))
            .setScale(0, RoundingMode.DOWN).max(BigDecimal.ONE)
            .min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact()
    }
}

data class StockHolding(val count: Long, val costBasis: Money) {
    init { require(count >= 0 && (count != 0L || costBasis.units == 0L)) }
    val averagePrice: Long get() = if (count == 0L) 0 else costBasis.units / count

    fun buy(price: Money, quantity: Long): StockHolding {
        require(price.units > 0 && quantity > 0)
        return StockHolding(Math.addExact(count, quantity), costBasis + price.times(quantity))
    }

    fun sell(quantity: Long): StockHolding {
        require(quantity > 0 && quantity <= count)
        if (quantity == count) return StockHolding(0, Money(0))
        val released = BigInteger.valueOf(costBasis.units).multiply(BigInteger.valueOf(quantity))
            .divide(BigInteger.valueOf(count)).longValueExact()
        return StockHolding(count - quantity, costBasis - Money(released))
    }
}

/** A persisted boundary replaces per-tick Calendar flags and survives missed warning ticks. */
class StockSchedule(val periodMillis: Long) {
    init { require(periodMillis > 0) }
    fun boundary(nowMillis: Long): Long = Math.multiplyExact(Math.floorDiv(nowMillis, periodMillis), periodMillis)
    fun due(nowMillis: Long, lastUpdateMillis: Long): Boolean = boundary(nowMillis) > lastUpdateMillis
    fun nextBoundary(nowMillis: Long): Long = Math.addExact(boundary(nowMillis), periodMillis)
}
