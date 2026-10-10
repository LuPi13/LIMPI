package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class AbilityItemUseTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID(); private val id = UUID.randomUUID()
    private val before = List<StoredStack?>(37) { if (it == 0) StoredStack("original sword", 2, 64) else if (it == 36) StoredStack("ordinary offhand", 1, 1) else null }
    private val after = before.toMutableList().apply { this[0] = before[0]!!.copy(count = 1) }.toList()
    private val terms = AbilityItemTerms("blade_leaper", 0, "original sword", ItemGrant("worn sword", 1, 64))
    private fun setup(store: StateStore) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: player\nmoney: 0\nability: blade_leaper\n") }
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(AbilityItemUse.KIND to AbilityItemUse(DeliveryJournal(store))))
    private fun prepare(store: StateStore, frozen: AbilityItemTerms = terms) = journal(store).prepare(id, owner, AbilityItemUse.KIND, frozen.encode(), before, after)
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null; var saves = 0; var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; checkpoint = id; saves++; afterSave()
        }
    }
    @Test fun `saved single sword removal atomically reserves one frozen worn sword return`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before)
            inventory.afterSave = { assertTrue(DeliveryJournal(store).pending(owner).isEmpty()) }
            journal(store).apply(prepare(store), inventory)
            assertEquals(after, inventory.image); assertEquals(listOf(terms.returned), DeliveryJournal(store).pending(owner).single().items)
            assertEquals(RemovalState.DONE, journal(store).find(owner, id)!!.state)
            assertTrue(DeliveryJournal(store).pending(UUID.randomUUID()).isEmpty())
        }
    }
    @Test fun `replay does not remove another sword or create a second return`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before); journal(store).apply(prepare(store), inventory)
            inventory.image = before; journal(store).apply(prepare(store), inventory)
            assertEquals(1, inventory.saves); assertEquals(before, inventory.image); assertEquals(1, DeliveryJournal(store).pending(owner).size)
            assertThrows(TransactionIdentityConflict::class.java) { prepare(store, terms.copy(returned = null)) }
        }
    }
    @Test fun `broken sword is consumed without a return record`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).apply(prepare(store, terms.copy(returned = null)), Inventory(before))
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty()); assertEquals(RemovalState.DONE, journal(store).find(owner, id)!!.state)
        }
    }
    @Test fun `crash after sword save recovers return from frozen terms after restart`() {
        var fail = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fail) error("crash") }).use { store ->
            setup(store); val record = prepare(store); inventory.afterSave = { fail = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
        }
        inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); journal(store).recover(owner, inventory, true)
            assertEquals(1, inventory.saves); assertEquals(terms.returned, DeliveryJournal(store).pending(owner).single().items.single())
        }
    }
    @Test fun `wrong source or removing the whole stack cannot reserve a return`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms.copy(payload = "wrong")) }
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(id, owner, AbilityItemUse.KIND, terms.encode(), before, after.toMutableList().apply { this[0] = null }) }
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty()); assertTrue(journal(store).pending(owner).isEmpty())
        }
    }
    @Test fun `operator retained judgment never grants a sword and removed judgment grants once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); prepare(store); journal(store).resolve(UUID.randomUUID(), "operator", owner, id, false, "not removed")
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
            val second = UUID.randomUUID(); journal(store).prepare(second, owner, AbilityItemUse.KIND, terms.encode(), before, after)
            val request = UUID.randomUUID(); journal(store).resolve(request, "operator", owner, second, true, "saved removal")
            journal(store).resolve(request, "operator", owner, second, true, "saved removal")
            assertEquals(1, DeliveryJournal(store).pending(owner).size)
        }
    }
}
