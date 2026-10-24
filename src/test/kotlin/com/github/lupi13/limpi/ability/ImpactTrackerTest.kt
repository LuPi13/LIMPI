package com.github.lupi13.limpi.ability

import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ImpactTrackerTest {
    private val free: (Int, Int) -> Boolean = { _, _ -> false }
    private fun wall(axis: Int, direction: Int): (Int, Int) -> Boolean = { a, d -> a == axis && d == direction }

    @Test fun `first sample has no previous speed`() {
        val tracker = ImpactTracker()
        assertEquals(ImpactTracker.Sample(0.0, 0.0), tracker.sample(Vector(1.0, 0.0, 0.0), 1.0, wall(0, 1)))
    }

    @Test fun `hitting a wall reports the approach speed once`() {
        val tracker = ImpactTracker()
        tracker.sample(Vector(0.9, 0.0, 0.0), 1.0, free)
        assertEquals(0.9, tracker.sample(Vector(0.3, 0.0, 0.0), 1.0, wall(0, 1)).wallSpeed, 1e-12)
        assertEquals(0.0, tracker.sample(Vector(0.002, 0.0, 0.0), 1.0, wall(0, 1)).wallSpeed)
        assertEquals(0.0, tracker.sample(Vector(0.0, 0.0, 0.0), 1.0, wall(0, 1)).wallSpeed)
    }

    @Test fun `a doubled sample cannot exceed the web shooter top speed`() {
        val tracker = ImpactTracker()
        tracker.sample(Vector(2.0, 0.0, 0.0), 1.0, free)
        assertEquals(1.0, tracker.sample(Vector(0.0, 0.0, 0.0), 1.0, wall(0, 1)).wallSpeed, 1e-12)
    }

    @Test fun `ceiling counts but floor is left to fall damage`() {
        val up = ImpactTracker()
        up.sample(Vector(0.0, 0.8, 0.0), 1.0, free)
        assertEquals(0.8, up.sample(Vector(0.0, 0.1, 0.0), 1.0, wall(1, 1)).wallSpeed, 1e-12)
        val down = ImpactTracker()
        down.sample(Vector(0.0, -0.8, 0.0), 1.0, free)
        assertEquals(0.0, down.sample(Vector(0.0, -0.1, 0.0), 1.0) { _, _ -> true }.wallSpeed)
    }

    @Test fun `diagonal impact combines the blocked axes only`() {
        val tracker = ImpactTracker()
        tracker.sample(Vector(0.6, 0.0, 0.8), 2.0, free)
        assertEquals(1.0, tracker.sample(Vector(0.0, 0.0, 0.0), 2.0) { _, _ -> true }.wallSpeed, 1e-12)
        val sliding = ImpactTracker()
        sliding.sample(Vector(0.6, 0.0, 0.8), 2.0, free)
        assertEquals(0.6, sliding.sample(Vector(0.0, 0.0, 0.8), 2.0, wall(0, 1)).wallSpeed, 1e-12)
    }

    @Test fun `fall speed is one move behind so a short landing move keeps the impact speed`() {
        val tracker = ImpactTracker()
        assertEquals(0.0, tracker.sample(Vector(0.0, -0.5, 0.0), 1.0, free).fallSpeed)
        assertEquals(0.5, tracker.sample(Vector(0.0, -0.9, 0.0), 1.0, free).fallSpeed, 1e-12)
        assertEquals(0.9, tracker.sample(Vector(0.0, -0.05, 0.0), 1.0, free).fallSpeed, 1e-12)
    }

    @Test fun `rising resets fall speed and falling speed is capped at terminal velocity`() {
        val tracker = ImpactTracker()
        tracker.sample(Vector(0.0, -1.0, 0.0), 1.0, free)
        assertEquals(0.0, tracker.sample(Vector(0.0, 0.3, 0.0), 1.0, free).fallSpeed)
        assertEquals(0.2, tracker.sample(Vector(0.0, -0.2, 0.0), 1.0, free).fallSpeed, 1e-12)
        tracker.sample(Vector(0.0, -50.0, 0.0), 1.0, free)
        assertEquals(ImpactPolicy.terminalSpeed, tracker.sample(Vector(0.0, -1.0, 0.0), 1.0, free).fallSpeed, 1e-12)
    }

    @Test fun `reset forgets the previous move and the blocked state`() {
        val tracker = ImpactTracker()
        tracker.sample(Vector(1.0, 0.0, 0.0), 1.0, free)
        tracker.reset()
        assertEquals(0.0, tracker.sample(Vector(0.0, 0.0, 0.0), 1.0, wall(0, 1)).wallSpeed)
    }
}
