package com.github.lupi13.limpi.storage

import java.util.UUID

interface StateTransaction {
    fun get(key: String): String?
    fun put(key: String, payload: String?)
    fun documents(prefix: String): Map<String, String>
}

data class LedgerEntry(
    val id: UUID,
    val fingerprint: String,
    val receipt: String,
    val changedKeys: Set<String>,
    val createdAt: Long,
)
data class CommittedTransaction(val entry: LedgerEntry, val replayed: Boolean)

data class PruneResult(val documents: Int, val transactions: Int)

class TransactionIdentityConflict(id: UUID) :
    IllegalArgumentException("Transaction $id was already used for another request")

/** Serialized payloads preserve unknown fields; related documents and their receipt commit together. */
interface StateStore : AutoCloseable {
    fun get(key: String): String?
    fun documents(prefix: String): Map<String, String>
    fun atomic(action: (StateTransaction) -> Unit)
    fun execute(id: UUID, fingerprint: String, action: (StateTransaction) -> String): CommittedTransaction
    fun entry(id: UUID): LedgerEntry?
    /**
     * Delete finished-record documents under [documentPrefixes] and ledger rows last written before [cutoffMillis].
     * A document is only removed when no newer ledger row touched it; rows whose fingerprint starts with one of
     * [keepFingerprintPrefixes] (once-only identities) are kept.
     */
    fun prune(cutoffMillis: Long, documentPrefixes: Set<String>, keepFingerprintPrefixes: Set<String>): PruneResult
}
