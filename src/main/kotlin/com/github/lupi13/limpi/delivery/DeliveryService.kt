package com.github.lupi13.limpi.delivery

import java.util.UUID

/** Implementations must use authoritative persisted player data, never just a live PDC value. */
interface DeliveryInventory {
    fun snapshot(): List<StoredStack?>
    fun persistedCheckpoint(): UUID?
    /** Check before, apply after, save player data and verify the checkpoint reached the player data file. */
    fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID)
}

class DeliveryRecoveryRequired(val owner: UUID, val delivery: UUID, cause: Throwable? = null) :
    IllegalStateException("Delivery $delivery for $owner needs recovery before further inventory use", cause)

data class DeliveryResult(val delivered: Int, val waitingForSpace: Int)

class DeliveryService(private val journal: DeliveryJournal) {
    // A failed physical operation cannot be retried against the same live inventory as though it were a fresh login.
    private val quarantined = mutableMapOf<UUID, UUID>()

    @Synchronized fun drain(owner: UUID, inventory: DeliveryInventory, freshLogin: Boolean = false): DeliveryResult {
        val records = journal.pending(owner)
        if (owner in quarantined && !freshLogin) throw DeliveryRecoveryRequired(owner, quarantined.getValue(owner))
        if (freshLogin) quarantined.remove(owner)
        if (records.any { it.state == DeliveryState.REVIEW }) {
            throw DeliveryRecoveryRequired(owner, records.first { it.state == DeliveryState.REVIEW }.id)
        }
        // Finish any interrupted operation BEFORE another operation can replace its checkpoint.
        val ordered = records.sortedBy { if (it.state == DeliveryState.APPLYING) 0 else 1 }
        check(ordered.count { it.state == DeliveryState.APPLYING } <= 1) { "Multiple interrupted deliveries for $owner" }
        var delivered = 0
        var waiting = 0
        for (record in ordered) {
            if (record.state == DeliveryState.APPLYING && !freshLogin) throw DeliveryRecoveryRequired(owner, record.id)
            val planned = if (record.state == DeliveryState.APPLYING) record else {
                val before = inventory.snapshot()
                val after = try { InventoryPlan.grant(before, record.items) } catch (_: InventoryFull) { waiting++; continue }
                journal.begin(record, before, after) // Durable intention before touching a physical slot.
            }
            try {
                val before = requireNotNull(planned.before)
                val after = requireNotNull(planned.after)
                val current = inventory.snapshot()
                val checkpoint = inventory.persistedCheckpoint()
                when {
                    checkpoint == planned.id && current == after -> Unit // Saved already; do not grant again.
                    checkpoint != planned.id && current == before -> inventory.applyAndCheckpoint(before, after, planned.id)
                    else -> {
                        journal.review(planned, "Saved checkpoint or inventory does not match the recorded before/after images")
                        throw DeliveryRecoveryRequired(owner, planned.id)
                    }
                }
                check(inventory.persistedCheckpoint() == planned.id) { "Delivery checkpoint was not persisted" }
                check(inventory.snapshot() == after) { "Inventory changed while checkpointing" }
                journal.complete(planned)
                delivered++
            } catch (failure: Exception) {
                quarantined[owner] = planned.id
                if (failure is DeliveryRecoveryRequired) throw failure
                throw DeliveryRecoveryRequired(owner, planned.id, failure)
            }
        }
        return DeliveryResult(delivered, waiting)
    }
}
