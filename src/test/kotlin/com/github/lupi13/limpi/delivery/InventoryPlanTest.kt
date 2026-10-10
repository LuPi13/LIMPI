package com.github.lupi13.limpi.delivery

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InventoryPlanTest {
    @Test fun `whole bundle must fit even if its first item fits`() {
        val before = listOf(StoredStack("ticket", 59, 64))
        assertThrows(InventoryFull::class.java) { InventoryPlan.grant(before, listOf(ItemGrant("ticket", 10, 64))) }
        assertEquals(59, before.single().count)
    }

    @Test fun `partial stacks are filled before allocating new slots and quantities are conserved`() {
        val before = listOf(StoredStack("ticket", 59, 64), null, StoredStack("other", 12, 64))
        val after = InventoryPlan.grant(before, listOf(ItemGrant("ticket", 10, 64)))
        assertEquals(listOf(StoredStack("ticket", 64, 64), StoredStack("ticket", 5, 64), before[2]), after)
        assertEquals(10, after.filterNotNull().sumOf { if (it.payload == "ticket") it.count else 0 } - 59)
        assertNull(before[1])
    }

    @Test fun `distinct custom components cannot be stacked just because the material matches`() {
        val before = listOf(StoredStack("paper-check-A", 1, 64), null)
        val after = InventoryPlan.grant(before, listOf(ItemGrant("paper-check-B", 1, 64)))
        assertEquals(before[0], after[0]); assertEquals("paper-check-B", after[1]?.payload)
    }

    @Test fun `multiple grant lines combine safely and nonstackable items need individual slots`() {
        assertEquals(listOf(StoredStack("ticket", 64, 64), StoredStack("ticket", 6, 64)),
            InventoryPlan.grant(listOf(null, null), listOf(ItemGrant("ticket", 35, 64), ItemGrant("ticket", 35, 64))))
        assertThrows(InventoryFull::class.java) { InventoryPlan.grant(listOf(null), listOf(ItemGrant("sword", 2, 1))) }
    }

    @Test fun `oversized Long quantities fail without wrapping or allocating an item per quantity`() {
        assertThrows(InventoryFull::class.java) { InventoryPlan.grant(List(36) { null }, listOf(ItemGrant("stone", Long.MAX_VALUE, 64))) }
    }

    @Test fun `an unsuccessful later line does not partially alter the original slots`() {
        val before = listOf<StoredStack?>(null)
        assertThrows(InventoryFull::class.java) { InventoryPlan.grant(before, listOf(ItemGrant("a", 1, 64), ItemGrant("b", 1, 64))) }
        assertEquals(listOf<StoredStack?>(null), before)
    }
}
