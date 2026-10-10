package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class PickPocketSettlementTest {
    @TempDir lateinit var directory: Path
    private val victim = UUID.randomUUID(); private val thief = UUID.randomUUID(); private val id = UUID.randomUUID()
    private val before = List<StoredStack?>(41) { if (it == 39) StoredStack("custom helmet with PDC", 1, 1) else if (it == 40) StoredStack("offhand unchanged", 12, 64) else null }
    private val after = before.toMutableList().apply { this[39] = null }.toList()
    private val terms = PickPocketTerms(thief, 39, before[39]!!)
    private fun setup(store: StateStore) = store.atomic { tx ->
        for (owner in listOf(victim, thief)) tx.put(SqliteProfilePersistence.key(owner), "name: $owner\nmoney: 0\nunknown: keep\n")
    }
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(PickPocketSettlement.KIND to PickPocketSettlement(DeliveryJournal(store))))
    private fun prepare(store: StateStore, value: PickPocketTerms = terms, image: List<StoredStack?> = after) = journal(store).prepare(id, victim, PickPocketSettlement.KIND, value.encode(), before, image)
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null; var saves = 0; var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; checkpoint = id; saves++; afterSave()
        }
    }
    @Test fun `saved victim armor removal commits frozen receiver delivery without touching offhand`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before)
            inventory.afterSave = { assertTrue(DeliveryJournal(store).pending(thief).isEmpty()) }
            journal(store).apply(prepare(store), inventory)
            assertEquals(after, inventory.image); assertEquals(before[40], inventory.image[40])
            val item = DeliveryJournal(store).pending(thief).single().items.single()
            assertEquals(ItemGrant(terms.stolen.payload, 1, 1), item)
            assertTrue(DeliveryJournal(store).pending(victim).isEmpty())
            assertTrue(store.get(SqliteProfilePersistence.key(victim))!!.contains("unknown: keep"))
        }
    }
    @Test fun `replay cannot remove more items or switch the receiver`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before); journal(store).apply(prepare(store), inventory)
            journal(store).apply(prepare(store), inventory)
            assertEquals(1, inventory.saves); assertEquals(1, DeliveryJournal(store).pending(thief).size)
            assertThrows(TransactionIdentityConflict::class.java) { prepare(store, terms.copy(receiver = UUID.randomUUID())) }
        }
    }
    @Test fun `crash after victim save recovers recipient right even if recipient is offline`() {
        var fail = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fail) error("crash") }).use { store ->
            setup(store); val record = prepare(store); inventory.afterSave = { fail = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
        }
        inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(victim, inventory, true); journal(store).recover(victim, inventory, true)
            assertEquals(1, inventory.saves); assertEquals(terms.stolen.payload, DeliveryJournal(store).pending(thief).single().items.single().payload)
        }
    }
    @Test fun `wrong stack self receiver missing profile or multiple removed slots cannot prepare`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms.copy(stolen = terms.stolen.copy(payload = "wrong"))) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms.copy(receiver = victim)) }
            assertThrows(Exception::class.java) { prepare(store, terms.copy(receiver = UUID.randomUUID())) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store, image = after.toMutableList().apply { this[40] = null }) }
            assertTrue(journal(store).pending(victim).isEmpty()); assertTrue(DeliveryJournal(store).pending(thief).isEmpty())
        }
    }
    @Test fun `full recipient retains entire stolen stack right`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val offhandTerms = PickPocketTerms(thief, 40, before[40]!!)
            val image = before.toMutableList().apply { this[40] = null }
            journal(store).apply(prepare(store, offhandTerms, image), Inventory(before))
            val record = DeliveryJournal(store).pending(thief).single()
            assertEquals(12, record.items.single().count)
            assertThrows(InventoryFull::class.java) { InventoryPlan.grant(List(36) { StoredStack("full", 64, 64) }, record.items) }
            assertEquals(record, DeliveryJournal(store).pending(thief).single())
        }
    }
    @Test fun `offline retained resolution never grants and removed resolution grants to receiver once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); prepare(store); journal(store).resolve(UUID.randomUUID(), "admin", victim, id, false, "retained")
            assertTrue(DeliveryJournal(store).pending(thief).isEmpty())
            val second = UUID.randomUUID(); journal(store).prepare(second, victim, PickPocketSettlement.KIND, terms.encode(), before, after)
            val request = UUID.randomUUID(); journal(store).resolve(request, "admin", victim, second, true, "removed")
            journal(store).resolve(request, "admin", victim, second, true, "removed")
            assertEquals(1, DeliveryJournal(store).pending(thief).size)
        }
    }
}
