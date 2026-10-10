package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StockPolicyTest {
    private val policy = StockPricePolicy(0.7, 1.3, 0.1, 0.1)

    @Test fun `mean reversion uses real ratios rather than integer division`() {
        assertEquals(52L, policy.next(100, 50, 1.0))
        assertEquals(142L, policy.next(100, 150, 1.0))
    }

    @Test fun `the final multiplier is bounded even for extreme random samples`() {
        assertEquals(70L, policy.next(100, 100, -1_000_000.0))
        assertEquals(130L, policy.next(100, 100, 1_000_000.0))
    }

    @Test fun `prices stay positive and never overflow Long`() {
        assertEquals(1L, policy.next(1, 1, -100.0))
        assertEquals(Long.MAX_VALUE, policy.next(Long.MAX_VALUE, Long.MAX_VALUE, 100.0))
        assertThrows(IllegalArgumentException::class.java) { policy.next(0, 100, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { policy.next(100, -1, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { policy.next(100, 100, Double.NaN) }
    }

    @Test fun `invalid settings fail before starting the scheduler`() {
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(0.0, 1.3, 0.1, 0.1) }
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(1.3, 0.7, 0.1, 0.1) }
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(0.7, 1.3, -0.1, 0.1) }
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(0.7, 1.3, 0.1, 1.1) }
        assertThrows(IllegalArgumentException::class.java) { StockPricePolicy(Double.NaN, 1.3, 0.1, 0.1) }
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
