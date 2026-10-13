package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class TransitionTerms(val previous: String, val next: String, val previousSlot: Int?,
                           val previousItem: String?, val nextSlot: Int?, val nextItem: StoredStack?) {
    init {
        require(previousSlot == null || previousSlot in 0..36)
        require(nextSlot == null || nextSlot in 0..36)
        require(previousItem == null || (previousSlot != null && previousItem.isNotBlank()))
        require((nextSlot == null) == (nextItem == null))
    }
    fun plan(before: List<StoredStack?>) = AbilityInventoryPlan.plan(before, previousSlot, previousItem, nextSlot, nextItem)
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("previous", previous); set("next", next)
        set("previousSlot", previousSlot); set("previousItem", previousItem); set("nextSlot", nextSlot)
        set("nextItem", InventoryImages.encode(listOf(nextItem)))
    }.saveToString()
    companion object {
        fun decode(payload: String): TransitionTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
            require(data.get("previous") is String && data.get("next") is String)
            require(data.get("previousItem") == null || data.get("previousItem") is String)
            fun slot(path: String): Int? {
                val value = data.get(path) ?: return null
                require(value is Int || value is Long); val result = (value as Number).toLong()
                require(result in 0..36); return result.toInt()
            }
            val items = InventoryImages.decode(requireNotNull(data.getList("nextItem"))); require(items.size == 1)
            return TransitionTerms(requireNotNull(data.getString("previous")), requireNotNull(data.getString("next")),
                slot("previousSlot"), data.getString("previousItem"), slot("nextSlot"), items.single())
        }
    }
}

enum class TransitionState { APPLYING, REVIEW, DONE }
data class TransitionRecord(val evidence: RemovalEvidence, val terms: TransitionTerms,
                            val state: TransitionState = TransitionState.APPLYING, val reason: String? = null)

