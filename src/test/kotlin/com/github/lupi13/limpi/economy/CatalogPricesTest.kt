package com.github.lupi13.limpi.economy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CatalogPricesTest {
    @Test fun `defaults integral prices and decimal floor retain catalog semantics without Int clipping`() {
        assertEquals(1, CatalogPrices.sell(null).units)
        assertEquals(3_000_000_000, CatalogPrices.sell(3_000_000_000L).units)
        assertEquals(151, CatalogPrices.shop(Money(101), CatalogPrices.ratio(1.5)).units)
        assertEquals(4_500_000_000, CatalogPrices.shop(Money(3_000_000_000), CatalogPrices.ratio(1.5)).units)
        assertEquals(0, CatalogPrices.shop(Money(Long.MAX_VALUE), BigDecimal.ZERO).units)
        assertEquals(Long.MAX_VALUE, CatalogPrices.shop(Money(1), CatalogPrices.ratio(Long.MAX_VALUE)).units)
    }
    @Test fun `fractional negative textual and nonfinite settings fail instead of silently saturating`() {
        for (raw in listOf(-1, 1.5, "100")) assertThrows(IllegalArgumentException::class.java) { CatalogPrices.sell(raw) }
        for (raw in listOf(-1, Double.NaN, Double.POSITIVE_INFINITY, "1.5", null)) assertThrows(IllegalArgumentException::class.java) { CatalogPrices.ratio(raw) }
        assertThrows(ArithmeticException::class.java) { CatalogPrices.shop(Money(Long.MAX_VALUE), BigDecimal.TWO) }
    }
}
