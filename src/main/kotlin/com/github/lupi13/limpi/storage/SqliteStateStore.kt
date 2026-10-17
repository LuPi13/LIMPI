package com.github.lupi13.limpi.storage

import org.sqlite.JDBC
import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.time.Clock
import java.util.UUID

class SqliteStateStore(
    path: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val beforeCommit: () -> Unit = {},
    private val transactionTiming: ((operation: String, elapsedNanos: Long) -> Unit)? = null,
) : StateStore {
    private val connection: Connection
    private var inTransaction = false

    init {
        val target = path.toAbsolutePath().normalize()
        Files.createDirectories(target.parent)
        val settings = SQLiteConfig().apply {
            setBusyTimeout(5000)
            setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
        }
        connection = JDBC.createConnection("jdbc:sqlite:$target", settings.toProperties())
        try {
            val version = pragmaInt("user_version")
            require(version in 0..1) { "Unsupported LIMPI database schema: $version" }
            val application = pragmaInt("application_id")
            require(application == 0 || application == APPLICATION_ID) { "This is not a LIMPI database" }
            if (application == 0) {
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table'").use {
                        check(it.next() && it.getInt(1) == 0) { "An unidentified database must not be overwritten" }
                    }
                }
            }
            connection.createStatement().use {
                it.execute("PRAGMA journal_mode=WAL")
                it.execute("PRAGMA synchronous=FULL")
                it.execute("PRAGMA foreign_keys=ON")
            }
            transaction(checkpoint = false) {
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE IF NOT EXISTS documents (key TEXT PRIMARY KEY, payload TEXT NOT NULL)")
                    statement.execute("""CREATE TABLE IF NOT EXISTS transactions (
                        id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, receipt TEXT NOT NULL,
                        changed_keys TEXT NOT NULL, created_at INTEGER NOT NULL)""")
                    statement.execute("PRAGMA application_id=$APPLICATION_ID")
                    statement.execute("PRAGMA user_version=1")
                }
            }
        } catch (failure: Exception) {
            connection.close()
            throw failure
        }
    }

    @Synchronized override fun get(key: String): String? { checkAvailable(); return read(key) }
    @Synchronized override fun documents(prefix: String): Map<String, String> { checkAvailable(); return list(prefix) }
    @Synchronized override fun entry(id: UUID): LedgerEntry? { checkAvailable(); return readEntry(id) }

    @Synchronized override fun atomic(action: (StateTransaction) -> Unit) {
        checkAvailable()
        measured("atomic") { transaction { tx -> action(tx) } }
    }

    @Synchronized override fun execute(id: UUID, fingerprint: String, action: (StateTransaction) -> String): CommittedTransaction {
        checkAvailable()
        require(fingerprint.isNotBlank())
        return measured("execute:$id") {
            try {
                transaction { tx ->
                    readEntry(id)?.let { existing ->
                        if (existing.fingerprint != fingerprint) throw TransactionIdentityConflict(id)
                        return@transaction CommittedTransaction(existing, true)
                    }
                    val receipt = action(tx)
                    val entry = LedgerEntry(id, fingerprint, receipt, tx.changed.toSet(), clock.millis())
                    connection.prepareStatement("INSERT INTO transactions(id,fingerprint,receipt,changed_keys,created_at) VALUES(?,?,?,?,?)").use {
                        it.setString(1, id.toString()); it.setString(2, fingerprint); it.setString(3, receipt)
                        it.setString(4, entry.changedKeys.joinToString("\n")); it.setLong(5, entry.createdAt)
                        it.executeUpdate()
                    }
                    CommittedTransaction(entry, false)
                }
            } catch (failure: Exception) {
                // If commit returned an ambiguous error, the durable receipt resolves whether it happened.
                val committed = runCatching { readEntry(id) }.getOrNull()
                if (committed != null && committed.fingerprint == fingerprint) return@measured CommittedTransaction(committed, true)
                throw failure
            }
        }
    }

    private inline fun <T> measured(operation: String, action: () -> T): T {
        val observer = transactionTiming ?: return action()
        val started = System.nanoTime()
        try { return action() }
        finally {
            // Diagnostics must never change settlement or obscure a storage failure.
            runCatching { observer(operation, System.nanoTime() - started) }
        }
    }

    @Synchronized override fun prune(cutoffMillis: Long, documentPrefixes: Set<String>, keepFingerprintPrefixes: Set<String>,
                                     retain: (key: String, payload: String, get: (String) -> String?) -> Boolean): PruneResult {
        checkAvailable()
        require(documentPrefixes.all { it.endsWith("/") && it.length > 1 })
        val result = transaction { _ ->
            val recent = mutableSetOf<String>()
            connection.prepareStatement("SELECT changed_keys FROM transactions WHERE created_at>=?").use {
                it.setLong(1, cutoffMillis)
                it.executeQuery().use { rows -> while (rows.next()) recent += rows.getString(1).split('\n').filter(String::isNotEmpty) }
            }
            val oldKeys = mutableSetOf<String>(); val oldIds = mutableListOf<String>()
            connection.prepareStatement("SELECT id,fingerprint,changed_keys FROM transactions WHERE created_at<?").use {
                it.setLong(1, cutoffMillis)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        oldKeys += rows.getString(3).split('\n').filter(String::isNotEmpty)
                        if (keepFingerprintPrefixes.none { prefix -> rows.getString(2).startsWith(prefix) }) oldIds += rows.getString(1)
                    }
                }
            }
            var documents = 0
            connection.prepareStatement("SELECT payload FROM documents WHERE key=?").use { select ->
                fun read(key: String): String? {
                    select.setString(1, key)
                    return select.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                }
                connection.prepareStatement("DELETE FROM documents WHERE key=?").use { delete ->
                    for (key in oldKeys) {
                        if (key in recent || documentPrefixes.none { key.startsWith(it) }) continue
                        val payload = read(key) ?: continue
                        if (retain(key, payload, ::read)) continue
                        delete.setString(1, key); documents += delete.executeUpdate()
                    }
                }
            }
            connection.prepareStatement("DELETE FROM transactions WHERE id=?").use { delete ->
                for (id in oldIds) { delete.setString(1, id); delete.executeUpdate() }
            }
            PruneResult(documents, oldIds.size)
        }
        // Return the freed pages to the file system so the database actually shrinks.
        if (result.documents + result.transactions > 0) connection.createStatement().use { it.execute("VACUUM") }
        return result
    }

    @Synchronized override fun close() {
        check(!inTransaction) { "Cannot close a store during a transaction" }
        connection.close()
    }

    private fun <T> transaction(checkpoint: Boolean = true, action: (Tx) -> T): T {
        check(!inTransaction) { "Nested transactions are not allowed" }
        inTransaction = true
        val tx = Tx(Thread.currentThread())
        var ended = false
        try {
            connection.autoCommit = false
            val result = action(tx)
            if (checkpoint) beforeCommit()
            connection.commit()
            ended = true
            return result
        } catch (failure: Exception) {
            try {
                connection.rollback()
                ended = true
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        } finally {
            tx.active = false
            inTransaction = false
            if (ended && !connection.isClosed) connection.autoCommit = true
            else runCatching { connection.close() }
        }
    }

    private inner class Tx(private val owner: Thread) : StateTransaction {
        var active = true
        val changed = linkedSetOf<String>()
        private fun checkScope() { check(active && Thread.currentThread() === owner) { "Transaction escaped its scope" } }
        override fun get(key: String): String? { checkScope(); return read(key) }
        override fun documents(prefix: String): Map<String, String> { checkScope(); return list(prefix) }
        override fun put(key: String, payload: String?) {
            checkScope(); validateKey(key)
            val sql = if (payload == null) "DELETE FROM documents WHERE key=?" else
                "INSERT INTO documents(key,payload) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET payload=excluded.payload"
            connection.prepareStatement(sql).use {
                it.setString(1, key)
                if (payload != null) it.setString(2, payload)
                it.executeUpdate()
            }
            changed.add(key)
        }
    }

    private fun read(key: String): String? {
        validateKey(key)
        connection.prepareStatement("SELECT payload FROM documents WHERE key=?").use {
            it.setString(1, key)
            it.executeQuery().use { rows -> return if (rows.next()) rows.getString(1) else null }
        }
    }

    private fun list(prefix: String): Map<String, String> {
        validateKey(prefix)
        connection.prepareStatement(LIST_SQL).use {
            it.setString(1, prefix); it.setString(2, upperBound(prefix))
            it.executeQuery().use { rows ->
                val result = linkedMapOf<String, String>()
                while (rows.next()) result[rows.getString(1)] = rows.getString(2)
                return result
            }
        }
    }

    private fun readEntry(id: UUID): LedgerEntry? {
        connection.prepareStatement("SELECT fingerprint,receipt,changed_keys,created_at FROM transactions WHERE id=?").use {
            it.setString(1, id.toString())
            it.executeQuery().use { row ->
                return if (row.next()) LedgerEntry(id, row.getString(1), row.getString(2),
                    row.getString(3).split('\n').filter(String::isNotEmpty).toSet(), row.getLong(4)) else null
            }
        }
    }

    private fun pragmaInt(name: String): Int = connection.createStatement().use {
        it.executeQuery("PRAGMA $name").use { rows -> check(rows.next()); rows.getInt(1) }
    }
    private fun checkAvailable() { check(!connection.isClosed && !inTransaction) { "Store is closed or inside another transaction" } }
    private fun validateKey(key: String) { require(key.matches(Regex("[A-Za-z0-9_/-]+"))) { "Invalid state key" } }

    companion object {
        private const val APPLICATION_ID = 0x4C494D50

        /**
         * Prefix listing as a primary-key range, so SQLite searches the key index instead of scanning every document
         * (the old `substr(key,…)=?` read the whole table on each call). Keys only use [A-Za-z0-9_/-], all below DEL,
         * so `prefix <= key < prefix + DEL` holds exactly the keys that start with the prefix.
         */
        internal const val LIST_SQL = "SELECT key,payload FROM documents WHERE key>=? AND key<? ORDER BY key"
        internal fun upperBound(prefix: String) = prefix + '\u007F'
    }
}
