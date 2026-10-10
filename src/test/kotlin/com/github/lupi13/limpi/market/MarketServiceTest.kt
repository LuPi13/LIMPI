package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MarketServiceTest {
    @Test fun `code reuse creates a distinct listing and an old quote cannot buy the replacement`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); service(store).withdraw(UUID.randomUUID(), seller, listingId)
            val replacement = UUID.randomUUID()
            store.atomic { MarketCatalog(store).write(it, MarketListing(replacement, "AB12", "replacement", seller, "seller", Money(101), item)) }
            assertEquals(replacement, MarketCatalog(store).findByCode("AB12")?.id)
            assertEquals(MarketFailure.NOT_AVAILABLE, assertThrows(MarketException::class.java) {
                service(store).buy(UUID.randomUUID(), buyer, listingId, 101)
            }.reason)
            assertEquals(1000, money(store, buyer)); assertEquals(ListingState.ACTIVE, MarketCatalog(store).find(replacement)?.state)
        }
    }

    @Test fun `free sale retains the item claim without charging either account`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, price = 0)
            val result = service(store).buy(UUID.randomUUID(), buyer, listingId, 0)
            assertEquals(0, result.fee); assertEquals(1000, money(store, buyer)); assertEquals(200, money(store, seller))
            assertEquals(item, DeliveryJournal(store).pending(buyer).single().items.single())
        }
    }
    @TempDir lateinit var directory: Path
    private val buyer = UUID.randomUUID()
    private val seller = UUID.randomUUID()
    private val secondBuyer = UUID.randomUUID()
    private val listingId = UUID.randomUUID()
    private val item = ItemGrant("full custom item data", 10, 64)

    private fun setup(store: StateStore, price: Long = 101, sellerMoney: Long = 200, state: ListingState = ListingState.ACTIVE) {
        store.atomic { tx ->
            tx.put(SqliteProfilePersistence.key(buyer), "name: buyer\nmoney: 1000\nunlockedAbilities: [CustomAbility]\n")
            tx.put(SqliteProfilePersistence.key(secondBuyer), "name: second\nmoney: 1000\n")
            tx.put(SqliteProfilePersistence.key(seller), "name: RenamedSeller\nmoney: $sellerMoney\ncustom: preserved\n")
            MarketCatalog(store).write(tx, MarketListing(listingId, "AB12", "Custom item", seller, "OldSellerName", Money(price), item, state))
        }
    }
    private fun service(store: StateStore): MarketService = MarketService(store, MarketCatalog(store),
        DeliveryJournal(store), CachedPlayerRepository(SqliteProfilePersistence(store)))
    private fun money(store: StateStore, player: UUID) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(player))).getLong("money")

    @Test fun `buyer seller fee sold state and pending custom item commit together using seller UUID`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            val receipt = service(store).buy(UUID.randomUUID(), buyer, listingId, 101)
            assertEquals(899, money(store, buyer)); assertEquals(296, money(store, seller)); assertEquals(5, receipt.fee)
            assertEquals(1195, money(store, buyer) + money(store, seller))
            assertEquals(ListingState.SOLD, MarketCatalog(store).find(listingId)?.state)
            assertEquals(item, DeliveryJournal(store).pending(buyer).single().items.single())
            assertEquals(listOf("CustomAbility"), YamlFiles.parse(store.get(SqliteProfilePersistence.key(buyer))).getStringList("unlockedAbilities"))
            assertEquals("preserved", YamlFiles.parse(store.get(SqliteProfilePersistence.key(seller))).getString("custom"))
        }
    }

    @Test fun `failed settlement preserves every account listing and delivery before retry`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit fault") }).use { store ->
            setup(store); fault = true; val id = UUID.randomUUID(); val market = service(store)
            assertThrows(IllegalStateException::class.java) { market.buy(id, buyer, listingId, 101) }
            assertEquals(1000, money(store, buyer)); assertEquals(200, money(store, seller))
            assertEquals(ListingState.ACTIVE, MarketCatalog(store).find(listingId)?.state)
            assertTrue(DeliveryJournal(store).pending(buyer).isEmpty()); assertNull(store.entry(id))
            fault = false; assertFalse(market.buy(id, buyer, listingId, 101).replayed)
        }
    }

    @Test fun `purchase replay after restart does not charge again or recreate a delivery`() {
        val id = UUID.randomUUID()
        SqliteStateStore(directory.resolve("state.db")).use { store -> setup(store); service(store).buy(id, buyer, listingId, 101) }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            assertTrue(service(store).buy(id, buyer, listingId, 101).replayed)
            assertEquals(899, money(store, buyer)); assertEquals(296, money(store, seller))
            assertEquals(1, DeliveryJournal(store).pending(buyer).size)
            assertThrows(TransactionIdentityConflict::class.java) { service(store).buy(id, secondBuyer, listingId, 101) }
        }
    }

    @Test fun `competing buyers through independent DB connections cannot buy the same listing twice`() {
        SqliteStateStore(directory.resolve("state.db")).use { first ->
            setup(first)
            SqliteStateStore(directory.resolve("state.db")).use { second ->
                val workers = Executors.newFixedThreadPool(2)
                try {
                    val markets = listOf(service(first), service(second))
                    val buyers = listOf(buyer, secondBuyer)
                    val requests = markets.indices.map { index -> workers.submit<Boolean> {
                        try { markets[index].buy(UUID.randomUUID(), buyers[index], listingId, 101); true }
                        catch (failure: MarketException) { assertEquals(MarketFailure.NOT_AVAILABLE, failure.reason); false }
                    } }
                    assertEquals(1, requests.count { it.get(5, TimeUnit.SECONDS) })
                    assertEquals(1899, money(first, buyer) + money(first, secondBuyer)); assertEquals(296, money(first, seller))
                    assertEquals(1, DeliveryJournal(first).pending(buyer).size + DeliveryJournal(first).pending(secondBuyer).size)
                } finally { workers.shutdownNow() }
            }
        }
    }

    @Test fun `stale quote self purchase insufficient balance and seller overflow cannot partially settle`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, sellerMoney = Long.MAX_VALUE)
            val market = service(store)
            assertEquals(MarketFailure.STALE_QUOTE, assertThrows(MarketException::class.java) { market.buy(UUID.randomUUID(), buyer, listingId, 100) }.reason)
            assertEquals(MarketFailure.OWN_LISTING, assertThrows(MarketException::class.java) { market.buy(UUID.randomUUID(), seller, listingId, 101) }.reason)
            assertEquals(BankFailure.OVERFLOW, assertThrows(BankException::class.java) { market.buy(UUID.randomUUID(), buyer, listingId, 101) }.reason)
            store.atomic { it.put(SqliteProfilePersistence.key(buyer), "money: 100\n") }
            assertEquals(BankFailure.INSUFFICIENT_FUNDS, assertThrows(BankException::class.java) { market.buy(UUID.randomUUID(), buyer, listingId, 101) }.reason)
            assertEquals(100, money(store, buyer)); assertEquals(Long.MAX_VALUE, money(store, seller))
            assertEquals(ListingState.ACTIVE, MarketCatalog(store).find(listingId)?.state)
            assertTrue(DeliveryJournal(store).pending(buyer).isEmpty())
        }
    }

    @Test fun `withdrawal retains a durable owner claim without crediting anyone and retry is idempotent`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val market = service(store); val id = UUID.randomUUID()
            assertEquals(MarketFailure.NOT_OWNER, assertThrows(MarketException::class.java) {
                market.withdraw(UUID.randomUUID(), buyer, listingId)
            }.reason)
            assertFalse(market.withdraw(id, seller, listingId).replayed); assertTrue(market.withdraw(id, seller, listingId).replayed)
            assertEquals(200, money(store, seller)); assertEquals(ListingState.RETURNED, MarketCatalog(store).find(listingId)?.state)
            assertEquals(item, DeliveryJournal(store).pending(seller).single().items.single())
        }
    }
}
