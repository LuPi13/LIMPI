package com.github.lupi13.limpi.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.JDBC
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SqliteStateStoreTest {
    @TempDir lateinit var directory: Path
    private fun file() = directory.resolve("limpi.sqlite3")

    @Test fun `documents and durable receipt commit together and replay does not run the mutation`() {
        val id = UUID.randomUUID()
        SqliteStateStore(file()).use { store ->
            val result = store.execute(id, "request") {
                it.put("account/one", "한글 ' quoted value")
                it.put("account/two", "100")
                "receipt"
            }
            assertFalse(result.replayed)
            assertEquals(setOf("account/one", "account/two"), result.entry.changedKeys)
            val repeated = store.execute(id, "request") { fail("replay must not execute mutation") }
            assertTrue(repeated.replayed)
            assertEquals("receipt", repeated.entry.receipt)
        }
        SqliteStateStore(file()).use { reopened ->
            assertEquals("한글 ' quoted value", reopened.get("account/one"))
            assertNotNull(reopened.entry(id))
            assertTrue(reopened.execute(id, "request") { fail("restart must not repeat mutation") }.replayed)
        }
    }

    @Test fun `failure before commit rolls back every document and receipt`() {
        var fail = false
        val id = UUID.randomUUID()
        SqliteStateStore(file(), beforeCommit = { if (fail) throw IllegalStateException("commit fault") }).use { store ->
            store.atomic { it.put("account/one", "100"); it.put("account/two", "50") }
            fail = true
            assertThrows(IllegalStateException::class.java) {
                store.execute(id, "request") { it.put("account/one", "0"); it.put("account/two", "150"); "receipt" }
            }
            assertEquals("100", store.get("account/one"))
            assertEquals("50", store.get("account/two"))
            assertNull(store.entry(id))
        }
        SqliteStateStore(file()).use { reopened ->
            assertEquals("100", reopened.get("account/one"))
            assertNull(reopened.entry(id))
        }
    }

    @Test fun `mutation exception cannot leave half a transfer`() {
        SqliteStateStore(file()).use { store ->
            store.atomic { it.put("account/one", "100") }
            assertThrows(IllegalArgumentException::class.java) {
                store.execute(UUID.randomUUID(), "request") {
                    it.put("account/one", "0")
                    throw IllegalArgumentException("recipient validation failed")
                }
            }
            assertEquals("100", store.get("account/one"))
        }
    }

    @Test fun `one transaction ID cannot be reused for a changed request`() {
        val id = UUID.randomUUID()
        SqliteStateStore(file()).use { store ->
            store.execute(id, "first") { it.put("account/one", "100"); "receipt" }
            assertThrows(TransactionIdentityConflict::class.java) {
                store.execute(id, "second") { it.put("account/one", "0"); "different receipt" }
            }
            assertEquals("100", store.get("account/one"))
        }
    }

    @Test fun `nested or escaped transactions fail instead of writing outside a commit scope`() {
        SqliteStateStore(file()).use { store ->
            var escaped: StateTransaction? = null
            store.atomic { escaped = it }
            assertThrows(IllegalStateException::class.java) { escaped!!.put("account/one", "100") }
            assertThrows(IllegalStateException::class.java) { store.atomic { store.atomic {} } }
            assertNull(store.get("account/one"))
        }
    }

    @Test fun `deletes and prefix queries preserve unrelated namespaces`() {
        SqliteStateStore(file()).use { store ->
            store.atomic { it.put("player/one", "first"); it.put("player/two", "second"); it.put("market/one", "third") }
            store.atomic { it.put("player/one", null) }
            assertEquals(mapOf("player/two" to "second"), store.documents("player/"))
            assertEquals("third", store.get("market/one"))
        }
    }

    @Test fun `two database connections cannot apply the same transaction twice`() {
        val id = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        SqliteStateStore(file(), beforeCommit = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }).use { first ->
            SqliteStateStore(file()).use { second ->
                try {
                    val one = workers.submit<CommittedTransaction> {
                        first.execute(id, "request") { it.put("account/one", "100"); "receipt" }
                    }
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    val two = workers.submit<CommittedTransaction> {
                        second.execute(id, "request") { fail("duplicate connection must not run mutation") }
                    }
                    release.countDown()
                    assertFalse(one.get(5, TimeUnit.SECONDS).replayed)
                    assertTrue(two.get(5, TimeUnit.SECONDS).replayed)
                    assertEquals("100", second.get("account/one"))
                } finally { release.countDown(); workers.shutdownNow() }
            }
        }
    }

    @Test fun `future database schema is not downgraded`() {
        JDBC.createConnection("jdbc:sqlite:${file()}", Properties()).use { connection ->
            connection.createStatement().use { it.execute("PRAGMA user_version=2") }
        }
        assertThrows(IllegalArgumentException::class.java) { SqliteStateStore(file()) }
        JDBC.createConnection("jdbc:sqlite:${file()}", Properties()).use { connection ->
            connection.createStatement().use { it.executeQuery("PRAGMA user_version").use { row ->
                assertTrue(row.next()); assertEquals(2, row.getInt(1))
            } }
        }
    }

    @Test fun `unidentified existing database is not overwritten`() {
        JDBC.createConnection("jdbc:sqlite:${file()}", Properties()).use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE unrelated(value TEXT)") }
        }
        assertThrows(IllegalStateException::class.java) { SqliteStateStore(file()) }
    }

    @Test fun `prune removes old finished records and ledger rows but keeps live state and once-only identities`() {
        var now = 1_000L
        val clock = object : java.time.Clock() {
            override fun getZone() = java.time.ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = java.time.Instant.ofEpochMilli(now)
        }
        val old = UUID.randomUUID(); val initial = UUID.randomUUID(); val recent = UUID.randomUUID(); val touched = UUID.randomUUID()
        SqliteStateStore(file(), clock).use { store ->
            store.execute(old, "purchase") { it.put("delivery_done/a/1", "done"); it.put("player/a", "money: 1"); "r" }
            store.execute(initial, "initial-grant:a") { it.put("delivery_done/a/2", "done"); "r" }
            store.execute(touched, "settle") { it.put("removal_done/a/3", "done"); "r" }
            now = 10_000L
            store.execute(UUID.randomUUID(), "resolve") { it.put("removal_done/a/3", "resolved again"); "r" }
            store.execute(recent, "recent") { it.put("delivery_done/a/4", "done"); "r" }
            store.atomic { it.put("delivery_pending/a/5", "pending") }

            val result = store.prune(5_000L, setOf("delivery_done/", "removal_done/"), setOf("initial-"))
            assertNull(store.get("delivery_done/a/1")); assertNull(store.entry(old))
            assertNull(store.get("delivery_done/a/2")); assertNotNull(store.entry(initial))
            assertEquals("resolved again", store.get("removal_done/a/3"))
            assertEquals("done", store.get("delivery_done/a/4")); assertNotNull(store.entry(recent))
            assertEquals("money: 1", store.get("player/a")); assertEquals("pending", store.get("delivery_pending/a/5"))
            assertEquals(PruneResult(2, 2), result)
            assertFalse(store.execute(old, "purchase") { "again" }.replayed)
        }
    }
}