/** Freeze slot relocation before any physical change; settle profile only after its saved checkpoint. */
class AbilityTransitions(private val store: StateStore, private val players: PlayerRepository) {
    fun pending(owner: UUID): List<TransitionRecord> = store.documents("ability_transition_pending/$owner/").map { (key, payload) ->
        decode(payload).also { check(it.evidence.owner == owner && key == pendingKey(owner, it.evidence.id)) }
    }
    fun find(owner: UUID, id: UUID): TransitionRecord? = (store.get(pendingKey(owner, id)) ?: store.get(doneKey(owner, id)))?.let {
        decode(it).also { record -> check(record.evidence.owner == owner && record.evidence.id == id) }
    }
    fun prepare(id: UUID, owner: UUID, terms: TransitionTerms, before: List<StoredStack?>): TransitionRecord {
        val after = terms.plan(before)
        val candidate = TransitionRecord(RemovalEvidence(id, owner, UUID.randomUUID(), before.toList(), after), terms)
        val fingerprint = BankService.fingerprint("ABILITY_TRANSITION", owner.toString(), terms.encode(),
            YamlConfiguration().apply { set("before", InventoryImages.encode(before)) }.saveToString())
        return decode(store.execute(id, fingerprint) { tx ->
            check(tx.documents("ability_transition_pending/$owner/").isEmpty() && tx.documents("removal_pending/$owner/").isEmpty() &&
                tx.documents("market_escrow_pending/$owner/").isEmpty() && tx.documents("chest_session/$owner/").isEmpty()) { "An unresolved inventory operation exists" }
            // Completed/PENDING deliveries have no reserved image; APPLYING/REVIEW must finish first.
            check(tx.documents("delivery_pending/$owner/").values.none { YamlFiles.parse(it).getString("state") in listOf("APPLYING", "REVIEW") })
            validateProfile(tx, candidate)
            tx.put(pendingKey(owner, id), encode(candidate)); encode(candidate)
        }.entry.receipt)
    }
    fun apply(record: TransitionRecord, inventory: DeliveryInventory) {
        check(record.state == TransitionState.APPLYING)
        val e = record.evidence; val current = store.get(pendingKey(e.owner, e.id))
        if (current == null) { check(find(e.owner, e.id)?.state == TransitionState.DONE); return }
        check(decode(current) == record)
        PhysicalRemoval.apply(e, inventory, { reason -> review(record, reason) }, { complete(record) })
    }
    fun recover(owner: UUID, inventory: DeliveryInventory, freshLogin: Boolean) {
        val records = pending(owner); if (records.isEmpty()) return
        check(records.size == 1)
        val record = records.single()
        if (!freshLogin || record.state == TransitionState.REVIEW) throw DeliveryRecoveryRequired(owner, record.evidence.id)
        apply(record, inventory)
    }
    fun resolve(request: UUID, actor: String, owner: UUID, id: UUID, applied: Boolean, note: String) {
        require(actor.isNotBlank() && note.isNotBlank())
        invalidate(store.execute(request, BankService.fingerprint("ABILITY_TRANSITION_RESOLVE", actor, owner.toString(), id.toString(), applied.toString(), note)) { tx ->
            val record = decode(requireNotNull(tx.get(pendingKey(owner, id))))
            check(record.evidence.owner == owner && record.evidence.id == id)
            validateProfile(tx, record)
            if (applied) writeProfile(tx, record)
            tx.put(doneKey(owner, id), encode(record.copy(state = TransitionState.DONE, reason = note)))
            tx.put(pendingKey(owner, id), null)
            YamlConfiguration().apply {
                set("actor", actor); set("applied", applied); set("note", note); set("previousEvidence", encode(record))
            }.saveToString()
        })
    }
    private fun validateProfile(tx: StateTransaction, record: TransitionRecord) {
        require(Accounts.read(tx, record.evidence.owner).getString("ability").orEmpty() == record.terms.previous) { "Ability profile changed before transition settlement" }
        require(record.terms.plan(record.evidence.before) == record.evidence.after)
    }
    private fun writeProfile(tx: StateTransaction, record: TransitionRecord) {
        val owner = record.evidence.owner; val data = Accounts.read(tx, owner)
        data.set("ability", record.terms.next); tx.put(SqliteProfilePersistence.key(owner), data.saveToString())
    }
    private fun complete(record: TransitionRecord) {
        val e = record.evidence; val id = UUID.nameUUIDFromBytes("ability-transition-settle:${e.owner}:${e.id}".toByteArray(Charsets.UTF_8))
        invalidate(store.execute(id, "ability-transition-settle:${e.owner}:${e.id}") { tx ->
            check(decode(requireNotNull(tx.get(pendingKey(e.owner, e.id)))) == record)
            validateProfile(tx, record); writeProfile(tx, record)
            tx.put(doneKey(e.owner, e.id), encode(record.copy(state = TransitionState.DONE))); tx.put(pendingKey(e.owner, e.id), null)
            record.terms.encode()
        })
    }
    private fun invalidate(result: CommittedTransaction) = players.invalidate(result.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
    private fun review(record: TransitionRecord, reason: String) = store.atomic { tx ->
        val e = record.evidence; check(decode(requireNotNull(tx.get(pendingKey(e.owner, e.id)))) == record)
        tx.put(pendingKey(e.owner, e.id), encode(record.copy(state = TransitionState.REVIEW, reason = reason)))
    }
    private fun encode(record: TransitionRecord) = YamlConfiguration().apply {
        val e = record.evidence
        set("schemaVersion", 1); set("id", e.id.toString()); set("owner", e.owner.toString()); set("checkpoint", e.checkpoint.toString())
        set("before", InventoryImages.encode(e.before)); set("after", InventoryImages.encode(e.after))
        set("terms", record.terms.encode()); set("state", record.state.name); set("reason", record.reason)
    }.saveToString()
    private fun decode(payload: String): TransitionRecord {
        val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
        val before = InventoryImages.decode(requireNotNull(data.getList("before")))
        val after = InventoryImages.decode(requireNotNull(data.getList("after")))
        val terms = TransitionTerms.decode(requireNotNull(data.getString("terms"))); require(terms.plan(before) == after)
        return TransitionRecord(RemovalEvidence(UUID.fromString(data.getString("id")), UUID.fromString(data.getString("owner")),
            UUID.fromString(data.getString("checkpoint")), before, after), terms,
            TransitionState.valueOf(requireNotNull(data.getString("state"))), data.getString("reason"))
    }
    companion object {
        private fun pendingKey(owner: UUID, id: UUID) = "ability_transition_pending/$owner/$id"
        private fun doneKey(owner: UUID, id: UUID) = "ability_transition_done/$owner/$id"
    }
}
