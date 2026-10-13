package com.github.lupi13.limpi.delivery

import com.github.lupi13.limpi.storage.*
import com.github.lupi13.limpi.economy.BankService
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class DeliveryState { PENDING, APPLYING, DONE, REVIEW }
enum class DeliveryResolution { DELIVERED, RETRY }

data class DeliveryRecord(
    val id: UUID, val owner: UUID, val reason: String, val items: List<ItemGrant>,
    val state: DeliveryState = DeliveryState.PENDING,
    val before: List<StoredStack?>? = null, val after: List<StoredStack?>? = null,
    val reviewReason: String? = null,
    val checkpoint: UUID? = null, val slotLimit: Int = Int.MAX_VALUE,
) { val checkpointId: UUID get() = checkpoint ?: id }

/** Stage in the SAME transaction as the debit/listing/reward; never enqueue after that commit. */
class DeliveryJournal(private val store: StateStore) {
    fun stage(tx: StateTransaction, id: UUID, owner: UUID, reason: String, items: List<ItemGrant>) {
        require(reason.isNotBlank()); require(items.isNotEmpty())
        val record = DeliveryRecord(id, owner, reason, items.toList())
        val existing = tx.get(pendingKey(owner, id)) ?: tx.get(doneKey(owner, id))
        if (existing != null) {
            val previous = decode(existing)
            if (previous.id != id || previous.owner != owner || previous.reason != reason || previous.items != items) {
                throw TransactionIdentityConflict(id)
            }
            return
        }
        tx.put(pendingKey(owner, id), encode(record))
    }

    fun pending(owner: UUID): List<DeliveryRecord> = store.documents("delivery_pending/$owner/")
        .map { (key, payload) -> decode(payload).also { check(key == pendingKey(it.owner, it.id) && it.owner == owner) } }

    fun find(owner: UUID, id: UUID): DeliveryRecord? =
        (store.get(pendingKey(owner, id)) ?: store.get(doneKey(owner, id)))?.let(::decode)

    fun begin(record: DeliveryRecord, before: List<StoredStack?>, after: List<StoredStack?>, slotLimit: Int = Int.MAX_VALUE): DeliveryRecord {
        check(record.state == DeliveryState.PENDING)
        require(InventoryPlan.grant(before, record.items, slotLimit) == after) { "Invalid delivery plan" }
        val planned = record.copy(state = DeliveryState.APPLYING, before = before.toList(), after = after.toList(),
            checkpoint = UUID.randomUUID(), slotLimit = slotLimit)
        store.atomic { tx ->
            check(decode(requireNotNull(tx.get(pendingKey(record.owner, record.id)))) == record) { "Delivery changed while planning" }
            check(tx.documents("chest_session/${record.owner}/").isEmpty()) { "A chest window is still being recorded" }
            tx.put(pendingKey(record.owner, record.id), encode(planned))
        }
        return planned
    }

    fun complete(record: DeliveryRecord) {
        check(record.state == DeliveryState.APPLYING)
        val completionId = UUID.nameUUIDFromBytes("delivery-done:${record.owner}:${record.id}".toByteArray(Charsets.UTF_8))
        store.execute(completionId, "delivery-done:${record.owner}:${record.id}") { tx ->
            val current = decode(requireNotNull(tx.get(pendingKey(record.owner, record.id))))
            check(current == record) { "Delivery changed while completing" }
            tx.put(doneKey(record.owner, record.id), encode(record.copy(state = DeliveryState.DONE)))
            tx.put(pendingKey(record.owner, record.id), null)
            "DELIVERED ${record.id}"
        }
    }

    fun review(record: DeliveryRecord, reason: String) {
        require(reason.isNotBlank())
        store.atomic { tx ->
            val current = decode(requireNotNull(tx.get(pendingKey(record.owner, record.id))))
            check(current == record) { "Delivery changed while marking review" }
            tx.put(pendingKey(record.owner, record.id), encode(record.copy(state = DeliveryState.REVIEW, reviewReason = reason)))
        }
    }

