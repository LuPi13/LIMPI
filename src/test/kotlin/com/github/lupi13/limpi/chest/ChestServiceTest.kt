package com.github.lupi13.limpi.chest

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.recovery.PendingReviews
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class ChestServiceTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val prices = ChestPrices.DEFAULT
    private fun stack(name: String, count: Int = 1) = StoredStack(name, count, 64)

    private class Inventory(var image: List<StoredStack?>, var saved: UUID? = null) : DeliveryInventory {
        override fun snapshot() = image
        override fun persistedCheckpoint() = saved
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) = error("not used")
        override fun checkpoint(current: List<StoredStack?>, id: UUID) { check(image == current); saved = id }
    }

    // Unit on purpose: JUnit silently skips test methods that return a value.
    private fun withService(balance: Long = 10_000_000, body: (StateStore, ChestService) -> Unit) {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            if (store.get(SqliteProfilePersistence.key(owner)) == null)
                store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: owner\nmoney: $balance\ncustom: retained\n") }
            body(store, ChestService(store, CachedPlayerRepository(SqliteProfilePersistence(store))))
        }
    }
    private fun balance(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private fun buy(service: ChestService, product: ChestProduct) =
        service.buy(UUID.randomUUID(), owner, product, requireNotNull(prices.quote(service.state(owner), product)).units, prices)

    @Test fun `unlock then upgrades grow one row at a time with rising prices up to six rows`() = withService { store, service ->
        assertNull(prices.quote(service.state(owner), ChestProduct.UPGRADE))
        assertEquals(1, buy(service, ChestProduct.UNLOCK).rows)
        assertEquals(listOf(20_000L, 40_000L, 80_000L, 160_000L, 320_000L), (1..5).map {
            val price = prices.quote(service.state(owner), ChestProduct.UPGRADE)!!.units
            assertEquals(it + 1, buy(service, ChestProduct.UPGRADE).rows); price
        })
        assertEquals(54, service.state(owner).slots.size)
        assertNull(prices.quote(service.state(owner), ChestProduct.UPGRADE))
        assertThrows(ChestUnavailable::class.java) { service.buy(UUID.randomUUID(), owner, ChestProduct.UPGRADE, 0, prices) }
        assertEquals(10_000_000 - 10_000 - 620_000, balance(store))
        assertEquals("retained", YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getString("custom"))
    }

    @Test fun `features need an unlocked chest and a stale displayed price is refused without charging`() = withService { store, service ->
        assertThrows(ChestUnavailable::class.java) { service.buy(UUID.randomUUID(), owner, ChestProduct.AUTO_STORE, 50_000, prices) }
        buy(service, ChestProduct.UNLOCK)
        assertThrows(ChestUnavailable::class.java) { service.buy(UUID.randomUUID(), owner, ChestProduct.ENDER, 999, prices) }
        assertEquals(10_000_000 - 10_000, balance(store))
        assertTrue(buy(service, ChestProduct.AUTO_STORE).autoEnabled)
        assertTrue(buy(service, ChestProduct.ENDER).ender)
        assertThrows(ChestUnavailable::class.java) { service.buy(UUID.randomUUID(), owner, ChestProduct.ENDER, 1_000_000, prices) }
    }

    @Test fun `insufficient funds change nothing and the same request is not charged twice`() = withService(15_000) { store, service ->
        val request = UUID.randomUUID()
        service.buy(request, owner, ChestProduct.UNLOCK, 10_000, prices)
        service.buy(request, owner, ChestProduct.UNLOCK, 10_000, prices)
        assertEquals(5_000, balance(store))
        val failure = assertThrows(BankException::class.java) { service.buy(UUID.randomUUID(), owner, ChestProduct.UPGRADE, 20_000, prices) }
        assertEquals(BankFailure.INSUFFICIENT_FUNDS, failure.reason); assertEquals(1, service.state(owner).rows)
    }

    @Test fun `auto store toggles only after purchase`() = withService { _, service ->
        buy(service, ChestProduct.UNLOCK)
        assertThrows(ChestUnavailable::class.java) { service.setAuto(owner, null) }
        buy(service, ChestProduct.AUTO_STORE)
        assertFalse(service.setAuto(owner, null)); assertTrue(service.setAuto(owner, null)); assertFalse(service.setAuto(owner, false))
    }

    @Test fun `absorbing merges then fills and records how much of the ground item remains`() = withService { _, service ->
        buy(service, ChestProduct.UNLOCK); buy(service, ChestProduct.AUTO_STORE)
        val first = UUID.randomUUID(); val second = UUID.randomUUID()
        assertEquals(40, service.absorb(UUID.randomUUID(), owner, first, "dirt", 40, 64))
        assertEquals(0, service.absorbedRemaining(first))
        // 24 merge into the first stack and the other 8 slots take 64 each: 9 * 64 - 40 = 536 of 600 fit.
        assertEquals(536, service.absorb(UUID.randomUUID(), owner, second, "dirt", 600, 64))
        assertEquals(64, service.absorbedRemaining(second))
        assertEquals(0, service.absorb(UUID.randomUUID(), owner, UUID.randomUUID(), "dirt", 1, 64))
        assertTrue(service.state(owner).slots.all { it == stack("dirt", 64) })
        service.setAuto(owner, false)
        assertEquals(0, service.absorb(UUID.randomUUID(), owner, UUID.randomUUID(), "stone", 1, 64))
    }

    @Test fun `absorbed records survive a restart`() {
        val entity = UUID.randomUUID()
        withService { _, service -> buy(service, ChestProduct.UNLOCK); buy(service, ChestProduct.AUTO_STORE); service.absorb(UUID.randomUUID(), owner, entity, "dirt", 10, 64) }
        withService { _, service -> assertEquals(0, service.absorbedRemaining(entity)) }
    }

    @Test fun `death empties a normal chest but not an ender chest`() = withService { _, service ->
        assertTrue(service.deathDrop(UUID.randomUUID(), owner).isEmpty())
        buy(service, ChestProduct.UNLOCK); buy(service, ChestProduct.AUTO_STORE)
        service.absorb(UUID.randomUUID(), owner, UUID.randomUUID(), "dirt", 10, 64)
        assertEquals(listOf(stack("dirt", 10)), service.deathDrop(UUID.randomUUID(), owner))
        assertTrue(service.state(owner).slots.all { it == null })
        service.absorb(UUID.randomUUID(), owner, UUID.randomUUID(), "dirt", 10, 64)
        buy(service, ChestProduct.ENDER)
        assertTrue(service.deathDrop(UUID.randomUUID(), owner).isEmpty())
        assertEquals(stack("dirt", 10), service.state(owner).slots[0])
    }

    @Test fun `a normal close commits the after image`() = withService { _, service ->
        buy(service, ChestProduct.UNLOCK)
        val inventory = Inventory(listOf(stack("dirt"), null))
        val session = service.open(owner, inventory.image); inventory.checkpoint(inventory.image, session.openCheckpoint)
        inventory.image = listOf(null, null)
        val closing = service.closing(session, inventory.image, listOf(stack("dirt")) + List(8) { null })
        inventory.checkpoint(inventory.image, closing.closeCheckpoint!!); service.complete(closing)
        assertEquals(stack("dirt"), service.state(owner).slots[0]); assertTrue(service.pending(owner).isEmpty())
    }

    @Test fun `recovery keeps the opened chest when the saved player data is from before the window`() = withService { _, service ->
        buy(service, ChestProduct.UNLOCK)
        val inventory = Inventory(listOf(stack("dirt"), null))
        val session = service.open(owner, inventory.image); inventory.checkpoint(inventory.image, session.openCheckpoint)
        service.closing(session, listOf(null, null), listOf(stack("dirt")) + List(8) { null }) // crash before the close save
        assertThrows(DeliveryRecoveryRequired::class.java) { service.recover(owner, inventory, freshLogin = false) }
        assertEquals(ChestRecovery.ROLLED_BACK, service.recover(owner, inventory, freshLogin = true))
        assertTrue(service.state(owner).slots.all { it == null }); assertTrue(service.pending(owner).isEmpty())
    }

    @Test fun `recovery applies the close when the close checkpoint was saved`() = withService { _, service ->
        buy(service, ChestProduct.UNLOCK)
        val inventory = Inventory(listOf(stack("dirt"), null))
        val session = service.open(owner, inventory.image); inventory.checkpoint(inventory.image, session.openCheckpoint)
        inventory.image = listOf(null, null)
        val closing = service.closing(session, inventory.image, listOf(stack("dirt")) + List(8) { null })
        inventory.checkpoint(inventory.image, closing.closeCheckpoint!!) // crash before completing in the DB
        assertEquals(ChestRecovery.COMPLETED, service.recover(owner, inventory, freshLogin = true))
        assertEquals(stack("dirt"), service.state(owner).slots[0])
    }

    @Test fun `an open checkpoint that never reached the file means the window was never shown`() = withService { _, service ->
        buy(service, ChestProduct.UNLOCK)
        val inventory = Inventory(listOf(stack("dirt"), null), saved = UUID.randomUUID())
        service.open(owner, inventory.image)
        inventory.image = listOf(null, stack("other")) // older save; nothing came from the chest
        assertEquals(ChestRecovery.ROLLED_BACK, service.recover(owner, inventory, freshLogin = true))
    }

    @Test fun `a save from the middle of the window is held for an administrator`() = withService { store, service ->
        buy(service, ChestProduct.UNLOCK)
        val inventory = Inventory(listOf(null, null))
        val session = service.open(owner, inventory.image); inventory.checkpoint(inventory.image, session.openCheckpoint)
        inventory.image = listOf(stack("from chest"), null) // autosave while the window was open
        assertThrows(DeliveryRecoveryRequired::class.java) { service.recover(owner, inventory, freshLogin = true) }
        assertEquals(ChestSessionState.REVIEW, service.pending(owner).single().state)
        assertThrows(DeliveryRecoveryRequired::class.java) { service.recover(owner, inventory, freshLogin = true) }
        val review = PendingReviews.list(store).single()
        assertEquals("chest", review.kind); assertTrue(review.command.startsWith("/limpi chest resolve $owner ${session.id}"))
        assertThrows(IllegalArgumentException::class.java) { service.resolve(UUID.randomUUID(), "admin", owner, session.id, true, "checked") }
        service.resolve(UUID.randomUUID(), "admin", owner, session.id, false, "checked saved inventory")
        assertTrue(service.pending(owner).isEmpty()); assertTrue(service.state(owner).slots.all { it == null })
    }

    @Test fun `an open window blocks purchases death drops and other inventory operations`() = withService { store, service ->
        buy(service, ChestProduct.UNLOCK); buy(service, ChestProduct.AUTO_STORE)
        service.absorb(UUID.randomUUID(), owner, UUID.randomUUID(), "dirt", 1, 64)
        service.open(owner, listOf(null))
        assertThrows(IllegalStateException::class.java) { buy(service, ChestProduct.UPGRADE) }
        assertThrows(IllegalStateException::class.java) { service.deathDrop(UUID.randomUUID(), owner) }
        assertThrows(IllegalStateException::class.java) { service.open(owner, listOf(null)) }
        val removals = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf("TEST" to object : RemovalEffect {
            override fun validate(tx: StateTransaction, record: RemovalRecord) = Unit
            override fun settle(tx: StateTransaction, record: RemovalRecord) = "ok"
        }))
        assertThrows(IllegalStateException::class.java) { removals.prepare(UUID.randomUUID(), owner, "TEST", "", listOf(stack("dirt")), listOf(null)) }
    }

    @Test fun `opening waits for other unresolved inventory operations`() = withService { store, service ->
        buy(service, ChestProduct.UNLOCK)
        store.atomic { it.put("removal_pending/$owner/${UUID.randomUUID()}", "state: APPLYING\n") }
        assertThrows(IllegalStateException::class.java) { service.open(owner, listOf(null)) }
    }

    @Test fun `damaged documents and prices are refused instead of reset`() = withService { store, service ->
        store.atomic { it.put(ChestDocuments.key(owner), "schemaVersion: 1\nowner: $owner\nrows: 1\nautoOwned: maybe\nautoEnabled: false\nender: false\nslots: []\n") }
        assertThrows(IllegalArgumentException::class.java) { service.state(owner) }
        assertThrows(IllegalArgumentException::class.java) { service.buy(UUID.randomUUID(), owner, ChestProduct.UPGRADE, 20_000, prices) }
        val config = YamlConfiguration().apply {
            set("ChestUnlockPrice", 10_000); set("ChestUpgradePrice", listOf(1, 2, 3, 4, 5)); set("ChestAutoStorePrice", 1.5); set("ChestEnderPrice", 1)
        }
        assertThrows(IllegalArgumentException::class.java) { ChestPrices.read(config) }
        config.set("ChestAutoStorePrice", 50_000)
        assertEquals(Money(50_000), ChestPrices.read(config).autoStore)
        config.set("ChestUpgradePrice", listOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) { ChestPrices.read(config) }
    }

    @Test fun `unknown fields in the chest document are preserved`() = withService { store, service ->
        buy(service, ChestProduct.UNLOCK)
        store.atomic { tx -> tx.put(ChestDocuments.key(owner), tx.get(ChestDocuments.key(owner)) + "future: kept\n") }
        service.release(owner); buy(service, ChestProduct.UPGRADE)
        assertEquals("kept", YamlFiles.parse(store.get(ChestDocuments.key(owner))).getString("future"))
    }

    @Test fun `packaged config prices match the agreed defaults`() {
        val config = javaClass.classLoader.getResourceAsStream("config.yml")!!.use { YamlConfiguration().apply { loadFromString(it.readBytes().toString(Charsets.UTF_8)) } }
        assertEquals(ChestPrices.DEFAULT, ChestPrices.read(config))
        assertTrue(config.getBoolean("EnableChestSystem"))
    }
}
