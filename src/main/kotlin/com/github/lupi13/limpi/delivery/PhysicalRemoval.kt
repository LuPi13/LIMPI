package com.github.lupi13.limpi.delivery

import java.util.UUID

data class RemovalEvidence(val id: UUID, val owner: UUID, val checkpoint: UUID,
                           val before: List<StoredStack?>, val after: List<StoredStack?>)

/** Shared market/sale/exchange protocol; settlement happens only after authoritative saved removal. */
object PhysicalRemoval {
    fun apply(evidence: RemovalEvidence, inventory: DeliveryInventory, review: (String) -> Unit, complete: () -> Unit) {
        try {
            val current = inventory.snapshot(); val saved = inventory.persistedCheckpoint()
            when {
                saved == evidence.checkpoint && current == evidence.after -> Unit
                saved != evidence.checkpoint && current == evidence.before ->
                    inventory.applyAndCheckpoint(evidence.before, evidence.after, evidence.checkpoint)
                else -> {
                    review("Inventory or saved checkpoint differs from the removal plan")
                    throw DeliveryRecoveryRequired(evidence.owner, evidence.id)
                }
            }
            check(inventory.persistedCheckpoint() == evidence.checkpoint && inventory.snapshot() == evidence.after)
            complete()
        } catch (failure: Exception) {
            if (failure is DeliveryRecoveryRequired) throw failure
            throw DeliveryRecoveryRequired(evidence.owner, evidence.id, failure)
        }
    }

    fun validate(before: List<StoredStack?>, after: List<StoredStack?>) {
        require(before.isNotEmpty() && before.size == after.size)
        var removed = false
        for (slot in before.indices) {
            val source = before[slot]; val remaining = after[slot]
            if (source == null) require(remaining == null)
            else {
                require(remaining == null || (source.payload == remaining.payload && source.limit == remaining.limit && remaining.count <= source.count))
                if (remaining == null || remaining.count < source.count) removed = true
            }
        }
        require(removed) { "No items were selected" }
    }
}
