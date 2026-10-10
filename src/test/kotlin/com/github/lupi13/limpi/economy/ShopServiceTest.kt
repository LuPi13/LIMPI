package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class ShopServiceTest {
    @TempDir lateinit var directory: Path
    private val buyer = UUID.randomUUID()
    private val request = UUID.randomUUID()
    private fun setup(store: StateStore, balance: Long = 10_000_000_000) = store.atomic { it.put(SqliteProfilePersistence.key(buyer), "name: buyer\nmoney: $balance\ncustom: retained\n") }
    private fun balance(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(buyer))).getLong("money")
    private fun service(store: StateStore, offer: (String) -> ShopOffer?) = ShopService(store, DeliveryJournal(store), CachedPlayerRepository(SqliteProfilePersistence(store)), offer)
    private fun offer(price: Long = 3_000_000_000) = ShopOffer("STONE", ItemGrant("plain stone", 1, 64), Money(price))

    @Test fun `large price and affordable bundle debit and full item quantity commit together`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            val result = service(store) { offer() }.buy(request, buyer, "STONE", 3_000_000_000, true)
            assertEquals(3, result.count); assertEquals(9_000_000_000, result.total); assertEquals(1_000_000_000, balance(store))
            assertEquals(3, DeliveryJournal(store).pending(buyer).single().items.single().count)
            assertEquals("retained", YamlFiles.parse(store.get(SqliteProfilePersistence.key(buyer))).getString("custom"))
        }
    }
    @Test fun `commit failure preserves debit delivery and receipt before same request retry`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit fault") }).use { store ->
            setup(store); val shop = service(store) { offer() }; fault = true
            assertThrows(IllegalStateException::class.java) { shop.buy(request, buyer, "STONE", 3_000_000_000, true) }
            assertEquals(10_000_000_000, balance(store)); assertTrue(DeliveryJournal(store).pending(buyer).isEmpty()); assertNull(store.entry(request))
            fault = false; shop.buy(request, buyer, "STONE", 3_000_000_000, true); assertEquals(1_000_000_000, balance(store))
        }
    }
    @Test fun `restart replay does not consult changed catalog charge again or grant another bundle`() {
        SqliteStateStore(directory.resolve("state.db")).use { store -> setup(store); service(store) { offer() }.buy(request, buyer, "STONE", 3_000_000_000, true) }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val shop = service(store) { error("catalog must not be consulted on replay") }
            assertTrue(shop.buy(request, buyer, "STONE", 3_000_000_000, true).replayed)
            assertEquals(1_000_000_000, balance(store)); assertEquals(1, DeliveryJournal(store).pending(buyer).size)
            assertThrows(TransactionIdentityConflict::class.java) { shop.buy(request, buyer, "STONE", 3_000_000_000, false) }
        }
    }
    @Test fun `free configured price supports a full stack without division by zero`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, 0); val result = service(store) { offer(0) }.buy(request, buyer, "STONE", 0, true)
            assertEquals(64, result.count); assertEquals(0, result.total); assertEquals(0, balance(store))
            assertEquals(64, DeliveryJournal(store).pending(buyer).single().items.single().count)
        }
    }
    @Test fun `deleted changed stale unaffordable and corrupt accounts cannot stage a purchase`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, 10)
            assertThrows(ShopUnavailable::class.java) { service(store) { null }.buy(request, buyer, "STONE", 100, false) }
            assertThrows(ShopUnavailable::class.java) { service(store) { offer(101) }.buy(request, buyer, "STONE", 100, false) }
            for (bundle in listOf(false, true)) assertEquals(BankFailure.INSUFFICIENT_FUNDS, assertThrows(BankException::class.java) { service(store) { offer(100) }.buy(request, buyer, "STONE", 100, bundle) }.reason)
            setup(store, -1)
            assertEquals(BankFailure.INVALID_ACCOUNT, assertThrows(BankException::class.java) { service(store) { offer(0) }.buy(request, buyer, "STONE", 0, true) }.reason)
            assertEquals(-1, balance(store)); assertTrue(DeliveryJournal(store).pending(buyer).isEmpty()); assertNull(store.entry(request))
        }
    }
}
