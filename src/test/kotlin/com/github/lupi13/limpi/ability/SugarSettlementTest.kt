package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class SugarSettlementTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID(); private val id = UUID.randomUUID()
    private val before = List<StoredStack?>(37) { if (it == 36) StoredStack("sugar NBT", 2, 64) else null }
    private val after = before.toMutableList().apply { this[36] = before[36]!!.copy(count = 1) }.toList()
    private val terms = SugarTerms(36, "sugar NBT", "minecraft:instant_damage", 1, UUID.randomUUID(), listOf(UUID.randomUUID()), 10.0)
    private fun setup(store: StateStore) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: player\nmoney: 0\nability: suspicious_sugar\n") }
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(SugarSettlement.KIND to SugarSettlement(store)))
    private fun prepare(store: StateStore, value: SugarTerms = terms) = journal(store).prepare(id, owner, SugarSettlement.KIND, value.encode(), before, after)
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null; var saves = 0; var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; checkpoint = id; saves++; afterSave()
        }
    }
    @Test fun `saved sugar consumption freezes exact effect duration world and target ids`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before)
            inventory.afterSave = { assertTrue(SugarSettlement(store).pending(owner).isEmpty()) }
            journal(store).apply(prepare(store), inventory)
            assertEquals(after, inventory.image); assertEquals(mapOf(id to terms), SugarSettlement(store).pending(owner))
            assertEquals(terms, SugarSettlement(store).activate(owner, id)); assertNull(SugarSettlement(store).activate(owner, id))
            assertTrue(SugarSettlement(store).pending(owner).isEmpty())
        }
    }
    @Test fun `crash after consumption save recovers same result without another sugar removal`() {
        var fail = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fail) error("crash") }).use { store ->
            setup(store); val record = prepare(store); inventory.afterSave = { fail = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
        }
        inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); journal(store).recover(owner, inventory, true)
            assertEquals(1, inventory.saves); assertEquals(terms, SugarSettlement(store).activate(owner, id))
        }
    }
    @Test fun `crash after activation permit never replays an uncertain instant effect`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).apply(prepare(store), Inventory(before)); assertEquals(terms, SugarSettlement(store).activate(owner, id))
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            assertTrue(SugarSettlement(store).pending(owner).isEmpty()); assertNull(SugarSettlement(store).activate(owner, id))
        }
    }
    @Test fun `activation commit failure leaves the frozen result available and no permit returned`() {
        var fail = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fail) error("crash") }).use { store ->
            setup(store); journal(store).apply(prepare(store), Inventory(before)); fail = true
            assertThrows(IllegalStateException::class.java) { SugarSettlement(store).activate(owner, id) }
            assertEquals(mapOf(id to terms), SugarSettlement(store).pending(owner)); fail = false
            assertEquals(terms, SugarSettlement(store).activate(owner, id))
        }
    }
    @Test fun `replay cannot change the effect or reserve another consumption before activation`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).apply(prepare(store), Inventory(before))
            assertThrows(TransactionIdentityConflict::class.java) { prepare(store, terms.copy(effect = "minecraft:instant_health")) }
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(UUID.randomUUID(), owner, SugarSettlement.KIND, terms.encode(), before, after) }
        }
    }
    @Test fun `wrong cost source self area target or invalid frozen effect is rejected`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms.copy(sugar = "wrong")) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms.copy(targets = listOf(owner))) }
            assertThrows(IllegalArgumentException::class.java) { terms.copy(effect = "not a key") }
            assertThrows(IllegalArgumentException::class.java) { terms.copy(duration = 0) }
            assertThrows(IllegalArgumentException::class.java) { terms.copy(targets = List(257) { UUID.randomUUID() }) }
            assertTrue(journal(store).pending(owner).isEmpty())
        }
    }
    @Test fun `offline retained judgment does not create an effect and removed grants one activation`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); prepare(store); journal(store).resolve(UUID.randomUUID(), "admin", owner, id, false, "retained")
            assertTrue(SugarSettlement(store).pending(owner).isEmpty())
            val second = UUID.randomUUID(); journal(store).prepare(second, owner, SugarSettlement.KIND, terms.encode(), before, after)
            val request = UUID.randomUUID(); journal(store).resolve(request, "admin", owner, second, true, "removed")
            journal(store).resolve(request, "admin", owner, second, true, "removed")
            assertEquals(terms, SugarSettlement(store).activate(owner, second)); assertNull(SugarSettlement(store).activate(owner, second))
        }
    }
    @Test fun `administrator void ends an unappliable result once without refund or activation`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).apply(prepare(store), Inventory(before))
            val settlement = SugarSettlement(store); val request = UUID.randomUUID()
            assertEquals(listOf(id), settlement.pendingIds(owner))
            assertTrue(settlement.void(request, "CONSOLE", owner, id, "unknown effect after datapack change"))
            assertFalse(settlement.void(request, "CONSOLE", owner, id, "unknown effect after datapack change"))
            assertTrue(settlement.pendingIds(owner).isEmpty()); assertTrue(settlement.pending(owner).isEmpty())
            assertThrows(Exception::class.java) { settlement.void(UUID.randomUUID(), "CONSOLE", owner, id, "again") }
            assertThrows(Exception::class.java) { settlement.activate(owner, id) }
            assertTrue(store.get("sugar_result/$owner/$id") != null)
        }
    }
}
