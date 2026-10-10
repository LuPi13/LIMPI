package com.github.lupi13.limpi.delivery

import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class DeliveryResolutionTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val items = listOf(ItemGrant("original custom item", 10, 64))

    private fun reviewing(store: StateStore): Pair<DeliveryJournal, DeliveryRecord> {
        val journal = DeliveryJournal(store)
        store.atomic { journal.stage(it, id, owner, "original purchase", items) }
        val before = listOf<StoredStack?>(null)
        val applying = journal.begin(journal.find(owner, id)!!, before, InventoryPlan.grant(before, items))
        journal.review(applying, "ambiguous saved playerdata")
        return journal to journal.find(owner, id)!!
    }

    @Test fun `operator confirms delivered and preserves all previous evidence in an idempotent audit receipt`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val (journal, previous) = reviewing(store)
            val request = UUID.randomUUID()
            journal.resolve(request, owner, id, "CONSOLE", DeliveryResolution.DELIVERED, "verified player file and item owner")
            assertEquals(DeliveryState.DONE, journal.find(owner, id)?.state); assertTrue(journal.pending(owner).isEmpty())
            val receipt = YamlFiles.parse(store.entry(request)?.receipt)
            assertEquals("CONSOLE", receipt.getString("actor")); assertEquals("DELIVERED", receipt.getString("decision"))
            val evidence = YamlFiles.parse(receipt.getString("previousEvidence"))
            assertEquals(previous.checkpointId.toString(), evidence.getString("checkpoint"))
            assertNotNull(evidence.getList("before")); assertNotNull(evidence.getList("after"))
            assertDoesNotThrow { journal.resolve(request, owner, id, "CONSOLE", DeliveryResolution.DELIVERED, "verified player file and item owner") }
            assertThrows(TransactionIdentityConflict::class.java) { journal.resolve(request, owner, id, "CONSOLE", DeliveryResolution.RETRY, "different decision") }
        }
    }

    @Test fun `retry has a new physical checkpoint so an older stored checkpoint cannot masquerade as this attempt`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val (journal, previous) = reviewing(store)
            journal.resolve(UUID.randomUUID(), owner, id, "operator UUID", DeliveryResolution.RETRY, "verified item was not saved")
            val pending = journal.find(owner, id)!!
            assertEquals(DeliveryState.PENDING, pending.state); assertEquals(items, pending.items)
            assertNull(pending.before); assertNull(pending.after)
            val before = listOf<StoredStack?>(null)
            val second = journal.begin(pending, before, InventoryPlan.grant(before, items))
            assertNotEquals(previous.checkpointId, second.checkpointId)
            var applied = 0
            val inventory = object : DeliveryInventory {
                var image = before
                var saved = previous.checkpointId
                override fun snapshot() = image
                override fun persistedCheckpoint() = saved
                override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) { applied++; image = after; saved = id }
            }
            assertEquals(DeliveryResult(1, 0), DeliveryService(journal).drain(owner, inventory, true))
            assertEquals(1, applied); assertEquals(10, inventory.snapshot().single()?.count)
        }
    }

    @Test fun `failed operator resolution retains REVIEW and all item rights until retry`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("repair commit failed") }).use { store ->
            val (journal, previous) = reviewing(store); val request = UUID.randomUUID(); fault = true
            assertThrows(IllegalStateException::class.java) { journal.resolve(request, owner, id, "CONSOLE", DeliveryResolution.RETRY, "verified original file") }
            assertEquals(previous, journal.find(owner, id)); assertNull(store.entry(request))
            fault = false; journal.resolve(request, owner, id, "CONSOLE", DeliveryResolution.RETRY, "verified original file")
            assertEquals(DeliveryState.PENDING, journal.find(owner, id)?.state)
        }
    }

    @Test fun `ordinary pending claims cannot be administratively marked delivered and invalid plans do not persist`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val journal = DeliveryJournal(store)
            store.atomic { journal.stage(it, id, owner, "purchase", items) }
            assertThrows(IllegalStateException::class.java) { journal.resolve(UUID.randomUUID(), owner, id, "CONSOLE", DeliveryResolution.DELIVERED, "skip ordinary pending") }
            assertThrows(IllegalArgumentException::class.java) {
                journal.begin(journal.find(owner, id)!!, listOf(null), listOf(StoredStack("wrong item", 10, 64)))
            }
            assertEquals(DeliveryState.PENDING, journal.find(owner, id)?.state)
        }
    }
}