    /** Explicit operator decision after inspecting OFFLINE player data; previous evidence is kept in the ledger. */
    fun resolve(request: UUID, owner: UUID, id: UUID, actor: String, decision: DeliveryResolution, note: String) {
        require(actor.isNotBlank() && note.isNotBlank())
        store.execute(request, BankService.fingerprint("DELIVERY_RESOLVE", owner.toString(), id.toString(), actor, decision.name, note)) { tx ->
            val previous = decode(requireNotNull(tx.get(pendingKey(owner, id))) { "No unresolved delivery" })
            check(previous.owner == owner && previous.id == id)
            check(previous.state == DeliveryState.REVIEW || previous.state == DeliveryState.APPLYING) { "Delivery is not awaiting review" }
            val reason = "${decision.name} by $actor: $note"
            when (decision) {
                DeliveryResolution.DELIVERED -> {
                    check(previous.before != null && previous.after != null) { "Missing delivery evidence" }
                    require(InventoryPlan.grant(previous.before, previous.items, previous.slotLimit) == previous.after)
                    tx.put(doneKey(owner, id), encode(previous.copy(state = DeliveryState.DONE, reviewReason = reason)))
                    tx.put(pendingKey(owner, id), null)
                }
                DeliveryResolution.RETRY -> tx.put(pendingKey(owner, id), encode(previous.copy(
                    state = DeliveryState.PENDING, before = null, after = null, checkpoint = null, reviewReason = reason)))
            }
            YamlConfiguration().apply {
                set("owner", owner.toString()); set("delivery", id.toString()); set("actor", actor)
                set("decision", decision.name); set("note", note); set("previousEvidence", encode(previous))
            }.saveToString()
        }
    }

    private fun encode(record: DeliveryRecord): String = YamlConfiguration().apply {
        set("schemaVersion", 1); set("id", record.id.toString()); set("owner", record.owner.toString())
        set("reason", record.reason); set("state", record.state.name); set("reviewReason", record.reviewReason)
        set("checkpoint", record.checkpoint?.toString()); set("slotLimit", record.slotLimit)
        set("items", record.items.map { mapOf("payload" to it.payload, "count" to it.count, "limit" to it.limit) })
        fun image(value: List<StoredStack?>?) = value?.map { stack ->
            stack?.let { mapOf("payload" to it.payload, "count" to it.count, "limit" to it.limit) }
        }
        set("before", image(record.before)); set("after", image(record.after))
    }.saveToString()

    private fun decode(payload: String): DeliveryRecord {
        val data = YamlFiles.parse(payload)
        require(data.get("schemaVersion") == 1) { "Unsupported delivery schema" }
        fun number(map: Map<*, *>, name: String): Long = when (val value = map[name]) {
            is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            else -> throw IllegalArgumentException("Invalid delivery number: $name")
        }
        fun image(path: String): List<StoredStack?>? = data.getList(path)?.map { value ->
            if (value == null) null else {
                val map = value as Map<*, *>
                StoredStack(map["payload"] as String, Math.toIntExact(number(map, "count")), Math.toIntExact(number(map, "limit")))
            }
        }
        val items = requireNotNull(data.getList("items")).map { value ->
            val map = value as Map<*, *>
            ItemGrant(map["payload"] as String, number(map, "count"), Math.toIntExact(number(map, "limit")))
        }
        val rawLimit = data.get("slotLimit")
        val slotLimit = if (rawLimit == null) Int.MAX_VALUE else when (rawLimit) {
            is Byte, is Short, is Int, is Long -> Math.toIntExact((rawLimit as Number).toLong())
            else -> throw IllegalArgumentException("Invalid delivery slot limit")
        }
        require(slotLimit > 0)
        val record = DeliveryRecord(UUID.fromString(data.getString("id")), UUID.fromString(data.getString("owner")),
            requireNotNull(data.getString("reason")), items, DeliveryState.valueOf(requireNotNull(data.getString("state"))),
            image("before"), image("after"), data.getString("reviewReason"), data.getString("checkpoint")?.let(UUID::fromString),
            slotLimit)
        require(record.items.isNotEmpty() && record.reason.isNotBlank())
        if (record.state == DeliveryState.APPLYING || record.state == DeliveryState.DONE) {
            require(record.before != null && record.after != null && record.before.size == record.after.size)
            require(InventoryPlan.grant(record.before, record.items, record.slotLimit) == record.after) { "Invalid delivery plan" }
        }
        return record
    }

    companion object {
        private fun pendingKey(owner: UUID, id: UUID) = "delivery_pending/$owner/$id"
        private fun doneKey(owner: UUID, id: UUID) = "delivery_done/$owner/$id"
    }
}
