package com.github.lupi13.limpi.estate

import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class EstateServiceTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val world = UUID.randomUUID()
    private val settings = EstateSettings.DEFAULT
    private fun box(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) = EstateRegion.of(world, x1, y1, z1, x2, y2, z2)

    // Unit on purpose: JUnit silently skips test methods that return a value.
    private fun withService(balance: Long = 1_000_000, body: (StateStore, EstateService) -> Unit) {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            for (id in listOf(owner, other)) if (store.get(SqliteProfilePersistence.key(id)) == null)
                store.atomic { it.put(SqliteProfilePersistence.key(id), "name: p$id\nmoney: $balance\ncustom: retained\n") }
            body(store, EstateService(store, CachedPlayerRepository(SqliteProfilePersistence(store))) { 1234L })
        }
    }
    private fun balance(store: StateStore, id: UUID = owner) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(id))).getLong("money")
    private fun buy(service: EstateService, region: EstateRegion, name: String = "home", who: UUID = owner) =
        service.buy(UUID.randomUUID(), who, name, region, "world", service.quote(who, region, "world", settings).units, settings)

    @Test fun `a purchase charges four per block and records the estate with the protection index`() = withService { store, service ->
        val region = box(10, 70, 10, 0, 64, 0) // corners in any order
        assertEquals(11 * 11 * 7 * 4L, service.quote(owner, region, "world", settings).units)
        val estate = buy(service, region)
        assertEquals(1_000_000 - 11 * 11 * 7 * 4L, balance(store))
        assertEquals("retained", YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getString("custom"))
        assertEquals(estate, service.at(world, 0, 64, 0)); assertEquals(estate, service.at(world, 10, 70, 10))
        assertNull(service.at(world, 11, 70, 10)); assertNull(service.at(world, 5, 63, 5)); assertNull(service.at(UUID.randomUUID(), 5, 65, 5))
        assertEquals(1234L, estate.createdAt); assertEquals(Money(11 * 11 * 7 * 4L), estate.paid)
        // A fresh service reads the same estate back from the DB.
        assertEquals(estate, EstateService(store, CachedPlayerRepository(SqliteProfilePersistence(store))).get(estate.id))
    }

    @Test fun `overlaps with anyone are refused when quoting and again inside the purchase`() = withService { store, service ->
        buy(service, box(0, 0, 0, 9, 9, 9))
        val overlap = assertThrows(EstateOverlap::class.java) { service.quote(other, box(5, 5, 5, 20, 20, 20), "world", settings) }
        assertEquals(listOf(box(5, 5, 5, 9, 9, 9)), overlap.parts)
        assertThrows(EstateOverlap::class.java) { service.quote(owner, box(9, 0, 0, 12, 1, 1), "world", settings) }
        // A quote taken before someone else bought the same place is refused at purchase time without charging.
        val stale = service.quote(other, box(20, 0, 20, 29, 9, 29), "world", settings)
        buy(service, box(25, 0, 25, 30, 5, 30), "late")
        assertThrows(EstateOverlap::class.java) { service.buy(UUID.randomUUID(), other, "mine", box(20, 0, 20, 29, 9, 29), "world", stale.units, settings) }
        assertEquals(1_000_000, balance(store, other))
        assertNotNull(buy(service, box(10, 0, 0, 12, 1, 1), "next")) // touching but not overlapping is fine
    }

    @Test fun `size, count, world and name rules`() = withService { _, service ->
        assertThrows(EstateUnavailable::class.java) { service.quote(owner, box(0, 0, 0, 128, 0, 0), "world", settings) }
        assertNotNull(service.quote(owner, box(0, -64, 0, 127, 319, 127), "world", settings)) // no height limit by default
        val limited = settings.copy(maxHeight = 10, worlds = setOf("world"))
        assertThrows(EstateUnavailable::class.java) { service.quote(owner, box(0, 0, 0, 1, 10, 1), "world", limited) }
        assertThrows(EstateUnavailable::class.java) { service.quote(owner, box(0, 0, 0, 1, 1, 1), "world_nether", limited) }
        assertThrows(EstateUnavailable::class.java) { service.buy(UUID.randomUUID(), owner, "a b", box(0, 0, 0, 0, 0, 0), "world", 4, settings) }
        assertThrows(EstateUnavailable::class.java) { service.buy(UUID.randomUUID(), owner, "x".repeat(17), box(0, 0, 0, 0, 0, 0), "world", 4, settings) }
        buy(service, box(0, 0, 0, 0, 0, 0), "Home")
        assertThrows(EstateUnavailable::class.java) { buy(service, box(2, 0, 0, 2, 0, 0), "home") }
        assertNotNull(buy(service, box(4, 0, 0, 4, 0, 0), "home", other)) // names are per owner
        for (i in 1 until 10) buy(service, box(10 + 2 * i, 0, 0, 10 + 2 * i, 0, 0), "e$i")
        assertEquals(10, service.owned(owner).size)
        assertThrows(EstateUnavailable::class.java) { service.quote(owner, box(100, 0, 0, 100, 0, 0), "world", settings) }
    }

    @Test fun `a stale price, missing money and a repeated request change nothing extra`() = withService(100) { store, service ->
        val region = box(0, 0, 0, 2, 0, 0) // 12
        assertThrows(EstateUnavailable::class.java) { service.buy(UUID.randomUUID(), owner, "a", region, "world", 11, settings) }
        val request = UUID.randomUUID()
        val estate = service.buy(request, owner, "a", region, "world", 12, settings)
        assertEquals(estate, service.buy(request, owner, "a", region, "world", 12, settings))
        assertEquals(88, balance(store))
        val failure = assertThrows(BankException::class.java) { buy(service, box(0, 5, 0, 30, 5, 0), "b") }
        assertEquals(BankFailure.INSUFFICIENT_FUNDS, failure.reason)
        assertEquals(1, service.owned(owner).size); assertEquals(88, balance(store))
    }

    @Test fun `selling refunds half of what was paid and frees the place`() = withService { store, service ->
        val estate = buy(service, box(0, 0, 0, 4, 4, 0)) // 25 blocks, 100
        assertEquals(50, service.refund(estate, settings).units)
        assertThrows(EstateUnavailable::class.java) { service.sell(UUID.randomUUID(), owner, estate.id, 49, settings) }
        assertThrows(EstateUnavailable::class.java) { service.sell(UUID.randomUUID(), other, estate.id, 50, settings) }
        val (sold, refund) = service.sell(UUID.randomUUID(), owner, estate.id, 50, settings)
        assertEquals(estate, sold); assertEquals(50, refund.units)
        assertEquals(1_000_000 - 100 + 50, balance(store))
        assertNull(service.at(world, 0, 0, 0)); assertNull(store.get(EstateDocuments.key(estate.id)))
        assertNotNull(buy(service, box(0, 0, 0, 1, 1, 1), "again", other))
    }

    @Test fun `trusted players are kept with unknown fields`() = withService { store, service ->
        val estate = buy(service, box(0, 0, 0, 1, 1, 1))
        val key = EstateDocuments.key(estate.id)
        store.atomic { it.put(key, it.get(key) + "future: kept\n") }
        service.refresh()
        assertTrue(service.setMember(owner, estate.id, other, true)); assertFalse(service.setMember(owner, estate.id, other, true))
        assertTrue(service.get(estate.id)!!.trusted(other)); assertEquals(listOf(estate.id), service.shared(other).map { it.id })
        assertEquals("kept", YamlFiles.parse(store.get(key)).getString("future"))
        assertThrows(EstateUnavailable::class.java) { service.setMember(other, estate.id, owner, true) }
        assertThrows(EstateUnavailable::class.java) { service.setMember(owner, estate.id, owner, true) }
        assertTrue(service.setMember(owner, estate.id, other, false)); assertFalse(service.get(estate.id)!!.trusted(other))
    }

    @Test fun `an administrator removes with or without the refund and the reason is in the ledger`() = withService { store, service ->
        val first = buy(service, box(0, 0, 0, 4, 4, 0), "a")
        val second = buy(service, box(10, 0, 0, 14, 4, 0), "b")
        val request = UUID.randomUUID()
        assertEquals(0, service.remove(request, "CONSOLE", first.id, false, "방치된 땅", settings).second.units)
        assertEquals(1_000_000 - 200, balance(store))
        assertEquals("방치된 땅", YamlFiles.parse(store.entry(request)!!.receipt).getString("reason"))
        assertEquals(50, service.remove(UUID.randomUUID(), "CONSOLE", second.id, true, "요청", settings).second.units)
        assertEquals(1_000_000 - 150, balance(store)); assertTrue(service.owned(owner).isEmpty())
    }

    @Test fun `a damaged record is kept, blocks its owner and new purchases, and can be discarded with a reason`() = withService { store, service ->
        val good = buy(service, box(0, 0, 0, 1, 1, 1), "good", other)
        val broken = UUID.randomUUID()
        val payload = "schemaVersion: 1\nid: $broken\nowner: $owner\nname: x\nminX: nope\n"
        store.atomic { it.put(EstateDocuments.key(broken), payload) }
        service.refresh()
        assertEquals(mapOf(EstateDocuments.key(broken) to payload), service.damaged())
        assertEquals(setOf(owner), service.damagedOwners())
        assertEquals(good, service.at(world, 0, 0, 0)) // the rest is still protected
        assertThrows(EstateUnavailable::class.java) { service.quote(other, box(5, 5, 5, 5, 5, 5), "world", settings) }
        assertThrows(EstateUnavailable::class.java) { service.requireUsable(owner) }
        val review = com.github.lupi13.limpi.recovery.PendingReviews.list(store).single { it.kind == "estate" }
        assertEquals(owner, review.owner); assertEquals(broken.toString(), review.id)
        assertTrue(review.command.startsWith("/limpi estate discard $broken"))
        assertThrows(EstateUnavailable::class.java) { service.discard(UUID.randomUUID(), "admin", good.id, "정상") }
        val request = UUID.randomUUID()
        service.discard(request, "admin", broken, "백업 확인")
        assertNull(store.get(EstateDocuments.key(broken))); assertTrue(service.damaged().isEmpty())
        assertEquals(payload, YamlFiles.parse(store.entry(request)!!.receipt).getString("payload"))
        assertNotNull(service.quote(owner, box(5, 5, 5, 5, 5, 5), "world", settings))
    }

    @Test fun `settings are read strictly`() {
        val config = org.bukkit.configuration.file.YamlConfiguration().apply {
            set("EstatePricePerBlock", 4); set("EstateRefundPercent", 50); set("EstateMaxWidth", 128); set("EstateMaxHeight", 0)
            set("EstateMaxCount", 10); set("EstateWorlds", emptyList<String>())
        }
        assertEquals(EstateSettings.DEFAULT, EstateSettings.read(config))
        config.set("EstateWorlds", listOf("world")); assertEquals(setOf("world"), EstateSettings.read(config).worlds)
        config.set("EstatePricePerBlock", 1.5); assertThrows(IllegalArgumentException::class.java) { EstateSettings.read(config) }
        config.set("EstatePricePerBlock", 4); config.set("EstateRefundPercent", 101)
        assertThrows(IllegalArgumentException::class.java) { EstateSettings.read(config) }
        config.set("EstateRefundPercent", 50); config.set("EstateMaxWidth", 5000)
        assertThrows(IllegalArgumentException::class.java) { EstateSettings.read(config) }
    }
}
