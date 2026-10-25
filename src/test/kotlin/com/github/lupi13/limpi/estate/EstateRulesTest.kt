package com.github.lupi13.limpi.estate

import com.github.lupi13.limpi.economy.Money
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class EstateRulesTest {
    private val world = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private fun estate(region: EstateRegion, members: Set<UUID> = emptySet()) =
        Estate(UUID.randomUUID(), owner, "e", region, "world", Money(0), 0, members)

    @Test fun `index finds the estate across chunk borders and negative coordinates`() {
        val a = estate(EstateRegion.of(world, -20, -64, -20, 20, 0, 20))
        val b = estate(EstateRegion.of(world, 21, 0, 0, 40, 10, 15))
        val index = EstateIndex(listOf(a, b))
        assertEquals(a, index.at(world, -20, -64, -20)); assertEquals(a, index.at(world, 20, 0, 20)); assertEquals(a, index.at(world, -1, -10, 15))
        assertNull(index.at(world, -21, 0, 0)); assertNull(index.at(world, 0, 1, 0))
        assertEquals(b, index.at(world, 21, 0, 0)); assertEquals(b, index.at(world, 40, 10, 15)); assertNull(index.at(world, 40, 10, 16))
        assertNull(index.at(UUID.randomUUID(), 0, 0, 0))
        assertTrue(index.hasWorld(world)); assertFalse(index.hasWorld(UUID.randomUUID()))
    }

    @Test fun `regions normalise corners, intersect and refuse overflowing volumes`() {
        val region = EstateRegion.of(world, 5, 10, 5, 0, 0, 0)
        assertEquals(EstateRegion(world, 0, 0, 0, 5, 10, 5), region)
        assertEquals(6L * 11 * 6, region.volume)
        assertEquals(EstateRegion(world, 5, 5, 5, 5, 10, 5), region.intersection(EstateRegion.of(world, 5, 5, 5, 9, 20, 9)))
        assertNull(region.intersection(EstateRegion.of(world, 6, 0, 0, 9, 9, 9)))
        assertNull(region.intersection(EstateRegion.of(UUID.randomUUID(), 0, 0, 0, 1, 1, 1)))
        assertThrows(ArithmeticException::class.java) { EstateRegion(world, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE).volume }
    }

    @Test fun `only the owner, trusted players and bypass may build`() {
        val home = estate(EstateRegion.of(world, 0, 0, 0, 1, 1, 1), setOf(member))
        assertTrue(EstateRules.canBuild(home, owner, false)); assertTrue(EstateRules.canBuild(home, member, false))
        assertFalse(EstateRules.canBuild(home, stranger, false)); assertFalse(EstateRules.canBuild(home, null, false))
        assertTrue(EstateRules.canBuild(home, stranger, true)); assertTrue(EstateRules.canBuild(null, stranger, false))
    }

    @Test fun `spreading, pistons, explosions and falling blocks stay out of other estates`() {
        val home = estate(EstateRegion.of(world, 0, 0, 0, 9, 9, 9))
        val next = estate(EstateRegion.of(world, 10, 0, 0, 19, 9, 9))
        // Water or fire: inside the same estate or in the wild is fine; from outside into an estate is not.
        assertTrue(EstateRules.spreadAllowed(home, home)); assertTrue(EstateRules.spreadAllowed(home, null)); assertTrue(EstateRules.spreadAllowed(null, null))
        assertFalse(EstateRules.spreadAllowed(null, home)); assertFalse(EstateRules.spreadAllowed(next, home))
        // A flying machine from the wild cannot push into or pull out of an estate; the owner's piston may push out into the wild.
        assertFalse(EstateRules.pistonAllowed(null, listOf(null, home)))
        assertTrue(EstateRules.pistonAllowed(home, listOf(home, null)))
        assertFalse(EstateRules.pistonAllowed(home, listOf(home, next)))
        // TNT primed outside or a creeper breaks nothing in the estate; the owner's own TNT or a trusted player's blast does.
        assertFalse(EstateRules.explosionBreaks(home, { false }, null))
        assertFalse(EstateRules.explosionBreaks(home, { false }, next.id))
        assertTrue(EstateRules.explosionBreaks(home, { false }, home.id))
        assertTrue(EstateRules.explosionBreaks(home, { it == home }, null))
        assertTrue(EstateRules.explosionBreaks(null, { false }, null))
        // Sand at the bottom of an estate does not fall when its support outside is dug away.
        assertFalse(EstateRules.fallAllowed(home, null)); assertTrue(EstateRules.fallAllowed(home, home)); assertTrue(EstateRules.fallAllowed(null, home))
    }
}
