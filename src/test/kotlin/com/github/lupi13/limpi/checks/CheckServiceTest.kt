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

    @Test fun `legacy approval mints one audited claim without a second issuer debit and fingerprints block copies`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val checks = service(store); val fingerprint = "a".repeat(64)
            checks.importLegacy(issue, "CONSOLE", fingerprint, recipient, issuer, 500, "confirmed old debit", ::item)
            assertEquals(5_000_000_000, money(store, issuer)); assertEquals(10, money(store, recipient))
            assertEquals(1, DeliveryJournal(store).pending(recipient).size)
            val receipt = YamlFiles.parse(store.entry(issue)!!.receipt)
            assertEquals("CONSOLE", receipt.getString("actor")); assertEquals("confirmed old debit", receipt.getString("note"))
            assertTrue(checks.importLegacy(issue, "CONSOLE", fingerprint, recipient, issuer, 500, "confirmed old debit") { _, _ -> error("no extra mint") }.replayed)
            assertEquals(CheckFailure.LEGACY_CONVERTED, assertThrows(CheckException::class.java) {
                checks.importLegacy(UUID.randomUUID(), "CONSOLE", fingerprint, other, issuer, 500, "another copy", ::item)
            }.reason)
            checks.redeem(UUID.randomUUID(), issue, recipient); assertEquals(510, money(store, recipient))
        }
    }

    @Test fun `failed legacy approval leaves neither fingerprint reservation nor check and can be retried`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit fault") }).use { store ->
            setup(store); val checks = service(store); val fingerprint = "b".repeat(64); fault = true
            assertThrows(IllegalStateException::class.java) { checks.importLegacy(issue, "operator", fingerprint, recipient, issuer, 500, "evidence", ::item) }
            assertNull(store.get("check_legacy/$fingerprint")); assertNull(checks.find(issue)); assertTrue(DeliveryJournal(store).pending(recipient).isEmpty())
            fault = false; checks.importLegacy(issue, "operator", fingerprint, recipient, issuer, 500, "evidence", ::item)
            assertEquals(issue.toString(), store.get("check_legacy/$fingerprint"))
        }
    }
}
