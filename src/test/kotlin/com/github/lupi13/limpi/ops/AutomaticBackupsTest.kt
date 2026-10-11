package com.github.lupi13.limpi.ops

import com.github.lupi13.limpi.storage.SqliteStateStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AutomaticBackupsTest {
    @TempDir lateinit var root: Path
    private fun clock(second: Int) = Clock.fixed(Instant.parse("2026-10-11T01:00:${second.toString().padStart(2, '0')}Z"), ZoneOffset.UTC)

    @Test fun `live WAL backup preserves documents and receipts and keeps latest copies`() {
        val source = root.resolve("limpi.sqlite3")
        val folder = root.resolve("backups")
        SqliteStateStore(source).use { store ->
            for (second in 1..3) {
                val id = UUID.randomUUID()
                store.execute(id, "write-$second") { it.put("opaque/value", "unknown: $second"); "receipt-$second" }
                val target = AutomaticBackups(source, folder, 2, clock(second)).run()
                ReadOnlyDatabase(target).use { copy ->
                    assertEquals("unknown: $second", copy.snapshot().documents["opaque/value"])
                    assertTrue(copy.snapshot().transactions.any { it.id == id && it.receipt == "receipt-$second" })
                }
            }
            Files.list(folder).use { files ->
                assertEquals(setOf("limpi-20261011-010002.sqlite3", "limpi-20261011-010003.sqlite3"),
                    files.map { it.fileName.toString() }.filter { it.endsWith(".sqlite3") }.toList().toSet())
            }
        }
    }

    @Test fun `failed backup leaves old backups and unrelated files intact`() {
        val folder = Files.createDirectory(root.resolve("backups"))
        val old = folder.resolve("limpi-20261010-010001.sqlite3")
        Files.writeString(old, "old-backup")
        val other = folder.resolve("manual.sqlite3")
        Files.writeString(other, "manual")
        assertThrows(Exception::class.java) { AutomaticBackups(root.resolve("missing.sqlite3"), folder, 1, clock(1)).run() }
        assertEquals("old-backup", Files.readString(old))
        assertEquals("manual", Files.readString(other))
        assertFalse(Files.exists(root.resolve("missing.sqlite3")))
    }

    @Test fun `same second cannot overwrite published backup`() {
        val source = root.resolve("limpi.sqlite3")
        SqliteStateStore(source).use { store ->
            store.atomic { it.put("opaque/value", "first") }
            val backups = AutomaticBackups(source, root.resolve("backups"), 1, clock(1))
            val target = backups.run()
            store.atomic { it.put("opaque/value", "second") }
            assertThrows(Exception::class.java) { backups.run() }
            ReadOnlyDatabase(target).use { assertEquals("first", it.snapshot().documents["opaque/value"]) }
        }
    }
    @Test fun `backup sees committed state while writer has an uncommitted transaction`() {
        val source = root.resolve("limpi.sqlite3")
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            SqliteStateStore(source).use { store ->
                store.atomic { it.put("opaque/value", "committed") }
                val write = executor.submit {
                    store.atomic { tx ->
                        tx.put("opaque/value", "next")
                        entered.countDown()
                        check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    }
                }
                try {
                    assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    val target = AutomaticBackups(source, root.resolve("backups"), 1, clock(1)).run()
                    ReadOnlyDatabase(target).use { assertEquals("committed", it.snapshot().documents["opaque/value"]) }
                } finally { release.countDown() }
                write.get(5, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals("next", store.get("opaque/value"))
            }
        } finally { release.countDown(); executor.shutdownNow() }
    }
}
