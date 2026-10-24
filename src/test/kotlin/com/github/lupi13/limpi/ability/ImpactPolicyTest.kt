package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ImpactPolicyTest {
    /** Speed and height of the same vanilla free fall, tick by tick. */
    private fun freeFall(ticks: Int): Pair<Double, Double> {
        var speed = 0.0; var height = 0.0
        repeat(ticks) { speed = (speed + 0.08) * 0.98; height += speed }
        return speed to height
    }

    @Test fun `free fall speed maps back to the height fallen`() {
        for (ticks in listOf(1, 5, 10, 20, 40, 100)) {
            val (speed, height) = freeFall(ticks)
            assertEquals(height, ImpactPolicy.fallHeight(speed), 1e-9)
        }
    }

    @Test fun `height grows with speed and stays finite`() {
        var previous = 0.0
        for (speed in listOf(0.1, 0.5, 1.0, 2.0, 3.9, 5.0, 100.0, Double.MAX_VALUE)) {
            val height = ImpactPolicy.fallHeight(speed)
            assertTrue(height.isFinite()); assertTrue(height > previous)
            previous = height
        }
        assertTrue(ImpactPolicy.fallHeight(Double.POSITIVE_INFINITY).isFinite())
    }

    @Test fun `no speed means no height`() {
        for (speed in listOf(0.0, -1.0, Double.NaN, Double.NEGATIVE_INFINITY)) assertEquals(0.0, ImpactPolicy.fallHeight(speed))
    }

    @Test fun `multiplier one keeps vanilla fall distance and scales only the part above safe distance`() {
        val (speed, height) = freeFall(30)
        assertEquals(height, ImpactPolicy.fallDistance(speed, 3.0, 1.0), 1e-9)
        assertEquals(3.0 + (height - 3.0) * 0.5, ImpactPolicy.fallDistance(speed, 3.0, 0.5), 1e-9)
        assertEquals(3.0, ImpactPolicy.fallDistance(speed, 3.0, 0.0), 1e-9)
        val (slow, low) = freeFall(5)
        assertEquals(low, ImpactPolicy.fallDistance(slow, 3.0, 0.0), 1e-9)
    }

    @Test fun `impact damage matches vanilla fall damage for the same speed`() {
        val (speed, height) = freeFall(30)
        assertEquals(kotlin.math.ceil(height - 3.0), ImpactPolicy.impactDamage(speed, 3.0, 1.0))
        assertEquals(0.0, ImpactPolicy.impactDamage(freeFall(5).first, 3.0, 1.0))
        assertEquals(0.0, ImpactPolicy.impactDamage(speed, 3.0, 0.0))
        assertEquals(0.0, ImpactPolicy.impactDamage(Double.NaN, 3.0, 1.0))
    }

    @Test fun `web shooter top speed hits about like an eight block fall`() {
        assertEquals(6.0, ImpactPolicy.impactDamage(1.0, 3.0, 1.0))
    }
}
