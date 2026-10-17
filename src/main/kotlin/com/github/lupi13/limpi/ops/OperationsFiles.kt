package com.github.lupi13.limpi.ops

import com.github.lupi13.limpi.storage.SqliteStateStore
import org.sqlite.JDBC
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE

object OperationsFiles {
    /** Only publish a verified new file. An existing target, including a symlink, is never replaced. */
    private fun publish(target: Path, write: (Path) -> Unit) {
        val destination = target.toAbsolutePath().normalize()
        require(!Files.exists(destination, NOFOLLOW_LINKS)) { "Output already exists" }
        require(Files.isDirectory(destination.parent)) { "Output directory must already exist" }
        val temporary = Files.createTempFile(destination.parent, ".limpi-operations-", ".tmp")
        try {
            write(temporary)
            FileChannel.open(temporary, WRITE).use { it.force(true) }
            Files.move(temporary, destination) // No REPLACE_EXISTING and no ATOMIC_MOVE overwrite ambiguity.
        } finally {
            Files.deleteIfExists(temporary)
            Files.deleteIfExists(Path.of("$temporary-wal"))
            Files.deleteIfExists(Path.of("$temporary-shm"))
            Files.deleteIfExists(Path.of("$temporary-journal"))
        }
    }
    fun export(snapshot: DatabaseSnapshot, target: Path) = publish(target) { Files.writeString(it, snapshot.export()) }
    fun backup(source: Path, target: Path) {
        ReadOnlyDatabase(source).use { database ->
            val expected = database.fingerprint()
            publish(target) { temporary ->
                database.backupTo(temporary)
                ReadOnlyDatabase(temporary).use { require(it.fingerprint() == expected) { "Backup verification failed" } }
            }
        }
    }
    fun restore(snapshot: DatabaseSnapshot, target: Path) {
        SnapshotCheck.inspect(snapshot).requireValid()
        publish(target) { temporary ->
            SqliteStateStore(temporary).use { store -> store.atomic { tx -> snapshot.documents.forEach { (key, value) -> tx.put(key, value) } } }
            JDBC.createConnection("jdbc:sqlite:$temporary", java.util.Properties()).use { connection ->
                connection.autoCommit = false
                connection.prepareStatement("INSERT INTO transactions(id,fingerprint,receipt,changed_keys,created_at) VALUES(?,?,?,?,?)").use { statement ->
                    snapshot.transactions.forEach { entry ->
                        statement.setString(1, entry.id.toString()); statement.setString(2, entry.fingerprint)
                        statement.setString(3, entry.receipt); statement.setString(4, entry.changedKeys.sorted().joinToString("\n"))
                        statement.setLong(5, entry.createdAt); statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
                connection.autoCommit = true
                connection.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
            }
            ReadOnlyDatabase(temporary).use { require(it.snapshot().fingerprint() == snapshot.fingerprint()) { "Restore verification failed" } }
        }
    }
}
