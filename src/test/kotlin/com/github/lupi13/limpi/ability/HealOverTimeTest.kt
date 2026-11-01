package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HealOverTimeTest {
    @Test fun `four health over four seconds in half second steps`() {
        val steps = HealOverTime.steps(4.0, 4.0)
        assertEquals(8, steps.size)
        assertEquals(4.0, steps.sum(), 1e-12)
        assertTrue(steps.all { kotlin.math.abs(it - 0.5) < 1e-12 })
    }

    @Test fun `uneven splits still add up exactly and zero seconds heals at once`() {
        val steps = HealOverTime.steps(5.0, 1.3)
        assertEquals(3, steps.size)
        assertEquals(5.0, steps.sum(), 1e-12)
        assertEquals(listOf(4.0), HealOverTime.steps(4.0, 0.0))
        assertEquals(emptyList<Double>(), HealOverTime.steps(0.0, 4.0))
        assertThrows(IllegalArgumentException::class.java) { HealOverTime.steps(-1.0, 4.0) }
    }
}
