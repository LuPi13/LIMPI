package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Mean reversion on the log price (Ornstein-Uhlenbeck, 2.6.0):
 * `ln next = ln current + meanReversion × (ln initial − ln current) + standardDeviation × z`, z ~ N(0, 1).
 *
 * Moves are multiplicative and symmetric in log space, so the price never drifts to 0 or infinity and needs no
 * per-step bounds. The long-run log price is normal around the initial price with standard deviation
 * `standardDeviation / sqrt(meanReversion × (2 − meanReversion))`.
 */
data class StockPricePolicy(
    val standardDeviation: Double,
    val meanReversion: Double,
) {
    init {
        require(standardDeviation.isFinite() && standardDeviation >= 0)
        require(meanReversion.isFinite() && meanReversion in 0.0..1.0)
    }

    /** Long-run standard deviation of the log price, or infinity without mean reversion. */
    val stationaryDeviation: Double get() =
        if (meanReversion == 0.0) Double.POSITIVE_INFINITY else standardDeviation / Math.sqrt(meanReversion * (2 - meanReversion))

    fun next(initial: Long, current: Long, gaussian: Double): Long {
        require(initial > 0 && current > 0 && gaussian.isFinite())
        val logCurrent = Math.log(current.toDouble())
        val log = logCurrent + meanReversion * (Math.log(initial.toDouble()) - logCurrent) + standardDeviation * gaussian
        // Rounded to the nearest unit; exp saturates to 0 or infinity for extreme samples, which the bounds absorb.
        val next = Math.exp(log)
        if (next.isNaN()) throw IllegalArgumentException("Invalid stock price step")
        if (next >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return BigDecimal.valueOf(next).setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE).longValueExact()
    }
}

data class StockHolding(val count: Long, val costBasis: Money) {
    init { require(count >= 0 && (count != 0L || costBasis.units == 0L)) }
    val averagePrice: Long get() = if (count == 0L) 0 else costBasis.units / count

    /** A purchase's [fee] is part of the acquisition cost, so realized profit is net of both fees. */
    fun buy(price: Money, quantity: Long, fee: Money = Money(0)): StockHolding {
        require(price.units > 0 && quantity > 0)
        return StockHolding(Math.addExact(count, quantity), costBasis + price.times(quantity) + fee)
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
