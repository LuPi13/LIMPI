package com.github.lupi13.limpi.ops

import com.github.lupi13.limpi.storage.LedgerEntry
import org.sqlite.JDBC
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteConnection
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.UUID

/** Opening an inspection must never create, initialize, migrate or repair a source database. */
class ReadOnlyDatabase(path: Path) : AutoCloseable {
    private val connection: Connection
    init {
        val source = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(source)) { "Existing database required: $source" }
        val config = SQLiteConfig().apply {
            setReadOnly(true); setBusyTimeout(5000); setTransactionMode(SQLiteConfig.TransactionMode.DEFERRED)
        }
        connection = JDBC.createConnection("jdbc:sqlite:$source", config.toProperties())
        try {
            connection.autoCommit = false
            require(pragma("application_id") == APPLICATION_ID) { "Not a LIMPI database" }
            require(pragma("user_version") == 1) { "Unsupported database version" }
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rows ->
                    check(rows.next() && rows.getString(1) == "ok" && !rows.next()) { "SQLite integrity check failed" }
                }
            }
            val tables = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rows ->
                    buildSet { while (rows.next()) add(rows.getString(1)) }
                }
            }
            require(tables == setOf("documents", "transactions")) { "Unexpected database tables: $tables" }
        } catch (failure: Exception) { connection.close(); throw failure }
    }
    fun snapshot(): DatabaseSnapshot {
        val documents = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT key,payload FROM documents ORDER BY key").use { rows ->
                buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
            }
        }
        val transactions = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id,fingerprint,receipt,changed_keys,created_at FROM transactions ORDER BY id").use { rows ->
                buildList { while (rows.next()) add(LedgerEntry(UUID.fromString(rows.getString(1)), rows.getString(2), rows.getString(3),
                    rows.getString(4).split('\n').filter(String::isNotEmpty).toSet(), rows.getLong(5))) }
            }
        }
        return DatabaseSnapshot(documents, transactions)
    }
    internal fun backupTo(emptyTarget: Path) {
        val result = (connection as SQLiteConnection).database.backup("main", emptyTarget.toString(), null, 100, 10, 500)
        check(result == 0) { "SQLite backup failed: $result" }
    }
    private fun pragma(name: String): Int = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA $name").use { rows -> check(rows.next()); rows.getInt(1) }
    }
    override fun close() = connection.close()
    companion object { const val APPLICATION_ID = 0x4C494D50 }
}
