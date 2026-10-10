package com.github.lupi13.limpi.gacha

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GachaPolicyTest {
    private val settings = GachaSettings(PityCurve(.005, .05, 81), PityCurve(.03, .3, 7), .01, .3, .5, .5, 1, 1)
    private val pools = DrawGrade.entries.associateWith { listOf("${it.name}_pickup", "${it.name}_other") }
    private val pickups = mapOf(DrawGrade.LEGENDARY to "LEGENDARY_pickup", DrawGrade.EPIC to "EPIC_pickup")
    private fun random(vararg values: Double): () -> Double { val queue = ArrayDeque(values.toList()); return { queue.removeFirst() } }

    @Test fun `one based pity boundary makes default legendary 100 and epic conditional 10`() {
        val l = settings.legendary; val e = settings.epic
        assertEquals(.005, l.probability(79), 1e-12); assertEquals(.055, l.probability(80), 1e-12)
        assertEquals(.955, l.probability(98), 1e-12); assertEquals(1.0, l.probability(99)); assertEquals(100L, l.guaranteeAttempt())
        assertEquals(.03, e.probability(5), 1e-12); assertEquals(.33, e.probability(6), 1e-12)
        assertEquals(.93, e.probability(8), 1e-12); assertEquals(1.0, e.probability(9)); assertEquals(10L, e.guaranteeAttempt())
        assertEquals(1.0, l.probability(Long.MAX_VALUE))
    }
    @Test fun `legendary takes precedence over guaranteed epic and conditional rates sum to one`() {
        val policy = GachaPolicy(settings); val state = GachaState(99, 9)
        assertEquals(DrawGrade.LEGENDARY, policy.grade(state, random(.999)))
        val rates = policy.rates(GachaState())
        assertEquals(.005, rates[DrawGrade.LEGENDARY]!!, 1e-12)
        assertEquals(.995 * .03, rates[DrawGrade.EPIC]!!, 1e-12)
        assertEquals(1.0, rates.values.sum(), 1e-12)
        assertEquals(0.0, policy.rates(state)[DrawGrade.EPIC])
    }
    @Test fun `zero probability is impossible even when sample is zero and equality falls through`() {
        val zero = settings.copy(legendary = PityCurve(0.0, 0.0, 1), epic = PityCurve(0.0, 0.0, 1), troll = 0.0, rare = 0.0)
        assertEquals(DrawGrade.COMMON, GachaPolicy(zero).grade(GachaState(), random(0.0, 0.0, 0.0, 0.0)))
        assertEquals(DrawGrade.EPIC, GachaPolicy(settings).grade(GachaState(), random(.005, 0.0)))
    }
    @Test fun `actual random selection of pickup resets misses just like forced pickup and other grade increments failures`() {
        val policy = GachaPolicy(settings)
        val ordinaryPickup = policy.draw(GachaState(4, 3), pools, pickups, random(0.0, .9, 0.0))
        assertTrue(ordinaryPickup.pickup); assertEquals("LEGENDARY_pickup", ordinaryPickup.ability)
        assertEquals(GachaState(0, 4, 0, 0), ordinaryPickup.after)
        val other = policy.draw(GachaState(4, 3), pools, pickups, random(0.0, .9, .9))
        assertFalse(other.pickup); assertEquals(1, other.after.legendaryMiss)
        val forced = policy.draw(other.after, pools, pickups, random(0.0))
        assertTrue(forced.pickup); assertEquals(0, forced.after.legendaryMiss)
    }
    @Test fun `epic and low grade counters preserve unrelated pickup misses`() {
        val policy = GachaPolicy(settings)
        val epic = policy.draw(GachaState(5, 5, 2, 1), pools, pickups, random(.9, 0.0))
        assertEquals(GachaState(6, 0, 2, 0), epic.after)
        val common = policy.draw(GachaState(5, 5, 2, 3), pools, pickups, random(.9, .9, .9, .9, .9))
        assertEquals(GachaState(6, 6, 2, 3), common.after)
    }
    @Test fun `empty duplicate and incompatible pickup candidates fail before sampling`() {
        val policy = GachaPolicy(settings)
        for (bad in listOf(pools + (DrawGrade.COMMON to emptyList()), pools + (DrawGrade.RARE to listOf("same", "same")))) {
            assertThrows(IllegalArgumentException::class.java) { policy.draw(GachaState(), bad, pickups) { error("must validate first") } }
        }
        assertThrows(IllegalArgumentException::class.java) { policy.draw(GachaState(), pools, pickups + (DrawGrade.EPIC to "wrong")) { error("must validate first") } }
    }
    @Test fun `candidate and pickup snapshots survive a source mutation during sampling`() {
        val mutablePool = mutableListOf("LEGENDARY_pickup", "LEGENDARY_other")
        val source = pools + (DrawGrade.LEGENDARY to mutablePool)
        val result = GachaPolicy(settings).draw(GachaState(), source, pickups) { mutablePool.clear(); 0.0 }
        assertEquals("LEGENDARY_pickup", result.ability)
    }
    @Test fun `disabled growth has no finite guarantee while certain base is first attempt`() {
        assertNull(PityCurve(.2, 0.0, 5).guaranteeAttempt()); assertEquals(1L, PityCurve(1.0, 0.0, 5).guaranteeAttempt())
        assertEquals(.2, PityCurve(.2, 0.0, 5).probability(Long.MAX_VALUE))
        assertNull(PityCurve(0.0, Double.MIN_VALUE, Long.MAX_VALUE).guaranteeAttempt())
    }
    @Test fun `invalid configuration states samples and counter overflow are rejected`() {
        for (value in listOf(-.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) assertThrows(IllegalArgumentException::class.java) { PityCurve(value, .1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PityCurve(.1, -.1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PityCurve(.1, .1, 0) }
        assertThrows(IllegalArgumentException::class.java) { GachaState(-1) }
        for (sample in listOf(-.1, 1.0, Double.NaN)) assertThrows(IllegalArgumentException::class.java) { GachaPolicy(settings).grade(GachaState()) { sample } }
        val onlyCommon = settings.copy(legendary = PityCurve(0.0, 0.0, 1), epic = PityCurve(0.0, 0.0, 1), troll = 0.0, rare = 0.0)
        assertThrows(ArithmeticException::class.java) { GachaPolicy(onlyCommon).draw(GachaState(Long.MAX_VALUE), pools, pickups) { .5 } }
    }
}
