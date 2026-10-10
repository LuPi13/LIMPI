package com.github.lupi13.limpi.delivery

import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class DeliveryServiceTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val items = listOf(ItemGrant("ticket-components", 10, 64))

    private class Inventory(var slots: List<StoredStack?> = listOf(null)) : DeliveryInventory {
        var savedSlots = slots
        var savedId: UUID? = null
        var applications = 0
        var failBeforeSave = false
        var afterSave: () -> Unit = {}
        override fun snapshot() = slots.toList()
        override fun persistedCheckpoint() = savedId
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(slots == before)
            slots = after.toList(); applications++
            if (failBeforeSave) throw IllegalStateException("player save failed")
            savedSlots = slots; savedId = id
            afterSave()
        }
        fun reload() { slots = savedSlots; failBeforeSave = false; afterSave = {} }
    }

    private fun enqueue(store: StateStore, journal: DeliveryJournal, delivery: UUID = id) = store.atomic {
        journal.stage(it, delivery, owner, "purchase receipt", items)
    }

    @Test fun `a grant and the business debit are committed or rolled back together`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("DB failure") }).use { store ->
            val journal = DeliveryJournal(store)
            store.atomic { it.put("account/test", "100") }
            fault = true
            assertThrows(IllegalStateException::class.java) { store.atomic {
                it.put("account/test", "0"); journal.stage(it, id, owner, "purchase receipt", items)
            } }
            assertEquals("100", store.get("account/test")); assertTrue(journal.pending(owner).isEmpty())
            fault = false
            store.atomic { it.put("account/test", "0"); journal.stage(it, id, owner, "purchase receipt", items) }
            assertEquals("0", store.get("account/test")); assertEquals(id, journal.pending(owner).single().id)
        }
    }

    @Test fun `a full inventory keeps the whole grant pending without deleting or partially applying it`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            val inventory = Inventory(listOf(StoredStack("ticket-components", 59, 64)))
            val result = DeliveryService(journal).drain(owner, inventory)
            assertEquals(DeliveryResult(0, 1), result); assertEquals(0, inventory.applications)
            assertEquals(DeliveryState.PENDING, journal.find(owner, id)?.state)
        }
    }

    @Test fun `delivery completes only after a durable checkpoint and repeated drains do not grant twice`() {
        val inventory = Inventory()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            val service = DeliveryService(journal)
            assertEquals(DeliveryResult(1, 0), service.drain(owner, inventory))
            assertEquals(DeliveryState.DONE, journal.find(owner, id)?.state)
            assertEquals(DeliveryResult(0, 0), service.drain(owner, inventory))
            assertEquals(1, inventory.applications); assertEquals(10, inventory.slots.single()?.count)
            enqueue(store, journal) // A replayed producer cannot recreate a completed delivery.
            assertTrue(journal.pending(owner).isEmpty())
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            assertEquals(DeliveryResult(0, 0), DeliveryService(DeliveryJournal(store)).drain(owner, inventory, true))
            assertEquals(1, inventory.applications)
        }
    }

    @Test fun `failure before player save waits for a fresh authoritative reload instead of trusting live slots`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            val service = DeliveryService(journal); val inventory = Inventory().apply { failBeforeSave = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { service.drain(owner, inventory) }
            assertEquals(10, inventory.slots.single()?.count); assertNull(inventory.savedId)
            assertEquals(DeliveryState.APPLYING, journal.find(owner, id)?.state)
            assertThrows(DeliveryRecoveryRequired::class.java) { service.drain(owner, inventory) }
            inventory.reload()
            assertEquals(DeliveryResult(1, 0), service.drain(owner, inventory, true))
            assertEquals(10, inventory.savedSlots.single()?.count)
        }
    }

    @Test fun `failure after player save is resolved from the saved checkpoint without a duplicate grant`() {
        val inventory = Inventory()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            inventory.afterSave = { error("process stopped after player save") }
            assertThrows(DeliveryRecoveryRequired::class.java) { DeliveryService(journal).drain(owner, inventory) }
            assertEquals(journal.find(owner, id)?.checkpointId, inventory.savedId)
        }
        inventory.reload()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store)
            assertEquals(DeliveryResult(1, 0), DeliveryService(journal).drain(owner, inventory, true))
            assertEquals(1, inventory.applications); assertEquals(10, inventory.slots.single()?.count)
            assertEquals(DeliveryState.DONE, journal.find(owner, id)?.state)
        }
    }

    @Test fun `failure when committing DONE preserves the saved grant for restart recovery`() {
        var fault = false
        val inventory = Inventory()
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("DONE commit fault") }).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            inventory.afterSave = { fault = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { DeliveryService(journal).drain(owner, inventory) }
            assertEquals(DeliveryState.APPLYING, journal.find(owner, id)?.state)
            fault = false; inventory.reload()
            assertEquals(DeliveryResult(1, 0), DeliveryService(journal).drain(owner, inventory, true))
            assertEquals(1, inventory.applications)
        }
    }

    @Test fun `unexpected saved inventory is quarantined for review rather than overwritten or regranted`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            val before = listOf<StoredStack?>(null)
            journal.begin(journal.find(owner, id)!!, before, InventoryPlan.grant(before, items))
            val inventory = Inventory(listOf(StoredStack("unrelated property", 1, 1)))
            val service = DeliveryService(journal)
            assertThrows(DeliveryRecoveryRequired::class.java) { service.drain(owner, inventory, true) }
            assertEquals(DeliveryState.REVIEW, journal.find(owner, id)?.state)
            assertEquals(0, inventory.applications); assertEquals("unrelated property", inventory.slots.single()?.payload)
            assertThrows(DeliveryRecoveryRequired::class.java) { service.drain(owner, inventory, true) }
        }
    }

    @Test fun `an unresolved delivery prevents a later grant from overwriting its checkpoint`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            val before = listOf<StoredStack?>(null)
            journal.begin(journal.find(owner, id)!!, before, InventoryPlan.grant(before, items))
            enqueue(store, journal, UUID.randomUUID())
            val inventory = Inventory()
            assertThrows(DeliveryRecoveryRequired::class.java) { DeliveryService(journal).drain(owner, inventory) }
            assertEquals(0, inventory.applications)
        }
    }

    @Test fun `changing the quantity behind a delivery ID is rejected`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store); enqueue(store, journal)
            assertThrows(TransactionIdentityConflict::class.java) { store.atomic {
                journal.stage(it, id, owner, "purchase receipt", listOf(ItemGrant("ticket-components", 11, 64)))
            } }
            assertEquals(10L, journal.find(owner, id)?.items?.single()?.count)
        }
    }
}
