package com.github.lupi13.limpi.combat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class DamagePoliciesTest {
    private val owner = UUID.randomUUID()
    @Test fun `nested generated hits restore parent context even when damage throws`() {
        val root = AbilityHit(owner, "chain_lightning", HitKind.CHAIN)
        val child = AbilityHit(owner, "other", HitKind.ABILITY)
        DamageContext.with(root) {
            assertSame(root, DamageContext.current)
            assertThrows(IllegalStateException::class.java) { DamageContext.with(child) { assertSame(child, DamageContext.current); error("damage failed") } }
            assertSame(root, DamageContext.current)
        }
        assertNull(DamageContext.current)
    }
    @Test fun `context does not leak into another thread or a later vanilla hit`() {
        DamageContext.with(AbilityHit(owner, "ability", HitKind.ABILITY)) {
            val leaked = java.util.concurrent.atomic.AtomicReference<AbilityHit>()
            val thread = Thread { leaked.set(DamageContext.current) }; thread.start(); thread.join()
            assertNull(leaked.get())
        }
        assertNull(DamageContext.current)
    }
    @Test fun `chain does not revisit its origin or an already struck entity`() {
        val budget = ChainBudget(owner); val next = UUID.randomUUID()
        assertFalse(budget.reserve(owner, 10.0)); assertTrue(budget.reserve(next, 10.0)); assertFalse(budget.reserve(next, 10.0))
        assertEquals(1, budget.hops)
    }
    @Test fun `probability one and nondecaying damage still have finite chain work`() {
        val budget = ChainBudget(owner, 3)
        repeat(3) { assertTrue(budget.reserve(UUID.randomUUID(), 10.0)) }
        assertFalse(budget.reserve(UUID.randomUUID(), 10.0)); assertEquals(3, budget.hops)
    }
    @Test fun `invalid or zero damage cannot consume chain budget`() {
        val budget = ChainBudget(owner)
        for (damage in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertFalse(budget.reserve(UUID.randomUUID(), damage))
        assertEquals(0, budget.hops)
        assertThrows(IllegalArgumentException::class.java) { ChainBudget(owner, 0) }
        assertThrows(IllegalArgumentException::class.java) { ChainBudget(owner, 257) }
    }
    @Test fun `large damage remains finite through vanilla float conversion`() {
        val result = DamageNumbers.bounded(Double.MAX_VALUE)
        assertTrue(result.toFloat().isFinite()); assertEquals(DamageNumbers.MAXIMUM, result)
        assertTrue(DamageNumbers.execution(20.0, 4.0) > 24.0)
    }
    @Test fun `invalid damage is rejected instead of changing health`() {
        for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertThrows(IllegalArgumentException::class.java) { DamageNumbers.bounded(value) }
        assertThrows(IllegalArgumentException::class.java) { DamageNumbers.execution(20.0, -1.0) }
    }
    @Test fun `fixed bonus preserves normal armor reductions and absorbs the combined damage`() {
        val (base, absorption) = DamageNumbers.fixedAddition(10.0, 3.0, -6.0, 5.0)
        assertEquals(13.0, base); assertEquals(-5.0, absorption); assertEquals(2.0, base - 6 + absorption)
        assertEquals(7.0, DamageNumbers.fixedAddition(10.0, 3.0, -6.0, 0.0).let { it.first - 6 + it.second })
    }
    @Test fun `full absorption protects against a fixed bonus and all reductions can nullify damage`() {
        val (base, absorption) = DamageNumbers.fixedAddition(10.0, 3.0, -6.0, 100.0)
        assertEquals(0.0, base - 6 + absorption)
        assertEquals(0.0, DamageNumbers.fixedAddition(10.0, 0.0, -20.0, 5.0).second)
    }
    @Test fun `canceled attack keeps ready charge while confirmed attack consumes it`() {
        assertEquals(30.0, ChargeGauge.consume(30.0, false)); assertEquals(0.0, ChargeGauge.consume(30.0, true))
    }
    @Test fun `charge caps at its threshold and zero damage does not charge`() {
        assertEquals(30.0, ChargeGauge.gain(29.0, 10.0, 30.0)); assertEquals(29.0, ChargeGauge.gain(29.0, 0.0, 30.0))
        assertEquals(30.0, ChargeGauge.gain(30.0, Double.MAX_VALUE, 30.0))
    }
    @Test fun `invalid charge thresholds or hit values never corrupt the gauge`() {
        for (value in listOf(0.0, -1.0, Double.NaN)) assertThrows(IllegalArgumentException::class.java) { ChargeGauge.gain(0.0, 1.0, value) }
        assertThrows(IllegalArgumentException::class.java) { ChargeGauge.gain(0.0, Double.POSITIVE_INFINITY, 30.0) }
    }
}
