package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PotionOwnershipTest {
    private val entity = UUID.randomUUID(); private val scope = UUID.randomUUID()
    private val normal = PotionSignature(0, false, true, true)
    @Test fun `absent effect can be acquired refreshed and released once`() {
        val state = PotionOwnership()
        assertTrue(state.acquire(entity, "speed", scope, normal, null))
        assertTrue(state.acquire(entity, "speed", scope, normal, normal))
        assertTrue(state.release(entity, "speed", scope, normal))
        assertFalse(state.release(entity, "speed", scope, normal))
    }
    @Test fun `existing same type external effect is never overwritten even when weaker`() {
        val state = PotionOwnership()
        assertFalse(state.acquire(entity, "speed", scope, normal.copy(amplifier = 4), normal))
        assertFalse(state.release(entity, "speed", scope, normal))
    }
    @Test fun `external identical replacement invalidates ownership before cleanup`() {
        val state = PotionOwnership(); state.acquire(entity, "speed", scope, normal, null)
        state.external(entity, "speed")
        assertFalse(state.release(entity, "speed", scope, normal))
        assertFalse(state.acquire(entity, "speed", scope, normal, normal))
    }
    @Test fun `changed amplifier flags or missing effect never authorizes removal`() {
        for (current in listOf(normal.copy(amplifier = 2), normal.copy(ambient = true), normal.copy(particles = false), normal.copy(icon = false), null)) {
            val state = PotionOwnership(); state.acquire(entity, "speed", scope, normal, null)
            assertFalse(state.release(entity, "speed", scope, current))
            assertFalse(state.owns(entity, "speed", scope))
        }
    }
    @Test fun `one scope cannot replace or release another scopes potion`() {
        val state = PotionOwnership(); val second = UUID.randomUUID()
        state.acquire(entity, "speed", scope, normal, null)
        assertFalse(state.acquire(entity, "speed", second, normal, normal))
        assertFalse(state.release(entity, "speed", second, normal))
        assertTrue(state.release(entity, "speed", scope, normal))
    }
    @Test fun `same scope may change owned amplifier without keeping the old cleanup authority`() {
        val state = PotionOwnership(); state.acquire(entity, "jump", scope, normal, null)
        val stronger = normal.copy(amplifier = 3)
        assertTrue(state.acquire(entity, "jump", scope, stronger, normal))
        assertTrue(state.release(entity, "jump", scope, stronger))
    }
    @Test fun `entity and potion type are independent and shutdown clears all claims`() {
        val state = PotionOwnership(); val other = UUID.randomUUID()
        state.acquire(entity, "speed", scope, normal, null); state.acquire(entity, "resistance", scope, normal, null)
        state.acquire(other, "speed", scope, normal, null); state.external(entity, "speed")
        assertTrue(state.release(entity, "resistance", scope, normal)); assertTrue(state.owns(other, "speed", scope))
        state.clear(); assertFalse(state.release(other, "speed", scope, normal))
    }
}
