package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class AbilitySelectionTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val before = List<StoredStack?>(36) { null } + StoredStack("legacy epic ticket", 2, 64)
    private val after = before.dropLast(1) + before.last()!!.copy(count = 1)
    private val terms = SelectionTerms("legacy epic ticket", 36, "EPIC", "old", "chosen")
    private fun setup(store: StateStore) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: player\nmoney: 0\nability: old\nunlockedAbilities: [old, chosen, unknown_legacy]\n") }
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(AbilitySelection.KIND to AbilitySelection(store)))
    private fun prepare(store: StateStore, frozen: SelectionTerms = terms, request: UUID = id) =
        journal(store).prepare(request, owner, AbilitySelection.KIND, frozen.encode(), before, after)
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null
        var changes = 0
        var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; checkpoint = id; changes++; afterSave()
        }
    }
    @Test fun `saved ticket creates one durable application without changing profile before platform applies it`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before)
            inventory.afterSave = { assertTrue(AbilitySelection(store).pending(owner).isEmpty()) }
            journal(store).apply(prepare(store), inventory)
            assertEquals(terms, AbilitySelection(store).pending(owner).single().terms)
            val profile = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals("old", profile.getString("ability")); assertEquals(listOf("old", "chosen", "unknown_legacy"), profile.getStringList("unlockedAbilities"))
            assertEquals(RemovalState.DONE, journal(store).find(owner, id)!!.state)
        }
    }
    @Test fun `completed request cannot consume another ticket and different target conflicts`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before); journal(store).apply(prepare(store), inventory)
            inventory.image = before; journal(store).apply(prepare(store), inventory)
            assertEquals(before, inventory.image); assertEquals(1, inventory.changes)
            assertThrows(TransactionIdentityConflict::class.java) { prepare(store, terms.copy(ability = "other")) }
        }
    }
    @Test fun `commit fault after ticket save recovers frozen selection after restart`() {
        var fault = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("fault") }).use { store ->
            setup(store); val record = prepare(store); inventory.afterSave = { fault = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
            assertNull(AbilitySelection(store).find(owner, id)); assertTrue(AbilitySelection(store).pending(owner).isEmpty())
        }
        inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); journal(store).recover(owner, inventory, true)
            assertEquals(1, inventory.changes); assertEquals(terms, AbilitySelection(store).find(owner, id)!!.terms)
        }
    }
    @Test fun `locked stale wrong ticket and multiple source removal cannot prepare`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            for (frozen in listOf(terms.copy(ability = "locked"), terms.copy(previous = "stale"), terms.copy(ticket = "forged"))) {
                assertThrows(IllegalArgumentException::class.java) { prepare(store, frozen) }
            }
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(id, owner, AbilitySelection.KIND, terms.encode(), before, before.dropLast(1) + null) }
            assertNull(store.entry(id)); assertTrue(journal(store).pending(owner).isEmpty())
        }
    }
    @Test fun `retained resolution leaves no application while removed resolution is idempotent`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); prepare(store); journal(store).resolve(UUID.randomUUID(), "operator", owner, id, false, "ticket retained")
            assertTrue(AbilitySelection(store).pending(owner).isEmpty()); assertNull(AbilitySelection(store).find(owner, id))
            val second = UUID.randomUUID(); prepare(store, request = second); val request = UUID.randomUUID()
            journal(store).resolve(request, "operator", owner, second, true, "ticket removed")
            journal(store).resolve(request, "operator", owner, second, true, "ticket removed")
            assertEquals(second, AbilitySelection(store).pending(owner).single().id)
        }
    }
    @Test fun `ack requires actual persisted target and failed commit keeps pending application`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("ack fault") }).use { store ->
            setup(store); journal(store).apply(prepare(store), Inventory(before)); val service = AbilitySelection(store)
            val request = UUID.randomUUID()
            assertThrows(IllegalArgumentException::class.java) { service.acknowledge(request, owner, id, "chosen") }
            assertThrows(IllegalArgumentException::class.java) { prepare(store, request = UUID.randomUUID()) }
            store.atomic { tx -> val data = YamlFiles.parse(tx.get(SqliteProfilePersistence.key(owner))); data.set("ability", "chosen"); tx.put(SqliteProfilePersistence.key(owner), data.saveToString()) }
            fault = true
            assertThrows(IllegalStateException::class.java) { service.acknowledge(request, owner, id, "chosen") }
            assertEquals(1, service.pending(owner).size); assertNull(store.entry(request))
            fault = false; assertFalse(service.acknowledge(request, owner, id, "chosen"))
            assertTrue(service.acknowledge(request, owner, id, "chosen")); assertTrue(service.pending(owner).isEmpty())
        }
    }
}
