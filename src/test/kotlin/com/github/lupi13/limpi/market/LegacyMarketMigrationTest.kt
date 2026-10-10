package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.ItemGrant
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class LegacyMarketMigrationTest {
    @TempDir lateinit var directory: Path
    private val seller = UUID.randomUUID()
    private val legacy = "AB12:\n  seller: OldName\n  price: 3000000000\n  item: custom-payload\n  custom: preserved\n"
    private fun item(value: Any) = ItemGrant(value as String, 10, 64)

    @Test fun `legacy Long prices owner UUID items and unknown original fields are preserved`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put("financial/market", legacy) }
            val catalog = MarketCatalog(store)
            assertEquals(1, LegacyMarketMigration.import(store, catalog, { seller }, ::item))
            val listing = catalog.findByCode("AB12")!!
            assertEquals(seller, listing.seller); assertEquals(3000000000L, listing.price.units)
            assertEquals("custom-payload", listing.item.payload); assertEquals(legacy, store.get("financial/market"))
            assertEquals(0, LegacyMarketMigration.import(store, catalog, { error("must not re-import") }, ::item))
        }
    }

    @Test fun `ambiguous legacy owner becomes protected and can be resolved with an audited explicit UUID`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put("financial/market", legacy); it.put(SqliteProfilePersistence.key(seller), "name: CurrentName\nmoney: 0\n") }
            val catalog = MarketCatalog(store); LegacyMarketMigration.import(store, catalog, { null }, ::item)
            val listing = catalog.findByCode("AB12")!!
            assertEquals(ListingState.OWNERSHIP_REVIEW, listing.state); assertNull(listing.seller)
            val request = UUID.randomUUID()
            val service = MarketService(store, catalog, com.github.lupi13.limpi.delivery.DeliveryJournal(store), CachedPlayerRepository(SqliteProfilePersistence(store)))
            service.assignOwner(request, "CONSOLE", listing.id, seller, "verified legacy player identity")
            assertEquals(ListingState.ACTIVE, catalog.find(listing.id)?.state); assertEquals(seller, catalog.find(listing.id)?.seller)
            assertNotNull(YamlFiles.parse(store.entry(request)?.receipt).getString("previousEvidence"))
        }
    }

    @Test fun `one malformed item aborts the whole catalog import and retains the legacy document`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val bad = legacy + "BAD2:\n  seller: OldName\n  price: 100\n  item: malformed\n"
            store.atomic { it.put("financial/market", bad) }
            val catalog = MarketCatalog(store)
            assertThrows(IllegalArgumentException::class.java) { LegacyMarketMigration.import(store, catalog, { seller }) {
                require(it != "malformed"); item(it)
            } }
            assertFalse(catalog.imported()); assertTrue(catalog.listings().isEmpty()); assertEquals(bad, store.get("financial/market"))
        }
    }

    @Test fun `a legacy zero price remains a valid free listing rather than preventing startup`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put("financial/market", legacy.replace("3000000000", "0")) }
            val catalog = MarketCatalog(store); LegacyMarketMigration.import(store, catalog, { seller }, ::item)
            assertEquals(0L, catalog.findByCode("AB12")?.price?.units)
        }
    }
}
