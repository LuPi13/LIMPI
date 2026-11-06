package com.github.lupi13.limpi.stock

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class StockSummaryTest {
    @Test fun `counts rises and falls and names the extremes`() {
        val summary = StockSummary.market(listOf(StockMove("철", 969, 1000), StockMove("다이아", 1042, 1000), StockMove("금", 1010, 1000),
            StockMove("석탄", 500, 500), StockMove("구리", 990, 1000)))
        assertEquals(2, summary.rising); assertEquals(2, summary.falling)
        assertEquals("다이아", summary.best!!.name); assertEquals(42, summary.best!!.changeTenths)
        assertEquals("철", summary.worst!!.name); assertEquals(-31, summary.worst!!.changeTenths)
    }

    @Test fun `a market moving one way has only one extreme and a still market none`() {
        val up = StockSummary.market(listOf(StockMove("a", 110, 100), StockMove("b", 120, 100)))
        assertEquals("b", up.best!!.name); assertNull(up.worst)
        val still = StockSummary.market(listOf(StockMove("a", 100, 100)))
        assertEquals(0, still.rising); assertEquals(0, still.falling); assertNull(still.best); assertNull(still.worst)
        assertEquals(MarketSummary(0, 0, null, null), StockSummary.market(emptyList()))
    }

    @Test fun `a stock that has not moved yet counts as unchanged`() {
        for (previous in listOf(null, 0L, -5L)) {
            val move = StockMove("new", 100, previous)
            assertEquals(0, move.direction); assertEquals(0, move.changeTenths)
        }
    }

    @Test fun `a tie goes to the stock listed first`() {
        val summary = StockSummary.market(listOf(StockMove("first", 110, 100), StockMove("second", 220, 200)))
        assertEquals("first", summary.best!!.name)
    }

    @Test fun `percent text keeps one decimal without its sign`() {
        assertEquals(listOf("4.2", "4.2", "0.0", "0.5", "100.0"), listOf(42L, -42L, 0L, 5L, 1000L).map(StockSummary::percentText))
        assertEquals("922337203685477580.8", StockSummary.percentText(Long.MIN_VALUE))
    }

    @Test fun `the portfolio adds value and cost and reports the profit on the cost`() {
        val summary = StockSummary.portfolio(listOf(StockPosition(10, 10_300, 1100), StockPosition(5, 5_100, 900)))
        assertEquals(BigInteger.valueOf(15_500), summary.value); assertEquals(BigInteger.valueOf(15_400), summary.cost)
        assertEquals(BigInteger.valueOf(100), summary.profit); assertEquals(6, summary.profitTenths)
        val loss = StockSummary.portfolio(listOf(StockPosition(1, 1000, 900)))
        assertEquals(BigInteger.valueOf(-100), loss.profit); assertEquals(-100, loss.profitTenths)
    }

    @Test fun `no cost has no percent and huge holdings do not overflow`() {
        assertNull(StockSummary.portfolio(emptyList()).profitTenths)
        assertNull(StockSummary.portfolio(listOf(StockPosition(1, 0, 100))).profitTenths)
        val huge = StockSummary.portfolio(listOf(StockPosition(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE), StockPosition(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).pow(2) * BigInteger.TWO, huge.value)
        assertEquals(StockMove("max", Long.MAX_VALUE, 1).changeTenths, Long.MAX_VALUE)
    }
}
