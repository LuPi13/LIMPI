package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class MarketEscrowTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val before = listOf(StoredStack("custom item", 10, 64), StoredStack("unrelated item", 1, 1))
    private fun prepare(escrow: MarketEscrow) = escrow.prepare(id, owner, "seller", "AB12", "custom item", Money(100), before, 0)
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var savedImage = image
        var checkpoint: UUID? = null
        var changes = 0
        var beforeSave: () -> Unit = {}
        var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; changes++; beforeSave()
            savedImage = image; checkpoint = id; afterSave()
        }
        fun reload() { image = savedImage; beforeSave = {}; afterSave = {} }
    }

    @Test fun `prepared listing reserves its code but stays unbuyable until physical removal is saved`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog)
            val record = prepare(escrow)
            assertEquals(ListingState.ESCROW, catalog.find(id)?.state); assertEquals(id, catalog.findByCode("AB12")?.id)
            val inventory = Inventory(before); escrow.apply(record, inventory)
            assertEquals(ListingState.ACTIVE, catalog.find(id)?.state); assertTrue(escrow.pending(owner).isEmpty())
            assertEquals(listOf(null, before[1]), inventory.image); assertEquals(record.checkpoint, inventory.checkpoint)
            inventory.image = before // The player obtains another identical stack later.
            escrow.apply(record, inventory)
            assertEquals(before, inventory.image); assertEquals(1, inventory.changes)
        }
    }

    @Test fun `prepare DB failure cannot publish a listing or reserve a code`() {
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { error("prepare fault") }).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog)
            assertThrows(IllegalStateException::class.java) { prepare(escrow) }
            assertNull(catalog.find(id)); assertNull(catalog.findByCode("AB12")); assertTrue(escrow.pending(owner).isEmpty())
        }
    }

    @Test fun `failure before saving the removed item recovers from the original saved inventory on fresh login`() {
        val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog)
            inventory.beforeSave = { error("save failed") }
            assertThrows(DeliveryRecoveryRequired::class.java) { escrow.apply(prepare(escrow), inventory) }
            assertEquals(ListingState.ESCROW, catalog.find(id)?.state)
            assertThrows(DeliveryRecoveryRequired::class.java) { escrow.recover(owner, inventory, false) }
        }
        inventory.reload()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); MarketEscrow(store, catalog).recover(owner, inventory, true)
            assertEquals(ListingState.ACTIVE, catalog.find(id)?.state); assertEquals(listOf(null, before[1]), inventory.image)
        }
    }

    @Test fun `failure after save or during ACTIVE commit recovers without removing another stack`() {
        var fault = false
        val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("activate fault") }).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog)
            inventory.afterSave = { fault = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { escrow.apply(prepare(escrow), inventory) }
            assertEquals(ListingState.ESCROW, catalog.find(id)?.state); assertEquals(1, inventory.changes)
        }
        inventory.reload()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); MarketEscrow(store, catalog).recover(owner, inventory, true)
            assertEquals(ListingState.ACTIVE, catalog.find(id)?.state); assertEquals(1, inventory.changes)
        }
    }

    @Test fun `unexpected inventory remains intact and escrow is protected for review`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog); prepare(escrow)
            val image = listOf(StoredStack("different valuable item", 1, 1), before[1])
            val inventory = Inventory(image)
            assertThrows(DeliveryRecoveryRequired::class.java) { escrow.recover(owner, inventory, true) }
            assertEquals(image, inventory.image); assertEquals(0, inventory.changes)
            assertEquals(EscrowState.REVIEW, escrow.pending(owner).single().state)
            assertEquals(ListingState.ESCROW, catalog.find(id)?.state)
        }
    }

    @Test fun `operator retained resolution cancels hidden listing without creating a duplicate return grant`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog); prepare(escrow)
            val request = UUID.randomUUID()
            escrow.resolve(request, "CONSOLE", owner, id, false, "confirmed original stack remains saved")
            assertEquals(ListingState.RETURNED, catalog.find(id)?.state); assertNull(catalog.findByCode("AB12"))
            assertTrue(escrow.pending(owner).isEmpty()); assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
            assertNotNull(YamlFiles.parse(store.entry(request)?.receipt).getString("previousEvidence"))
            assertDoesNotThrow { escrow.resolve(request, "CONSOLE", owner, id, false, "confirmed original stack remains saved") }
        }
    }

    @Test fun `operator confirmed removal activates escrow while recording the decision`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog); prepare(escrow)
            val request = UUID.randomUUID(); escrow.resolve(request, "CONSOLE", owner, id, true, "confirmed item removal in player file")
            assertEquals(ListingState.ACTIVE, catalog.find(id)?.state)
            assertTrue(YamlFiles.parse(store.entry(request)?.receipt).getBoolean("removed"))
        }
    }

    @Test fun `code conflict does not stage another player escrow and same prepare ID replays its original checkpoint`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val catalog = MarketCatalog(store); val escrow = MarketEscrow(store, catalog); val first = prepare(escrow)
            assertEquals(first, prepare(escrow))
            val other = UUID.randomUUID()
            assertThrows(MarketException::class.java) { escrow.prepare(UUID.randomUUID(), other, "other", "AB12", "item", Money(100), before, 0) }
            assertTrue(escrow.pending(other).isEmpty()); assertEquals(id, catalog.findByCode("AB12")?.id)
        }
    }
}
