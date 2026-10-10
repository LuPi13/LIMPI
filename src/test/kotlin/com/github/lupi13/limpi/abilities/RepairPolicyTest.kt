package com.github.lupi13.limpi.abilities

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RepairPolicyTest {
    @Test fun `missing repair gets documented default while explicit zero remains disabled`() {
        assertEquals(50, RepairPolicy.amount(null)); assertEquals(0, RepairPolicy.amount(0)); assertEquals(100, RepairPolicy.amount(100L))
    }
    @Test fun `invalid fractional negative textual and oversized repair settings are rejected`() {
        for (value in listOf(-1, 1.5, "50", Int.MAX_VALUE.toLong() + 1)) assertThrows(IllegalArgumentException::class.java) { RepairPolicy.amount(value) }
    }
    @Test fun `repair stops at zero and integer boundaries never wrap`() {
        assertEquals(50, RepairPolicy.damageAfter(100, 50)); assertEquals(0, RepairPolicy.damageAfter(5, 50))
        assertEquals(0, RepairPolicy.damageAfter(0, Int.MAX_VALUE)); assertEquals(Int.MAX_VALUE, RepairPolicy.damageAfter(Int.MAX_VALUE, 0))
    }
}
