package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StockPolicyTest {
    /** A fixed base price with these settings was the 2.6.1 default; servers upgraded since keep them in their config.yml. */
    private val policy = StockPricePolicy(0.025, 0.005)

    /** config.yml defaults. */
    private val noise = StockPricePolicy(0.015, 0.02)
    private val base = StockBasePolicy(0.02, 0.0005, 5.0)

    @Test fun `a move of the base carries the price at once and is not pulled back`() {
        // The base doubled: the price doubles with it, and no gap to the base is left to close.
        assertEquals(2_000L, noise.next(1_000, 1_000, 0.0, Math.log(2.0), 0.0))
        // Half the base and half the price, the same gap as before.
        assertEquals(500L, noise.next(1_000, 500, 0.0, Math.log(0.5)))
        // A price under the moved base closes 2% of that gap (log), toward the moved base and not the initial price:
        // 1800 × (2000 / 1800)^0.02 = 1803.8
        assertEquals(1_804L, noise.next(1_000, 1_800, 0.0, Math.log(2.0)))
    }

    @Test fun `without a shift the price moves exactly as before`() {
        val random = java.util.Random(11)
        repeat(1_000) {
            val price = 1 + random.nextInt(100_000).toLong()
            val gaussian = random.nextGaussian()
            val log = Math.log(price.toDouble())
            val expected = Math.round(Math.exp(log + 0.005 * (Math.log(10_000.0) - log) + 0.025 * gaussian)).coerceAtLeast(1)
            assertEquals(expected, policy.next(10_000, price, gaussian))
        }
    }

    @Test fun `the base walks, drifts home slowly and never leaves its range`() {
        assertEquals(0.02, base.next(0.0, 1.0), 1e-12)
        assertEquals(-0.02, base.next(0.0, -1.0), 1e-12)
        assertEquals(1.0 * (1 - 0.0005), base.next(1.0, 0.0), 1e-12)
        assertEquals(Math.log(5.0), base.next(Math.log(5.0), 100.0), 1e-12)
        assertEquals(-Math.log(5.0), base.next(0.0, 0.0, -100.0), 1e-12)
        assertEquals(0.0, StockBasePolicy(0.02, 0.0005, 1.0).next(0.0, 3.0, 1.0), "a range of one fixes the base")
        assertThrows(IllegalArgumentException::class.java) { StockBasePolicy(-0.1, 0.0, 5.0) }
        assertThrows(IllegalArgumentException::class.java) { StockBasePolicy(0.1, 1.5, 5.0) }
        assertThrows(IllegalArgumentException::class.java) { StockBasePolicy(0.1, 0.0, 0.5) }
        assertThrows(IllegalArgumentException::class.java) { base.next(Double.NaN, 0.0) }
    }

    @Test fun `by default one move still has the size it had`() {
        assertEquals(0.025, Math.hypot(noise.standardDeviation, base.volatility), 1e-12)
    }

    @Test fun `by default a stock keeps a price and a cheap one is not sure to come back`() {
        val random = java.util.Random(19)
        var price = 10_000L; var shift = 0.0
        val prices = ArrayList<Long>()
        repeat(400_000) {
            val moved = base.next(shift, random.nextGaussian())
            price = noise.next(10_000, price, random.nextGaussian(), moved, shift)
            shift = moved
            prices += price
        }
        assertTrue(prices.min() in 1_000L..3_000L, "${prices.min()}"); assertTrue(prices.max() in 30_000L..100_000L, "${prices.max()}")
        // The noise alone stays small: its long-run deviation is about 7.5%, near the 6.2% a round trip costs in fees.
        assertEquals(0.0752, noise.stationaryDeviation, 1e-3)
    }

    @Test fun `mean reversion pulls the log price toward the initial price`() {
        // ln 5000 + 0.005 × ln 2 → 5000 × 2^0.005 = 5017.36
        assertEquals(5017L, policy.next(10_000, 5_000, 0.0))
        // 20000 × 0.5^0.005 = 19930.87
        assertEquals(19931L, policy.next(10_000, 20_000, 0.0))
        assertEquals(100L, policy.next(100, 100, 0.0))
        // A one-sigma step is the same ratio up or down: ×e^0.025 or ÷e^0.025.
        assertEquals(1025L, policy.next(1000, 1000, 1.0))
        assertEquals(975L, policy.next(1000, 1000, -1.0))
    }

    @Test fun `prices stay positive and never overflow Long even for extreme samples`() {
        assertEquals(1L, policy.next(100, 100, -1_000_000.0))
        assertEquals(Long.MAX_VALUE, policy.next(100, 100, 1_000_000.0))
        assertEquals(1L, policy.next(1, 1, -100.0))
        assertEquals(Long.MAX_VALUE, policy.next(Long.MAX_VALUE, Long.MAX_VALUE, 100.0))
        assertThrows(IllegalArgumentException::class.java) { policy.next(0, 100, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { policy.next(100, -1, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { policy.next(100, 100, Double.NaN) }
    }

    @Test fun `default settings keep the price mostly within half and one and a half times the initial price`() {
        assertEquals(0.2503, policy.stationaryDeviation, 1e-4)
        val random = java.util.Random(7)
        var price = 10_000L
        var inside = 0; var total = 0
        repeat(400_000) { step ->
            price = policy.next(10_000, price, random.nextGaussian())
            if (step >= 2_000) { total++; if (price in 5_000..15_000) inside++ }
        }
        // About 94% of the time (log-normal, deviation 0.25); never collapses to 1 or explodes.
        assertTrue(inside.toDouble() / total in 0.90..0.97, "${inside.toDouble() / total}")
    }

    @Test fun `invalid settings fail before starting the scheduler`() {
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(-0.1, 0.02) }
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(0.05, 1.1) }
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(0.05, -0.1) }
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(Double.NaN, 0.02) }
        assertEquals(Double.POSITIVE_INFINITY, StockPricePolicy(0.05, 0.0).stationaryDeviation)
    }

    @Test fun `sale releases acquisition cost independently of sale price`() {
        val holding = StockHolding(0, Money(0)).buy(Money(100), 2)
        val remaining = holding.sell(1)
        assertEquals(1L, remaining.count)
        assertEquals(100L, remaining.costBasis.units)
        assertEquals(100L, remaining.averagePrice)
    }

    @Test fun `fractional average remainders are retained rather than discarded`() {
        val holding = StockHolding(0, Money(0)).buy(Money(100), 1).buy(Money(101), 1)
        assertEquals(201L, holding.costBasis.units)
        assertEquals(100L, holding.averagePrice)
        assertEquals(101L, holding.sell(1).costBasis.units)
        assertEquals(0L, holding.sell(2).costBasis.units)
    }

    @Test fun `large holding proportions do not overflow the intermediate product`() {
        val holding = StockHolding(3, Money(Long.MAX_VALUE))
        assertEquals(3_074_457_345_618_258_603L, holding.sell(2).costBasis.units)
    }

    @Test fun `holding quantity and acquisition overflow fail without state changes`() {
        val holding = StockHolding(2, Money(200))
        assertThrows(IllegalArgumentException::class.java) { holding.sell(3) }
        assertThrows(IllegalArgumentException::class.java) { holding.buy(Money(100), 0) }
        assertThrows(ArithmeticException::class.java) { holding.buy(Money(Long.MAX_VALUE), 2) }
        assertEquals(2L, holding.count)
        assertEquals(200L, holding.costBasis.units)
    }

    @Test fun `a boundary update is due even without an earlier warning tick`() {
        val schedule = StockSchedule(600_000)
        assertFalse(schedule.due(599_999, 0))
        assertTrue(schedule.due(600_000, 0))
        assertFalse(schedule.due(601_000, 600_000))
        assertEquals(1_200_000L, schedule.nextBoundary(601_000))
    }

    @Test fun `restart or missed intervals cause one update for the current boundary`() {
        val schedule = StockSchedule(600_000)
        assertTrue(schedule.due(1_800_123, 600_000))
        val completed = schedule.boundary(1_800_123)
        assertEquals(1_800_000L, completed)
        assertFalse(schedule.due(1_800_124, completed))
        assertFalse(schedule.due(1_700_000, completed))
        assertThrows(IllegalArgumentException::class.java) { StockSchedule(0) }
    }
}
