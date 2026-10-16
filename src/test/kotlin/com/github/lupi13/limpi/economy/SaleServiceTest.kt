package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class SaleServiceTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val recovery = 12 * 3_600_000L
    private val catalog = SellCatalog { mapOf<String, Any?>("diamond" to 120, "totem_of_undying" to mapOf("price" to 300, "half" to 5, "floor" to 30))[it] }
    private fun setup(store: StateStore, balance: Long = 1_000) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: seller\nmoney: $balance\ncustom: retained\n") }
    private fun balance(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private fun service(store: StateStore) = SaleService(store, CachedPlayerRepository(SqliteProfilePersistence(store)))
    private val totems = listOf(SaleLine(0, "totem_of_undying", 3))

    @Test fun `credit and sold count commit together and a replay pays once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val sales = service(store); val request = UUID.randomUUID()
            assertEquals(788, sales.sell(request, owner, totems, catalog, 0, recovery).total)
            assertEquals(1_788, balance(store)); assertEquals(3_000, sales.counters(owner).getValue("totem_of_undying").milli)
            assertTrue(sales.sell(request, owner, totems, catalog, 0, recovery).replayed)
            assertEquals(1_788, balance(store)); assertEquals(3_000, sales.counters(owner).getValue("totem_of_undying").milli)
            assertEquals("retained", YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getString("custom"))
            // The next sale continues from the stored count.
            assertEquals(catalog.quote(totems, sales.counters(owner), 0, recovery).total.units, sales.sell(UUID.randomUUID(), owner, totems, catalog, 0, recovery).total)
            assertEquals(6_000, sales.counters(owner).getValue("totem_of_undying").milli)
        }
    }

    @Test fun `commit failure keeps neither credit nor count`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit fault") }).use { store ->
            setup(store); fault = true
            assertThrows(IllegalStateException::class.java) { service(store).sell(UUID.randomUUID(), owner, totems, catalog, 0, recovery) }
            assertEquals(1_000, balance(store)); assertTrue(service(store).counters(owner).isEmpty())
        }
    }

    @Test fun `worthless sale records nothing and corrupt or overflowing accounts are refused`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            assertEquals(0, service(store).sell(UUID.randomUUID(), owner, listOf(SaleLine(0, "dirt", 64)), catalog, 0, recovery).total)
            assertEquals(1_000, balance(store)); assertNull(store.get(SaleCounters.key(owner)))
            setup(store, Long.MAX_VALUE - 10)
            assertEquals(BankFailure.OVERFLOW, assertThrows(BankException::class.java) { service(store).sell(UUID.randomUUID(), owner, listOf(SaleLine(0, "diamond", 1)), catalog, 0, recovery) }.reason)
            assertTrue(service(store).counters(owner).isEmpty())
            setup(store, -1)
            assertEquals(BankFailure.INVALID_ACCOUNT, assertThrows(BankException::class.java) { service(store).sell(UUID.randomUUID(), owner, totems, catalog, 0, recovery) }.reason)
            assertEquals(-1, balance(store)); assertTrue(service(store).counters(owner).isEmpty())
        }
    }
}
