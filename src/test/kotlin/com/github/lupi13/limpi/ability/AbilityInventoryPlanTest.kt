package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AbilityInventoryPlanTest {
    private val active = StoredStack("new ability item", 1, 1)
    @Test fun `ordinary offhand relocates its entire stack without replacing any storage item`() {
        val before = List<StoredStack?>(36) { if (it == 0) StoredStack("shield", 63, 64) else null } + StoredStack("shield", 5, 64)
        val after = AbilityInventoryPlan.plan(before, null, null, 36, active)
        assertEquals(64, after[0]!!.count); assertEquals(4, after[1]!!.count); assertEquals(active, after[36])
        assertEquals(5, before[36]!!.count)
    }
    @Test fun `full storage refuses transition with original image intact`() {
        val before = List<StoredStack?>(36) { StoredStack("full $it", 64, 64) } + StoredStack("valuable offhand", 1, 1)
        assertThrows(InventoryFull::class.java) { AbilityInventoryPlan.plan(before, null, null, 36, active) }
        assertEquals("valuable offhand", before[36]!!.payload)
    }
    @Test fun `removal clears only the owned previous item and preserves ordinary offhand`() {
        val ordinary = List<StoredStack?>(36) { null } + StoredStack("normal", 1, 1)
        assertEquals(ordinary, AbilityInventoryPlan.plan(ordinary, 36, "old ability", null, null))
        val owned = ordinary.dropLast(1) + StoredStack("old ability", 1, 1)
        assertNull(AbilityInventoryPlan.plan(owned, 36, "old ability", null, null)[36])
    }
    @Test fun `same restricted slot changes owned active item without requiring empty storage`() {
        val before = List<StoredStack?>(36) { StoredStack("full $it", 64, 64) } + StoredStack("old ability", 1, 1)
        val after = AbilityInventoryPlan.plan(before, 36, "old ability", 36, active)
        assertEquals(before.take(36), after.take(36)); assertEquals(active, after[36])
    }
    @Test fun `future storage restricted slots do not relocate items into the reserved destination`() {
        val before = List<StoredStack?>(37) { if (it == 0) StoredStack("ordinary", 1, 1) else null }
        val after = AbilityInventoryPlan.plan(before, null, null, 0, active)
        assertEquals(active, after[0]); assertEquals("ordinary", after[1]!!.payload)
    }
}
