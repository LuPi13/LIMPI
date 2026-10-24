package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExecutionChanceTest {
    @Test fun `targets at or below the reference health keep the old missing-health curve`() {
        assertEquals(0.49, ExecutionChance.of(6.0, 20.0, 2.0, 40.0), 1e-9)
        assertEquals(0.25, ExecutionChance.of(20.0, 40.0, 2.0, 40.0), 1e-9)
        assertEquals(0.0, ExecutionChance.of(20.0, 20.0, 2.0, 40.0))
        assertEquals(1.0, ExecutionChance.of(0.0, 20.0, 2.0, 40.0))
    }

    @Test fun `bosses are scaled down by reference over max health`() {
        // 위더 300: 절반 체력에서 0.25 × 40/300
        assertEquals(0.25 * 40 / 300, ExecutionChance.of(150.0, 300.0, 2.0, 40.0), 1e-12)
        // 워든 500
        assertEquals(0.25 * 0.08, ExecutionChance.of(250.0, 500.0, 2.0, 40.0), 1e-12)
    }

    @Test fun `odd health values never exceed the probability range and bad settings fail`() {
        assertEquals(0.0, ExecutionChance.of(30.0, 20.0, 2.0, 40.0))
        assertEquals(1.0, ExecutionChance.of(-5.0, 20.0, 2.0, 40.0))
        assertEquals(0.0, ExecutionChance.of(1.0, 0.0, 2.0, 40.0))
        assertEquals(0.0, ExecutionChance.of(Double.NaN, 20.0, 2.0, 40.0))
        assertThrows(IllegalArgumentException::class.java) { ExecutionChance.of(1.0, 20.0, 2.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { ExecutionChance.of(1.0, 20.0, 0.0, 40.0) }
    }
}
