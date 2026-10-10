package com.github.lupi13.limpi.ops

import com.github.lupi13.limpi.storage.SqliteStateStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.JDBC
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

class OperationsTest {
    @TempDir lateinit var root: Path
    private val owner = UUID.randomUUID()
    private fun profile(money: String = "5000000000") = "schemaVersion: 1\nname: 사용자\nmoney: $money\nunknown:\n  nested: [a, b]\n"
    private fun fixture(): Path = root.resolve("source.sqlite3").also { path ->
        SqliteStateStore(path).use { store ->
            store.execute(UUID.randomUUID(), "stable-request") {
                it.put("player/$owner", profile()); it.put("extension/opaque", "custom: '한글 : value'\n")
                "exact receipt\nwith newline"
            }
        }
    }
    private fun snapshot(path: Path) = ReadOnlyDatabase(path).use { it.snapshot() }
    private fun cli(vararg args: String): Int = OperationsCli.run(arrayOf(*args), {}, {})

    @Test fun `export and restore preserve exact raw payloads ledger and replay identities`() {
        val source = fixture(); val before = snapshot(source)
        val exported = DatabaseSnapshot.import(before.export())
        assertEquals(before, exported)
        val copy = root.resolve("restored.sqlite3")
        OperationsFiles.restore(exported, copy)
        assertEquals(before.fingerprint(), snapshot(copy).fingerprint())
        val entry = before.transactions.single()
        SqliteStateStore(copy).use { store ->
            assertEquals(entry, store.entry(entry.id))
            assertTrue(store.execute(entry.id, entry.fingerprint) { fail("Restored receipt must prevent duplicate settlement") }.replayed)
        }
        assertEquals(before, snapshot(source))
    }
    @Test fun `backup includes committed WAL while writer stays open`() {
        val source = root.resolve("wal.sqlite3")
        SqliteStateStore(source).use { store ->
            store.execute(UUID.randomUUID(), "live-write") { it.put("player/$owner", profile()); "DONE" }
            assertTrue(Files.size(Path.of("$source-wal")) > 0)
            val expected = snapshot(source)
            val backup = root.resolve("backup.sqlite3")
            OperationsFiles.backup(source, backup)
            assertEquals(expected.fingerprint(), snapshot(backup).fingerprint())
            store.atomic { it.put("extension/after_backup", "next") }
            assertNull(snapshot(backup).documents["extension/after_backup"])
        }
    }
    @Test fun `existing targets and source itself are never overwritten`() {
        val source = fixture(); val original = Files.readAllBytes(source)
        val target = root.resolve("existing"); Files.writeString(target, "keep")
        assertThrows(Exception::class.java) { OperationsFiles.backup(source, source) }
        assertThrows(Exception::class.java) { OperationsFiles.export(snapshot(source), target) }
        assertThrows(Exception::class.java) { OperationsFiles.restore(snapshot(source), target) }
        assertArrayEquals(original, Files.readAllBytes(source)); assertEquals("keep", Files.readString(target))
    }
    @Test fun `missing source cannot create an empty database`() {
        val missing = root.resolve("missing.sqlite3")
        assertEquals(2, cli("check", missing.toString()))
        assertFalse(Files.exists(missing))
    }
    @Test fun `foreign and future databases are not initialized or migrated`() {
        val source = fixture()
        JDBC.createConnection("jdbc:sqlite:$source", Properties()).use { c -> c.createStatement().use { it.execute("PRAGMA user_version=99") } }
        val bytes = Files.readAllBytes(source)
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyDatabase(source) }
        assertArrayEquals(bytes, Files.readAllBytes(source))
        val foreign = root.resolve("foreign.sqlite3")
        JDBC.createConnection("jdbc:sqlite:$foreign", Properties()).use { c -> c.createStatement().use { it.execute("CREATE TABLE other(x)") } }
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyDatabase(foreign) }
    }
    @Test fun `export checksum detects changed money without writing restored output`() {
        val exported = root.resolve("tampered.yml")
        Files.writeString(exported, snapshot(fixture()).export().replace("5000000000", "6000000000"))
        val target = root.resolve("restore.sqlite3")
        assertEquals(2, cli("restore-copy", exported.toString(), target.toString()))
        assertFalse(Files.exists(target))
    }
    @Test fun `dry run has no output or source mutations`() {
        val source = fixture(); val exported = root.resolve("export.yml")
        val sourceBytes = Files.readAllBytes(source)
        assertEquals(0, cli("export", source.toString(), exported.toString()))
        val bytes = Files.readAllBytes(exported)
        assertEquals(0, cli("restore-dry-run", exported.toString()))
        assertArrayEquals(bytes, Files.readAllBytes(exported))
        assertArrayEquals(sourceBytes, Files.readAllBytes(source))
        // SQLite may create empty -wal/-shm sidecars for read-only WAL access; they hold no committed frames.
        for (suffix in listOf("-wal", "-shm")) Path.of("$source$suffix").takeIf(Files::exists)?.let { sidecar ->
            if (suffix == "-wal") assertEquals(0L, Files.size(sidecar))
        }
        Files.list(root).use { files ->
            assertEquals(setOf("source.sqlite3", "export.yml"),
                files.map { p -> p.fileName.toString() }.filter { !it.endsWith("-wal") && !it.endsWith("-shm") }.toList().toSet())
        }
    }
    @Test fun `malformed profile can be backed up but not restored automatically`() {
        val source = root.resolve("bad.sqlite3")
        SqliteStateStore(source).use { it.atomic { tx -> tx.put("player/$owner", profile("3.5")) } }
        val export = root.resolve("bad.yml"); val backup = root.resolve("bad-backup.sqlite3")
        assertEquals(2, cli("check", source.toString()))
        assertEquals(2, cli("export", source.toString(), export.toString())); assertTrue(Files.exists(export))
        assertEquals(2, cli("backup", source.toString(), backup.toString())); assertTrue(Files.exists(backup))
        assertEquals(snapshot(source), snapshot(backup))
        assertEquals(2, cli("restore-copy", export.toString(), root.resolve("denied.sqlite3").toString()))
        assertFalse(Files.exists(root.resolve("denied.sqlite3")))
    }
    @Test fun `negative balance is reported and preserved not silently repaired`() {
        val data = DatabaseSnapshot(mapOf("player/$owner" to profile("-100")), emptyList())
        val report = SnapshotCheck.inspect(data)
        assertTrue(report.errors.isEmpty()); assertEquals("-100", report.balanceSum.toString())
        assertTrue(report.warnings.any { it.contains("negative") })
        OperationsFiles.restore(data, root.resolve("negative.sqlite3"))
        assertEquals(data, snapshot(root.resolve("negative.sqlite3")))
    }
    @Test fun `future document schema owner mismatch and missing market index are rejected`() {
        val id = UUID.randomUUID()
        val documents = mapOf("delivery_pending/$owner/$id" to "schemaVersion: 2\nowner: $owner\nid: $id\n",
            "removal_pending/$owner/$id" to "schemaVersion: 1\nowner: ${UUID.randomUUID()}\nid: $id\n",
            "market_code/ABCD" to id.toString())
        assertEquals(3, SnapshotCheck.inspect(DatabaseSnapshot(documents, emptyList())).errors.size)
    }
    @Test fun `safe YAML rejects duplicate fields object tags and duplicate exported documents`() {
        assertThrows(Exception::class.java) { OperationsYaml.map("money: 10\nmoney: 20\n") }
        assertThrows(Exception::class.java) { OperationsYaml.parse("!!java.net.URL ['https://example.com']") }
        val snapshot = snapshot(fixture())
        val data = OperationsYaml.map(snapshot.export()).toMutableMap()
        val docs = (data["documents"] as List<*>).toMutableList(); docs.add(docs.first()); data["documents"] = docs
        assertThrows(Exception::class.java) { DatabaseSnapshot.import(OperationsYaml.dump(data)) }
    }
    @Test fun `holding numeric coercion and negative values fail preflight`() {
        for (values in listOf("count: 1.5", "count: -1", "count: 1\n    costBasis: -5","count: 1\n    realizedProfit: bad")) {
            val data = "${profile()}stock:\n  ABCD:\n    $values\n"
            assertFalse(SnapshotCheck.inspect(DatabaseSnapshot(mapOf("player/$owner" to data), emptyList())).errors.isEmpty())
        }
    }
    @Test fun `CLI shape errors never write and help requires no server`() {
        assertEquals(0, cli("help")); assertEquals(2, cli("restore-copy", "missing"))
        assertEquals(2, cli("unknown", "missing")); assertEquals(2, cli("check"))
    }
}
