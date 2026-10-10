package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class ItemExchangeTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val before = listOf(StoredStack("emerald", 64, 64), StoredStack("named emerald", 64, 64), StoredStack("emerald", 64, 64))
    private val terms = ExchangeTerms("EPIC", "emerald", 100, ItemGrant("legacy compatible epic ticket", 10, 64))
    private fun setup(store: StateStore) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: player\nmoney: 100\nunlockedAbilities: [Kept]\n") }
    private fun exchange(store: StateStore) = ItemExchange(store, DeliveryJournal(store))
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(ItemExchange.KIND to exchange(store)))
    private fun prepare(store: StateStore) = journal(store).prepare(id, owner, ItemExchange.KIND, terms.encode(), before, InventoryPlan.remove(before, terms.currency, terms.cost))
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null; var changes = 0
        var saved = image; var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; saved = after; checkpoint = id; changes++; afterSave()
        }
    }
    @Test fun `exact normal currency across multiple slots is removed while custom stacks and source image survive`() {
        assertEquals(128, InventoryPlan.count(before, "emerald"))
        assertEquals(listOf(null, before[1], before[2].copy(count = 28)), InventoryPlan.remove(before, "emerald", 100))
        assertEquals(64, before[0].count)
        assertThrows(IllegalArgumentException::class.java) { InventoryPlan.remove(before, "emerald", 129) }
        for (count in listOf(0L, -1L)) assertThrows(IllegalArgumentException::class.java) { InventoryPlan.remove(before, "emerald", count) }
    }
    @Test fun `saved currency removal stages all ten tickets with completion and replay cannot spend again`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val record = prepare(store); val inventory = Inventory(before)
            inventory.afterSave = { assertTrue(DeliveryJournal(store).pending(owner).isEmpty()) }
            journal(store).apply(record, inventory)
            assertEquals(terms.reward, DeliveryJournal(store).pending(owner).single().items.single())
            assertEquals(RemovalState.DONE, journal(store).find(owner, id)?.state)
            assertEquals(100, YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money"))
            inventory.image = before; journal(store).apply(prepare(store), inventory)
            assertEquals(before, inventory.image); assertEquals(1, inventory.changes); assertEquals(1, DeliveryJournal(store).pending(owner).size)
        }
    }
    @Test fun `settlement fault after saved payment leaves recoverable debt then stages rewards once after restart`() {
        var fault = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("settlement fault") }).use { store ->
            setup(store); val record = prepare(store); inventory.afterSave = { fault = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty()); assertEquals(1, journal(store).pending(owner).size)
        }
        inventory.image = inventory.saved; inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); journal(store).recover(owner, inventory, true)
            assertEquals(1, inventory.changes); assertEquals(terms.reward, DeliveryJournal(store).pending(owner).single().items.single())
        }
    }
    @Test fun `full reward quantity remains pending when only one ticket fits`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).apply(prepare(store), Inventory(before))
            val claim = DeliveryJournal(store).pending(owner).single()
            assertThrows(InventoryFull::class.java) { InventoryPlan.grant(listOf(StoredStack(terms.reward.payload, 63, 64)), claim.items) }
            assertEquals(10, DeliveryJournal(store).pending(owner).single().items.single().count)
        }
    }
    @Test fun `forged removal cost or other payload cannot prepare a reward claim`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(id, owner, ItemExchange.KIND, terms.encode(), before, InventoryPlan.remove(before, "emerald", 99)) }
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(id, owner, ItemExchange.KIND, terms.copy(cost = 64).encode(), before, InventoryPlan.remove(before, "named emerald", 64)) }
            assertTrue(journal(store).pending(owner).isEmpty()); assertTrue(DeliveryJournal(store).pending(owner).isEmpty()); assertNull(store.entry(id))
        }
    }
    @Test fun `retained resolution does not reward and removed resolution rewards once with audit`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); prepare(store); journal(store).resolve(UUID.randomUUID(), "operator", owner, id, false, "currency retained")
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
            val second = UUID.randomUUID()
            journal(store).prepare(second, owner, ItemExchange.KIND, terms.encode(), before, InventoryPlan.remove(before, "emerald", 100))
            val request = UUID.randomUUID()
            journal(store).resolve(request, "operator", owner, second, true, "saved removal verified")
            journal(store).resolve(request, "operator", owner, second, true, "saved removal verified")
            assertEquals(terms.reward, DeliveryJournal(store).pending(owner).single().items.single())
            assertEquals("operator", YamlFiles.parse(store.entry(request)!!.receipt).getString("actor"))
        }
    }
    @Test fun `zero price creates ten durable rewards without a removal and survives replay`() {
        val free = terms.copy(cost = 0)
        SqliteStateStore(directory.resolve("state.db")).use { store -> setup(store); assertFalse(exchange(store).free(id, owner, free)) }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            assertTrue(exchange(store).free(id, owner, free)); assertTrue(journal(store).pending(owner).isEmpty())
            assertEquals(terms.reward, DeliveryJournal(store).pending(owner).single().items.single())
            assertThrows(TransactionIdentityConflict::class.java) { exchange(store).free(id, owner, free.copy(reward = free.reward.copy(count = 1))) }
        }
    }
    @Test fun `free exchange commit fault leaves no partial reward or receipt`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("free fault") }).use { store ->
            setup(store); fault = true
            assertThrows(IllegalStateException::class.java) { exchange(store).free(id, owner, terms.copy(cost = 0)) }
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty()); assertNull(store.entry(id))
            fault = false; exchange(store).free(id, owner, terms.copy(cost = 0)); assertEquals(1, DeliveryJournal(store).pending(owner).size)
        }
    }
}
