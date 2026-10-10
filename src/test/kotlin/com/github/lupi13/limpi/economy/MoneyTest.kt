package com.github.lupi13.limpi.economy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MoneyTest {
    @Test fun `balances above Int range remain exact`() {
        assertEquals(3_000_000_125L, (Money(3_000_000_000L) + Money(125)).units)
        assertEquals(6_000_000_000L, Money(3_000_000_000L).times(2).units)
    }

    @Test fun `overflow cannot produce a negative or wrapped balance`() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) + Money(1) }
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE).times(2) }
    }

    @Test fun `negative amounts and overdrafts are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Money(-1) }
        assertThrows(IllegalArgumentException::class.java) { Money(10) - Money(11) }
        assertThrows(IllegalArgumentException::class.java) { Money(10).times(-1) }
    }

    @Test fun `integer fee rounds down and stays exact at Long maximum`() {
        assertEquals(0L, Money(19).fee(5).units)
        assertEquals(1L, Money(20).fee(5).units)
        assertEquals(5L, Money(101).fee(5).units)
        assertEquals(461_168_601_842_738_790L, Money(Long.MAX_VALUE).fee(5).units)
        assertEquals(Long.MAX_VALUE, Money(Long.MAX_VALUE).fee(100).units)
        assertThrows(IllegalArgumentException::class.java) { Money(10).fee(101) }
    }
}
