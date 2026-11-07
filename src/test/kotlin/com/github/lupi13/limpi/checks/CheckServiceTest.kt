package com.github.lupi13.limpi.checks

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

class CheckServiceTest {
    @TempDir lateinit var directory: Path
    private val issuer = UUID.randomUUID()
    private val recipient = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val issue = UUID.randomUUID()
    private fun service(store: StateStore) = CheckService(store, DeliveryJournal(store), CachedPlayerRepository(SqliteProfilePersistence(store)))
    private fun setup(store: StateStore, balance: Long = 5_000_000_000, receiving: Long = 10) = store.atomic { tx ->
        tx.put(SqliteProfilePersistence.key(issuer), "name: issuer\nmoney: $balance\ncustom: preserved\n")
        tx.put(SqliteProfilePersistence.key(recipient), "name: recipient\nmoney: $receiving\n")
        tx.put(SqliteProfilePersistence.key(other), "name: other\nmoney: 20\n")
    }
    private fun money(store: StateStore, owner: UUID) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private fun item(id: UUID, amount: Money) = ItemGrant("check:$id:${amount.units}", 1, 1)

    @Test fun `entire Long balance becomes one durable check claim and unrelated profile data survives`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val checks = service(store)
            checks.issue(issue, issuer, 5_000_000_000, ::item)
            assertEquals(0, money(store, issuer)); assertEquals(5_000_000_000, checks.find(issue)?.amount?.units)
            assertEquals(CheckState.ISSUED, checks.find(issue)?.state)
            assertEquals(listOf(item(issue, Money(5_000_000_000))), DeliveryJournal(store).pending(issuer).single().items)
            assertNotNull(store.entry(issue))
            assertEquals("preserved", YamlFiles.parse(store.get(SqliteProfilePersistence.key(issuer))).getString("custom"))
        }
    }

    @Test fun `death takes the percentage as a redeemable check without any delivery`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, balance = 1_001); val checks = service(store)
            val result = checks.forfeit(issue, issuer, 50)
            assertEquals(500, result.amount, "rounded down"); assertEquals(issue, result.check)
            assertEquals(501, money(store, issuer))
            assertEquals(CheckState.ISSUED, checks.find(issue)?.state); assertEquals(issuer, checks.find(issue)?.issuer)
            assertTrue(DeliveryJournal(store).pending(issuer).isEmpty(), "the item goes to the ground, not the claim box")
            assertEquals("preserved", YamlFiles.parse(store.get(SqliteProfilePersistence.key(issuer))).getString("custom"))
            checks.redeem(UUID.randomUUID(), issue, recipient)
            assertEquals(510, money(store, recipient), "whoever picks it up can pay it in")
        }
    }

    @Test fun `death with too little to take changes nothing and records no check`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, balance = 1); val checks = service(store)
            assertEquals(0, checks.forfeit(issue, issuer, 50).amount)
            assertEquals(1, money(store, issuer)); assertNull(checks.find(issue))
        }
    }

    @Test fun `the same death is settled once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, balance = 1_000); val checks = service(store)
            assertFalse(checks.forfeit(issue, issuer, 100).replayed)
            val again = checks.forfeit(issue, issuer, 100)
            assertTrue(again.replayed); assertEquals(1_000, again.amount); assertEquals(0, money(store, issuer))
        }
    }

    @Test fun `a death settlement that fails to commit takes nothing`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit fault") }).use { store ->
            setup(store, balance = 1_000); val checks = service(store); fault = true
            assertThrows(IllegalStateException::class.java) { checks.forfeit(issue, issuer, 50) }
            assertEquals(1_000, money(store, issuer)); assertNull(checks.find(issue))
        }
    }

    @Test fun `a damaged balance is refused and kept`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { tx -> tx.put(SqliteProfilePersistence.key(issuer), "name: issuer\nmoney: -5\n") }
            val checks = service(store)
            assertThrows(Exception::class.java) { checks.forfeit(issue, issuer, 50) }
            assertEquals(-5, money(store, issuer)); assertNull(checks.find(issue))
        }
    }

    @Test fun `the configured percentage is a whole number from 0 to 100`() {
        assertEquals(0, DeathLoss.percent(0)); assertEquals(50, DeathLoss.percent(50)); assertEquals(100, DeathLoss.percent(100))
        listOf(-1, 101, 12.5, "50", null).forEach { assertNull(DeathLoss.percent(it), "$it") }
    }

    @Test fun `the loss is rounded down and survives the largest balance`() {
        assertEquals(0, DeathLoss.amount(1, 50)); assertEquals(1, DeathLoss.amount(3, 50)); assertEquals(1_000, DeathLoss.amount(1_000, 100))
        assertEquals(Long.MAX_VALUE, DeathLoss.amount(Long.MAX_VALUE, 100))
        assertEquals(Long.MAX_VALUE / 2, DeathLoss.amount(Long.MAX_VALUE, 50))
    }

    @Test fun `issuance commit failure leaves no debit check delivery or receipt and retry succeeds`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit fault") }).use { store ->
            setup(store); val checks = service(store); fault = true
            assertThrows(IllegalStateException::class.java) { checks.issue(issue, issuer, 500, ::item) }
            assertEquals(5_000_000_000, money(store, issuer)); assertNull(checks.find(issue))
            assertTrue(DeliveryJournal(store).pending(issuer).isEmpty()); assertNull(store.entry(issue))
            fault = false; assertFalse(checks.issue(issue, issuer, 500, ::item).replayed)
        }
    }

    @Test fun `issuance replay after restart and redemption never mints another item or charges again`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); service(store).issue(issue, issuer, 500, ::item)
            service(store).redeem(UUID.randomUUID(), issue, recipient)
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            assertTrue(service(store).issue(issue, issuer, 500) { _, _ -> error("must not recreate item") }.replayed)
            assertEquals(4_999_999_500, money(store, issuer)); assertEquals(510, money(store, recipient))
            assertEquals(1, DeliveryJournal(store).pending(issuer).size)
            assertThrows(TransactionIdentityConflict::class.java) { service(store).issue(issue, issuer, 501, ::item) }
        }
    }

    @Test fun `zero negative insufficient and invalid item grants cannot debit or mint`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, 100); val checks = service(store)
            for (amount in listOf(0L, -1L)) assertThrows(IllegalArgumentException::class.java) { checks.issue(UUID.randomUUID(), issuer, amount, ::item) }
            assertEquals(BankFailure.INSUFFICIENT_FUNDS, assertThrows(BankException::class.java) { checks.issue(issue, issuer, 101, ::item) }.reason)
            assertThrows(IllegalArgumentException::class.java) { checks.issue(issue, issuer, 100) { _, _ -> ItemGrant("wrong stack", 2, 64) } }
            assertEquals(100, money(store, issuer)); assertNull(checks.find(issue)); assertTrue(DeliveryJournal(store).pending(issuer).isEmpty())
        }
    }

    @Test fun `redemption is paid once from stored value even across request replay and copied check IDs`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val checks = service(store); checks.issue(issue, issuer, 500, ::item)
            val request = UUID.randomUUID()
            assertEquals(500, checks.redeem(request, issue, recipient).amount)
            assertTrue(checks.redeem(request, issue, recipient).replayed)
            assertEquals(510, money(store, recipient)); assertEquals(recipient, checks.find(issue)?.redeemer)
            assertEquals(CheckFailure.REDEEMED, assertThrows(CheckException::class.java) { checks.redeem(UUID.randomUUID(), issue, other) }.reason)
            assertThrows(TransactionIdentityConflict::class.java) { checks.redeem(request, issue, other) }
            assertEquals(20, money(store, other))
        }
    }

    @Test fun `two independent DB connections cannot deposit copies of the same check twice`() {
        SqliteStateStore(directory.resolve("state.db")).use { first ->
            setup(first); service(first).issue(issue, issuer, 500, ::item)
            SqliteStateStore(directory.resolve("state.db")).use { second ->
                val workers = Executors.newFixedThreadPool(2)
                try {
                    val services = listOf(service(first), service(second)); val owners = listOf(recipient, other)
                    val results = services.indices.map { index -> workers.submit<Boolean> {
                        try { services[index].redeem(UUID.randomUUID(), issue, owners[index]); true }
                        catch (failure: CheckException) { assertEquals(CheckFailure.REDEEMED, failure.reason); false }
                    } }.map { it.get(10, TimeUnit.SECONDS) }
                    assertEquals(1, results.count { it }); assertEquals(530, money(first, recipient) + money(first, other))
                    assertEquals(CheckState.REDEEMED, service(first).find(issue)?.state)
                } finally { workers.shutdownNow() }
            }
        }
    }

    @Test fun `redemption commit failure preserves claim and account until retry`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit fault") }).use { store ->
            setup(store); val checks = service(store); checks.issue(issue, issuer, 500, ::item)
            val request = UUID.randomUUID(); fault = true
            assertThrows(IllegalStateException::class.java) { checks.redeem(request, issue, recipient) }
            assertEquals(10, money(store, recipient)); assertEquals(CheckState.ISSUED, checks.find(issue)?.state); assertNull(store.entry(request))
            fault = false; checks.redeem(request, issue, recipient); assertEquals(510, money(store, recipient))
        }
    }

    @Test fun `overflow and unknown identity never consume the claim or credit an account`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store, receiving = Long.MAX_VALUE); val checks = service(store); checks.issue(issue, issuer, 500, ::item)
            assertEquals(BankFailure.OVERFLOW, assertThrows(BankException::class.java) { checks.redeem(UUID.randomUUID(), issue, recipient) }.reason)
            assertEquals(CheckFailure.UNKNOWN, assertThrows(CheckException::class.java) { checks.redeem(UUID.randomUUID(), UUID.randomUUID(), other) }.reason)
            assertEquals(Long.MAX_VALUE, money(store, recipient)); assertEquals(20, money(store, other))
            assertEquals(CheckState.ISSUED, checks.find(issue)?.state)
        }
    }
}
