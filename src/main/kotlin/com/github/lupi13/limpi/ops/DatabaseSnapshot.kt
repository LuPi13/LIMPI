package com.github.lupi13.limpi.ops

import com.github.lupi13.limpi.storage.LedgerEntry
import java.security.MessageDigest
import java.util.UUID

data class DatabaseSnapshot(val documents: Map<String, String>, val transactions: List<LedgerEntry>) {
    init {
        require(documents.keys.all { it.matches(Regex("[A-Za-z0-9_/-]+")) })
        require(transactions.map { it.id }.toSet().size == transactions.size)
        require(transactions.all { it.fingerprint.isNotBlank() && it.changedKeys.all { key -> key.matches(Regex("[A-Za-z0-9_/-]+")) } })
    }
    /** Length-prefix every value so separators inside unknown user payloads cannot collide. */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(8).putLong(bytes.size.toLong()).array()); digest.update(bytes)
        }
        add("LIMPI_EXPORT_1"); add(documents.size.toString()); add(transactions.size.toString())
        documents.toSortedMap().forEach { (key, payload) -> add(key); add(payload) }
        transactions.sortedBy { it.id.toString() }.forEach { entry ->
            add(entry.id.toString()); add(entry.fingerprint); add(entry.receipt); add(entry.createdAt.toString())
            add(entry.changedKeys.size.toString()); entry.changedKeys.sorted().forEach(::add)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun export(): String = OperationsYaml.dump(linkedMapOf(
        "formatVersion" to 1, "applicationId" to ReadOnlyDatabase.APPLICATION_ID, "databaseVersion" to 1,
        "sha256" to fingerprint(),
        "documents" to documents.toSortedMap().map { (key, payload) -> linkedMapOf("key" to key, "payload" to payload) },
        "transactions" to transactions.sortedBy { it.id.toString() }.map { entry -> linkedMapOf(
            "id" to entry.id.toString(), "fingerprint" to entry.fingerprint, "receipt" to entry.receipt,
            "changedKeys" to entry.changedKeys.sorted(), "createdAt" to entry.createdAt) }
    ))
    companion object {
        fun import(text: String): DatabaseSnapshot {
            val data = OperationsYaml.map(text)
            require(OperationsYaml.integer(data["formatVersion"], "formatVersion") == 1L)
            require(OperationsYaml.integer(data["applicationId"], "applicationId") == ReadOnlyDatabase.APPLICATION_ID.toLong())
            require(OperationsYaml.integer(data["databaseVersion"], "databaseVersion") == 1L)
            val documents = linkedMapOf<String, String>()
            for (raw in data["documents"] as? List<*> ?: error("Missing documents")) {
                val item = raw as? Map<*, *> ?: error("Invalid document")
                val key = item["key"] as? String ?: error("Missing key")
                val payload = item["payload"] as? String ?: error("Missing payload")
                require(documents.put(key, payload) == null) { "Duplicate document $key" }
            }
            val transactions = (data["transactions"] as? List<*> ?: error("Missing transactions")).map { raw ->
                val item = raw as? Map<*, *> ?: error("Invalid transaction")
                val keys = item["changedKeys"] as? List<*> ?: error("Missing changedKeys")
                require(keys.all { it is String } && keys.toSet().size == keys.size)
                LedgerEntry(UUID.fromString(item["id"] as? String), item["fingerprint"] as? String ?: error("Missing fingerprint"),
                    item["receipt"] as? String ?: error("Missing receipt"), keys.map { it as String }.toSet(), OperationsYaml.integer(item["createdAt"], "createdAt"))
            }
            return DatabaseSnapshot(documents, transactions).also { require(it.fingerprint() == data["sha256"]) { "Export fingerprint mismatch" } }
        }
    }
}
