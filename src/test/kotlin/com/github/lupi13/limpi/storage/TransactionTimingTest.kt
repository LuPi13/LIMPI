package com.github.lupi13.limpi.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class TransactionTimingTest {
    @TempDir lateinit var root: Path
    @Test fun `timing observes successful replayed and failed calls without altering receipts`() {
        val timings = mutableListOf<Pair<String, Long>>()
        SqliteStateStore(root.resolve("state.sqlite3"), transactionTiming = { operation, nanos -> timings += operation to nanos }).use { store ->
            val id = UUID.randomUUID()
            store.atomic { it.put("opaque/value", "initial") }
            store.execute(id, "request") { it.put("opaque/value", "committed"); "receipt" }
            assertTrue(store.execute(id, "request") { fail("must not repeat") }.replayed)
            assertThrows(IllegalStateException::class.java) { store.atomic { it.put("opaque/value", "lost"); error("fault") } }
            assertEquals("committed", store.get("opaque/value"))
            assertEquals("receipt", store.entry(id)?.receipt)
            assertEquals(listOf("atomic", "execute:$id", "execute:$id", "atomic"), timings.map { it.first })
            assertTrue(timings.all { it.second >= 0 })
        }
    }
    @Test fun `broken diagnostics cannot turn a successful settlement into a failed one`() {
        SqliteStateStore(root.resolve("state.sqlite3"), transactionTiming = { _, _ -> error("logger failed") }).use { store ->
            val id = UUID.randomUUID()
            assertEquals("ok", store.execute(id, "request") { it.put("opaque/value", "keep"); "ok" }.entry.receipt)
            assertEquals("keep", store.get("opaque/value"))
        }
    }
}
