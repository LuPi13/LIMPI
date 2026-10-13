package com.github.lupi13.limpi.delivery

import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class RemovalState { APPLYING, REVIEW, DONE }
data class RemovalRecord(val evidence: RemovalEvidence, val kind: String, val terms: String,
                         val state: RemovalState = RemovalState.APPLYING, val reason: String? = null)

/** Effects may only change DB state through this transaction, never live inventories or messages. */
interface RemovalEffect {
    fun validate(tx: StateTransaction, record: RemovalRecord)
    fun settle(tx: StateTransaction, record: RemovalRecord): String
}

class RemovalJournal(private val store: StateStore, private val players: PlayerRepository,
                     private val effects: Map<String, RemovalEffect>) {
    fun pending(owner: UUID): List<RemovalRecord> = store.documents("removal_pending/$owner/")
        .map { (key, payload) -> decode(payload).also { check(key == pendingKey(it.evidence.owner, it.evidence.id) && it.evidence.owner == owner) } }
    fun find(owner: UUID, id: UUID): RemovalRecord? = (store.get(pendingKey(owner, id)) ?: store.get(doneKey(owner, id)))
        ?.let { decode(it).also { record -> check(record.evidence.owner == owner && record.evidence.id == id) } }

    fun prepare(id: UUID, owner: UUID, kind: String, terms: String, before: List<StoredStack?>, after: List<StoredStack?>): RemovalRecord {
        PhysicalRemoval.validate(before, after)
        val effect = requireNotNull(effects[kind]) { "Unknown removal effect $kind" }
        val candidate = RemovalRecord(RemovalEvidence(id, owner, UUID.randomUUID(), before.toList(), after.toList()), kind, terms)
        val fingerprint = BankService.fingerprint("REMOVAL_PREPARE", owner.toString(), kind, terms,
            YamlConfiguration().apply { set("before", InventoryImages.encode(before)); set("after", InventoryImages.encode(after)) }.saveToString())
        return decode(store.execute(id, fingerprint) { tx ->
            check(tx.documents("removal_pending/$owner/").isEmpty() && tx.documents("market_escrow_pending/$owner/").isEmpty() && tx.documents("ability_transition_pending/$owner/").isEmpty() && tx.documents("chest_session/$owner/").isEmpty()) { "An unresolved inventory operation exists" }
            effect.validate(tx, candidate)
            tx.put(pendingKey(owner, id), encode(candidate)); encode(candidate)
        }.entry.receipt)
    }

    fun apply(record: RemovalRecord, inventory: DeliveryInventory) {
        check(record.state == RemovalState.APPLYING)
        val e = record.evidence
        val pending = store.get(pendingKey(e.owner, e.id))
        if (pending == null) { check(find(e.owner, e.id)?.state == RemovalState.DONE); return }
        check(decode(pending) == record)
        PhysicalRemoval.apply(e, inventory, { reason -> review(record, reason) }, { complete(record) })
    }

    fun recover(owner: UUID, inventory: DeliveryInventory, freshLogin: Boolean) {
        val records = pending(owner)
        if (records.isEmpty()) return
        check(records.size == 1) { "Multiple removals for $owner" }
        val record = records.single()
        if (!freshLogin || record.state == RemovalState.REVIEW) throw DeliveryRecoveryRequired(owner, record.evidence.id)
        apply(record, inventory)
    }

    fun resolve(request: UUID, actor: String, owner: UUID, id: UUID, removed: Boolean, note: String) {
        require(actor.isNotBlank() && note.isNotBlank())
        invalidate(store.execute(request, BankService.fingerprint("REMOVAL_RESOLVE", actor, owner.toString(), id.toString(), removed.toString(), note)) { tx ->
            val previous = decode(requireNotNull(tx.get(pendingKey(owner, id))))
            check(previous.evidence.owner == owner && previous.evidence.id == id)
            val settlement = if (removed) requireNotNull(effects[previous.kind]).settle(tx, previous) else "RETAINED: no settlement"
            tx.put(doneKey(owner, id), encode(previous.copy(state = RemovalState.DONE, reason = note)))
            tx.put(pendingKey(owner, id), null)
            YamlConfiguration().apply {
                set("actor", actor); set("removed", removed); set("note", note); set("previousEvidence", encode(previous)); set("settlement", settlement)
            }.saveToString()
        })
    }

    private fun complete(record: RemovalRecord) {
        val e = record.evidence
        val id = UUID.nameUUIDFromBytes("removal-settle:${e.owner}:${e.id}".toByteArray(Charsets.UTF_8))
        val committed = try {
            store.execute(id, "removal-settle:${e.owner}:${e.id}") { tx ->
                check(decode(requireNotNull(tx.get(pendingKey(e.owner, e.id)))) == record)
                val receipt = requireNotNull(effects[record.kind]).settle(tx, record)
                tx.put(doneKey(e.owner, e.id), encode(record.copy(state = RemovalState.DONE)))
                tx.put(pendingKey(e.owner, e.id), null); receipt
            }
        } catch (failure: com.github.lupi13.limpi.economy.BankException) {
            // Items are already physically removed; a deterministic account rule (e.g. balance overflow since prepare)
            // would fail on every retry, so hand it to an administrator instead of looping on each login.
            review(record, "Settlement rejected after physical removal: ${failure.reason}")
            throw failure
        }
        invalidate(committed)
    }
    private fun invalidate(result: CommittedTransaction) = players.invalidate(result.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
    private fun review(record: RemovalRecord, reason: String) = store.atomic { tx ->
        val e = record.evidence
        check(decode(requireNotNull(tx.get(pendingKey(e.owner, e.id)))) == record)
        tx.put(pendingKey(e.owner, e.id), encode(record.copy(state = RemovalState.REVIEW, reason = reason)))
    }
    private fun encode(record: RemovalRecord) = YamlConfiguration().apply {
        val e = record.evidence
        set("schemaVersion", 1); set("id", e.id.toString()); set("owner", e.owner.toString()); set("checkpoint", e.checkpoint.toString())
        set("before", InventoryImages.encode(e.before)); set("after", InventoryImages.encode(e.after))
        set("kind", record.kind); set("terms", record.terms); set("state", record.state.name); set("reason", record.reason)
    }.saveToString()
    private fun decode(payload: String): RemovalRecord {
        val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
        val before = InventoryImages.decode(requireNotNull(data.getList("before"))); val after = InventoryImages.decode(requireNotNull(data.getList("after")))
        PhysicalRemoval.validate(before, after)
        return RemovalRecord(RemovalEvidence(UUID.fromString(data.getString("id")), UUID.fromString(data.getString("owner")),
            UUID.fromString(data.getString("checkpoint")), before, after), requireNotNull(data.getString("kind")),
            requireNotNull(data.getString("terms")), RemovalState.valueOf(requireNotNull(data.getString("state"))), data.getString("reason"))
    }
    companion object {
        private fun pendingKey(owner: UUID, id: UUID) = "removal_pending/$owner/$id"
        private fun doneKey(owner: UUID, id: UUID) = "removal_done/$owner/$id"
    }
}
