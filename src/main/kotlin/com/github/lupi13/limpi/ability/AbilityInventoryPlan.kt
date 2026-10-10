package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*

/** Logical offhand index 36 follows storage; armor is never touched by an ability transition. */
object AbilityInventoryPlan {
    fun plan(before: List<StoredStack?>, previousSlot: Int?, previousItem: String?, nextSlot: Int?, nextItem: StoredStack?): List<StoredStack?> {
        require(before.size == 37)
        require(previousSlot == null || previousSlot in before.indices)
        require(nextSlot == null || nextSlot in before.indices)
        require((nextSlot == null) == (nextItem == null))
        val after = before.toMutableList()
        if (previousSlot != null && previousItem != null && after[previousSlot]?.payload == previousItem) after[previousSlot] = null
        if (nextSlot != null) {
            val displaced = after[nextSlot]
            if (displaced != null && displaced.payload != nextItem!!.payload) {
                val storageIndices = (0 until 36).filter { it != nextSlot }
                val storage = storageIndices.map { after[it] }
                val moved = InventoryPlan.grant(storage, listOf(ItemGrant(displaced.payload, displaced.count.toLong(), displaced.limit)))
                storageIndices.forEachIndexed { index, slot -> after[slot] = moved[index] }
            }
            after[nextSlot] = nextItem
        }
        return after.toList()
    }
}
