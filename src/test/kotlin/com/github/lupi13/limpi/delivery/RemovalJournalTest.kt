package com.github.lupi13.limpi.delivery

import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.market.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class RemovalJournalTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val before = listOf(StoredStack("custom stone", 10, 64), StoredStack("unchanged", 1, 1), StoredStack("custom wood", 5, 64))
    private val after = listOf(null, before[1], before[2].copy(count = 2))
    private val prices = mapOf(0 to Money(3_000_000_000), 2 to Money(2))
    private fun setup(store: StateStore, money: Long = 10) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: seller\nmoney: $money\ncustom: preserved\n") }
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(SaleSettlement.KIND to SaleSettlement))
    private fun prepare(journal: RemovalJournal) = journal.prepare(id, owner, SaleSettlement.KIND, SaleSettlement.terms(prices), before, after)
    private fun money(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var saved = image; var checkpoint: UUID? = null; var changes = 0
        var beforeSave: () -> Unit = {}; var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; changes++; beforeSave()
            saved = image; checkpoint = id; afterSave()
        }
        fun reload() { image = saved; beforeSave = {}; afterSave = {} }
    }

    @Test fun `multi slot exact removal reaches saved inventory before settlement and replay never removes new items`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val journal = journal(store); val record = prepare(journal)
            assertEquals(10, money(store)); val inventory = Inventory(before)
            inventory.afterSave = { assertEquals(10, money(store)); assertEquals(after, inventory.saved) }
            journal.apply(record, inventory)
            assertEquals(30_000_000_016, money(store)); assertEquals(after, inventory.image)
            assertTrue(journal.pending(owner).isEmpty()); assertEquals(RemovalState.DONE, journal.find(owner, id)?.state)
            inventory.image = before; journal.apply(prepare(journal), inventory)
            assertEquals(before, inventory.image); assertEquals(1, inventory.changes); assertEquals(30_000_000_016, money(store))
        }
    }
    @Test fun `prepare commit failure leaves no pending or receipt and does not change money`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("prepare fault") }).use { store ->
            setup(store); fault = true
            assertThrows(IllegalStateException::class.java) { prepare(journal(store)) }
            assertTrue(journal(store).pending(owner).isEmpty()); assertNull(store.entry(id)); assertEquals(10, money(store))
        }
    }
    @Test fun `failure before saving removal recovers original persisted inventory on fresh login only`() {
        val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val journal = journal(store); val record = prepare(journal)
            inventory.beforeSave = { error("save failed") }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal.apply(record, inventory) }
            assertEquals(10, money(store)); assertEquals(before, inventory.saved)
            assertThrows(DeliveryRecoveryRequired::class.java) { journal.recover(owner, inventory, false) }
        }
        inventory.reload()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); assertEquals(30_000_000_016, money(store)); assertEquals(after, inventory.saved)
        }
    }
    @Test fun `saved removal with failed settlement commits once after restart without physical removal again`() {
        var fault = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("settlement fault") }).use { store ->
            setup(store); val record = prepare(journal(store)); inventory.afterSave = { fault = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
            assertEquals(10, money(store)); assertEquals(after, inventory.saved); assertEquals(RemovalState.APPLYING, journal(store).pending(owner).single().state)
        }
        inventory.reload()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); journal(store).recover(owner, inventory, true)
            assertEquals(30_000_000_016, money(store)); assertEquals(1, inventory.changes)
        }
    }
    @Test fun `unknown inventory becomes review without overwriting and retained resolution neither credits nor returns items`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val journal = journal(store); val record = prepare(journal)
            val inventory = Inventory(listOf(null, before[1], before[2]))
            assertThrows(DeliveryRecoveryRequired::class.java) { journal.apply(record, inventory) }
            assertEquals(RemovalState.REVIEW, journal.pending(owner).single().state); assertEquals(0, inventory.changes)
            val request = UUID.randomUUID(); journal.resolve(request, "CONSOLE", owner, id, false, "original retained")
            journal.resolve(request, "CONSOLE", owner, id, false, "original retained")
            assertEquals(10, money(store)); assertTrue(journal.pending(owner).isEmpty()); assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
            val receipt = YamlFiles.parse(store.entry(request)!!.receipt)
            assertEquals("CONSOLE", receipt.getString("actor")); assertNotNull(receipt.getString("previousEvidence"))
        }
    }
    @Test fun `failure after saved removal keeps the debt and settles on fresh login without another physical change`() {
        val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val record = prepare(journal(store)); inventory.afterSave = { error("saved but call failed") }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
            assertEquals(after, inventory.saved); assertEquals(10, money(store))
        }
        inventory.reload()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true)
            assertEquals(30_000_000_016, money(store)); assertEquals(1, inventory.changes)
        }
    }
    @Test fun `removed operator decision and failed resolve preserve full state before idempotent credit`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("resolve fault") }).use { store ->
            setup(store); val journal = journal(store); prepare(journal); val request = UUID.randomUUID(); fault = true
            assertThrows(IllegalStateException::class.java) { journal.resolve(request, "operator", owner, id, true, "saved after verified") }
            assertEquals(10, money(store)); assertEquals(1, journal.pending(owner).size); assertNull(store.entry(request))
            fault = false; journal.resolve(request, "operator", owner, id, true, "saved after verified")
            journal.resolve(request, "operator", owner, id, true, "saved after verified")
            assertEquals(30_000_000_016, money(store)); assertTrue(journal.pending(owner).isEmpty())
        }
    }
    @Test fun `overflow and mismatched price slots are rejected before physical removal`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, Long.MAX_VALUE)
            assertEquals(BankFailure.OVERFLOW, assertThrows(BankException::class.java) { prepare(journal(store)) }.reason)
            assertTrue(journal(store).pending(owner).isEmpty())
            setup(store)
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(id, owner, SaleSettlement.KIND, SaleSettlement.terms(mapOf(0 to Money(1))), before, after) }
            assertEquals(10, money(store)); assertNull(store.entry(id))
        }
    }
    @Test fun `removal plans cannot create move replace or increase any stack`() {
        for (bad in listOf(before, listOf(before[0], before[1], before[2].copy(count = 6)),
            listOf(before[0], before[1], StoredStack("changed", 1, 64)), listOf(before[2], before[1], before[0]))) {
            assertThrows(IllegalArgumentException::class.java) { PhysicalRemoval.validate(before, bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { PhysicalRemoval.validate(listOf(null, before[1]), listOf(before[0], null)) }
    }
    @Test fun `market and generalized removals reserve one shared checkpoint operation per owner`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val journal = journal(store); prepare(journal)
            val escrow = MarketEscrow(store, MarketCatalog(store))
            assertThrows(IllegalStateException::class.java) { escrow.prepare(UUID.randomUUID(), owner, "seller", "AB12", "item", Money(100), before, 0) }
            journal.resolve(UUID.randomUUID(), "operator", owner, id, false, "retained")
            escrow.prepare(UUID.randomUUID(), owner, "seller", "AB12", "item", Money(100), before, 0)
            assertThrows(IllegalStateException::class.java) { journal.prepare(UUID.randomUUID(), owner, SaleSettlement.KIND, SaleSettlement.terms(prices), before, after) }
            assertTrue(journal.pending(owner).isEmpty())
        }
    }
    @Test fun `account rejection after saved removal becomes review instead of looping and admin can settle after repair`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val journal = journal(store); val record = prepare(journal)
            val inventory = Inventory(before)
            // The balance grows between prepare and settlement so the frozen credit would overflow.
            inventory.afterSave = { setup(store, Long.MAX_VALUE) }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal.apply(record, inventory) }
            assertEquals(after, inventory.saved); assertEquals(RemovalState.REVIEW, journal.pending(owner).single().state)
            assertEquals(Long.MAX_VALUE, money(store))
            assertThrows(DeliveryRecoveryRequired::class.java) { journal.recover(owner, inventory, freshLogin = true) }
            setup(store, 10)
            journal.resolve(UUID.randomUUID(), "CONSOLE", owner, id, true, "balance repaired; removal verified offline")
            assertEquals(30_000_000_016, money(store)); assertEquals(RemovalState.DONE, journal.find(owner, id)?.state)
        }
    }
}
