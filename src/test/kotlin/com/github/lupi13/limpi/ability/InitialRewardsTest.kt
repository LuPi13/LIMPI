package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class InitialRewardsTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val bundle = listOf(ItemGrant("gacha", 1000, 64), ItemGrant("epic", 1, 64))
    private fun service(store: StateStore) = InitialRewards(store, DeliveryJournal(store), CachedPlayerRepository(SqliteProfilePersistence(store)))
    @Test fun `new profile and frozen eligibility register together and repeated login grants only once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            service(store).register(owner, "name", Money(123), bundle)
            assertEquals(123, YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money"))
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            service(store).register(owner, "renamed", Money(999), listOf(ItemGrant("changed config", 9, 64)))
            service(store).claim(owner); service(store).claim(owner)
            assertEquals(bundle, DeliveryJournal(store).pending(owner).single().items)
            assertEquals(123, YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money"))
        }
    }
    @Test fun `existing profile is exempt with its balance unlock and unknown fields intact`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val payload = "money: 777\nunlockedAbilities: [existing_code]\nunknown: keep\n"
            store.atomic { it.put(SqliteProfilePersistence.key(owner), payload) }
            service(store).register(owner, "name", Money(123), bundle)
            assertFalse(service(store).claim(owner)); assertEquals(payload, store.get(SqliteProfilePersistence.key(owner)))
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
        }
    }
    @Test fun `register failure creates neither profile nor an eligibility marker`() {
        var fault = true
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("register fault") }).use { store ->
            assertThrows(IllegalStateException::class.java) { service(store).register(owner, "name", Money(123), bundle) }
            assertNull(store.get(SqliteProfilePersistence.key(owner))); assertNull(store.get("initial_rewards/$owner"))
            fault = false; service(store).register(owner, "name", Money(123), bundle); service(store).claim(owner)
            assertEquals(bundle, DeliveryJournal(store).pending(owner).single().items)
        }
    }
    @Test fun `grant fault retains frozen entitlement with no partial delivery then retries once`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("grant fault") }).use { store ->
            service(store).register(owner, "name", Money(123), bundle); fault = true
            assertThrows(IllegalStateException::class.java) { service(store).claim(owner) }
            assertEquals("WAITING", YamlFiles.parse(store.get("initial_rewards/$owner")).getString("state"))
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
            fault = false; service(store).claim(owner); service(store).claim(owner)
            assertEquals(1, DeliveryJournal(store).pending(owner).size)
        }
    }
    @Test fun `zero initial counts record completion without creating an empty delivery`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            service(store).register(owner, "name", Money(0), emptyList()); service(store).claim(owner)
            assertEquals("GRANTED", YamlFiles.parse(store.get("initial_rewards/$owner")).getString("state"))
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
            service(store).register(owner, "name", Money(0), bundle); service(store).claim(owner)
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
        }
    }
    @Test fun `initial counts preserve zero and Long without accepting decimals or negatives`() {
        val data = YamlFiles.parse("InitialTicket: 0\nInitialCOMMON: 2147483648\nInitialRARE: 1\nInitialEPIC: 1\n")
        assertEquals(InitialTicketCounts(0, 2147483648, 1, 1), InitialTicketCounts.read(data))
        for (raw in listOf(-1, 1.5, "3")) {
            data.set("InitialEPIC", raw)
            assertThrows(IllegalArgumentException::class.java) { InitialTicketCounts.read(data) }
        }
    }
}
