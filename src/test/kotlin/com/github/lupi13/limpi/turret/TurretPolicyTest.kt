package com.github.lupi13.limpi.turret

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class TurretPolicyTest {
    @Test fun `aim lands on targets within range at any height`() {
        for ((x, y) in listOf(1.0 to 0.0, 5.0 to 0.0, 10.0 to 0.0, 7.0 to 3.0, 8.0 to -4.0, 3.0 to 6.0, 9.5 to 2.0)) {
            val pitch = TurretAim.pitch(x, y, 1.5)
            assertEquals(y, TurretAim.heightAt(pitch, x, 1.5), 0.2, "target ($x, $y) pitch $pitch")
        }
    }

    @Test fun `aim takes the low arc and compensates for gravity`() {
        val flat = TurretAim.pitch(10.0, 0.0, 1.5)
        assertTrue(flat > 0.0 && flat < 20.0, "pitch $flat")
        assertEquals(0.0, TurretAim.pitch(10.0, 0.0, 1.5, gravity = 0.0, drag = 1.0), 0.5)
    }

    @Test fun `out of reach falls back to the highest reaching angle and straight up or down at zero distance`() {
        val far = TurretAim.pitch(200.0, 0.0, 0.5)
        assertTrue(far in 30.0..60.0, "pitch $far")
        assertEquals(90.0, TurretAim.pitch(0.0, 3.0, 1.5))
        assertEquals(-90.0, TurretAim.pitch(0.0, -3.0, 1.5))
        assertThrows(IllegalArgumentException::class.java) { TurretAim.pitch(5.0, 0.0, 0.0) }
    }

    @Test fun `a mob stays locked to its first turret until that turret is gone`() {
        val locks = TargetLocks()
        val mob = UUID.randomUUID(); val a = UUID.randomUUID(); val b = UUID.randomUUID(); val player = UUID.randomUUID()
        val alive = mutableSetOf(a, b)
        assertTrue(locks.allows(mob, player))
        assertTrue(locks.lock(mob, a, alive::contains))
        assertFalse(locks.lock(mob, b, alive::contains))
        assertEquals(a, locks.lockedTo(mob))
        assertTrue(locks.allows(mob, a)); assertFalse(locks.allows(mob, b)); assertFalse(locks.allows(mob, player)); assertFalse(locks.allows(mob, null))
        assertEquals(listOf(mob), locks.mobsOf(a))
        alive.remove(a)
        assertTrue(locks.lock(mob, b, alive::contains)) // 죽은 포탑의 고정은 새 포탑으로 바뀜
        locks.release(b)
        assertNull(locks.lockedTo(mob)); assertTrue(locks.allows(mob, player))
    }

    @Test fun `decay never goes below zero`() {
        assertEquals(29.0, TurretDecay.next(30.0, 1.0))
        assertEquals(0.0, TurretDecay.next(0.5, 1.0))
        assertEquals(30.0, TurretDecay.next(30.0, 0.0))
    }
}
