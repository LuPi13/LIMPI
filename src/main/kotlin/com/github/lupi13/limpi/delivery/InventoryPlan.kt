package com.github.lupi13.limpi.delivery

/** Payload is a lossless, single-item serialization, including every component/PDC value. */
data class StoredStack(val payload: String, val count: Int, val limit: Int) {
    init { require(payload.isNotBlank()); require(limit > 0); require(count in 1..limit) }
}

data class ItemGrant(val payload: String, val count: Long, val limit: Int) {
    init { require(payload.isNotBlank()); require(count > 0); require(limit > 0) }
}

class InventoryFull : IllegalStateException("The complete delivery does not fit")

object InventoryPlan {
    /** Build a complete replacement without touching the caller's inventory, or fail without a partial result. */
    fun grant(before: List<StoredStack?>, items: List<ItemGrant>): List<StoredStack?> {
        require(before.isNotEmpty()); require(items.isNotEmpty())
        val after = before.toMutableList()
        for (item in items) {
            var remaining = item.count
            for (slot in after.indices) {
                val stack = after[slot] ?: continue
                if (stack.payload != item.payload || stack.limit != item.limit) continue
                val count = minOf(remaining, (stack.limit - stack.count).toLong()).toInt()
                if (count > 0) after[slot] = stack.copy(count = stack.count + count)
                remaining -= count
            }
            for (slot in after.indices) {
                if (remaining == 0L) break
                if (after[slot] != null) continue
                val count = minOf(remaining, item.limit.toLong()).toInt()
                after[slot] = StoredStack(item.payload, count, item.limit)
                remaining -= count
            }
            if (remaining != 0L) throw InventoryFull()
        }
        return after.toList()
    }
}
