package com.github.lupi13.limpi.ability

import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HomingPolicyTest {
    private fun deg(value: Double) = Math.toRadians(value)
    private val forward = Vector(0, 0, 1)

    @Test fun `turns at most the per-tick limit and keeps the speed`() {
        val next = HomingPolicy.steer(forward, Vector(1, 0, 0), forward, deg(4.0), deg(45.0), 1.5)
        assertEquals(1.5, next.length(), 1e-9)
        assertEquals(4.0, Math.toDegrees(HomingPolicy.angle(forward, next)), 1e-9)
        assertTrue(next.x > 0)
    }

    @Test fun `a target within the per-tick limit is faced exactly`() {
        val desired = Vector(Math.sin(deg(2.0)), 0.0, Math.cos(deg(2.0)))
        val next = HomingPolicy.steer(forward, desired, forward, deg(4.0), deg(45.0), 1.0)
        assertEquals(0.0, HomingPolicy.angle(desired, next), 1e-9)
    }

    @Test fun `never bends more than the total limit from the launch direction, so it cannot u-turn`() {
        var current = forward.clone()
        repeat(200) { current = HomingPolicy.steer(current, Vector(0, 0, -1).add(Vector(0.01, 0.0, 0.0)), forward, deg(4.0), deg(45.0), 1.5) }
        assertEquals(45.0, Math.toDegrees(HomingPolicy.angle(forward, current)), 1e-6)
        assertTrue(current.z > 0)
    }

    @Test fun `no target or an exactly opposite target keeps flying straight`() {
        assertEquals(0.0, HomingPolicy.angle(forward, HomingPolicy.steer(forward, null, forward, deg(4.0), deg(45.0), 1.0)), 1e-9)
        assertEquals(0.0, HomingPolicy.angle(forward, HomingPolicy.steer(forward, Vector(0, 0, -1), forward, deg(4.0), deg(45.0), 1.0)), 1e-9)
    }

    @Test fun `lead point follows the target velocity up to the lead limit`() {
        val point = HomingPolicy.leadPoint(Vector(0, 0, 0), Vector(0, 0, 15), Vector(0.2, 0.0, 0.0), 1.5, 20.0)
        assertEquals(Vector(2.0, 0.0, 15.0).distance(point), 0.0, 1e-9)
        val capped = HomingPolicy.leadPoint(Vector(0, 0, 0), Vector(0, 0, 150), Vector(0.2, 0.0, 0.0), 1.5, 20.0)
        assertEquals(4.0, capped.x, 1e-9)
    }
}
