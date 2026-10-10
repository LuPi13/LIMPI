package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.platform.normalizeSafely
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PhysicsBoundsTest {
    @Test fun `zero and coincident vectors remain finite zero and preserve mutation contract`() {
        val vector = Vector()
        assertSame(vector, vector.normalizeSafely()); assertEquals(Vector(), vector)
    }
    @Test fun `non finite coordinates never reach entity movement as NaN`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(Vector(), Vector(value, 1.0, 2.0).normalizeSafely())
        }
    }
    @Test fun `very large and very small valid coordinates retain their direction`() {
        for (value in listOf(Double.MAX_VALUE, Double.MIN_VALUE, 1e-300)) {
            val vector = Vector(value, value, value).normalizeSafely()
            assertEquals(1.0, vector.length(), 1e-12); assertEquals(vector.x, vector.y); assertEquals(vector.y, vector.z)
        }
    }
    @Test fun `ordinary vector normalization preserves its expected direction`() {
        val vector = Vector(3, 0, 4).normalizeSafely()
        assertEquals(0.6, vector.x, 1e-12); assertEquals(0.8, vector.z, 1e-12)
    }
    @Test fun `ordinary shockwave preserves damage falloff and display radius`() {
        val policy = ShockwavePolicy(20.0, 2.0)
        assertEquals(40.0, policy.radius); assertEquals(10, policy.rings)
        assertEquals(20.0, policy.damage(0.0)); assertEquals(20.0 * kotlin.math.exp(-0.9), policy.damage(3.0), 1e-12)
    }
    @Test fun `huge fall damage has bounded spatial queries and particle rings`() {
        val policy = ShockwavePolicy(Double.MAX_VALUE, 2.0)
        assertEquals(128.0, policy.radius); assertEquals(16, policy.rings)
        assertTrue(policy.damage(4.0).isFinite()); assertEquals(0.0, policy.damage(Double.MAX_VALUE))
    }
    @Test fun `random repeat budget terminates even when every roll succeeds`() {
        val policy = ShockwavePolicy(20.0, 2.0); var calls = 0
        assertEquals(8, policy.repeats(1.0) { calls++; 0.0 }); assertEquals(8, calls)
        assertEquals(0, policy.repeats(0.0) { 0.0 })
        assertEquals(0, policy.repeats(0.5) { 0.9 })
    }
    @Test fun `zero damping and extremely low damage never create NaN`() {
        assertEquals(Double.MIN_VALUE, ShockwavePolicy(Double.MIN_VALUE, 0.0).damage(Double.MAX_VALUE))
        assertEquals(0.0, ShockwavePolicy(Double.MIN_VALUE, 2.0).damage(2.0))
    }
    @Test fun `invalid shockwave inputs are rejected before any effect work`() {
        for (damage in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { ShockwavePolicy(damage, 2.0) }
        }
        assertThrows(IllegalArgumentException::class.java) { ShockwavePolicy(2.0, -1.0) }
        assertThrows(IllegalArgumentException::class.java) { ShockwavePolicy(2.0, 1.0).damage(Double.NaN) }
    }
}
