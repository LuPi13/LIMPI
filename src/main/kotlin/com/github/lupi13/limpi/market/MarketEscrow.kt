package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class EscrowState { APPLYING, REVIEW, DONE }
data class MarketEscrowRecord(val listing: UUID, val owner: UUID, val checkpoint: UUID,
    val before: List<StoredStack?>, val after: List<StoredStack?>, val slot: Int,
    val state: EscrowState = EscrowState.APPLYING, val reason: String? = null)

/** A listing is hidden until removal of its exact source stack has reached playerdata. */
class MarketEscrow(private val store: StateStore, private val catalog: MarketCatalog) {
    fun pending(owner: UUID): List<MarketEscrowRecord> = store.documents("market_escrow_pending/$owner/")
        .map { (key, payload) -> decode(payload).also { check(key == pendingKey(it.owner, it.listing) && it.owner == owner) } }

    fun prepare(id: UUID, owner: UUID, sellerName: String, code: String, name: String, price: Money,
                before: List<StoredStack?>, slot: Int): MarketEscrowRecord {
        require(slot in before.indices)
        val stack = requireNotNull(before[slot]) { "No item in selected slot" }
        val listing = MarketListing(id, code, name, owner, sellerName, price,
            ItemGrant(stack.payload, stack.count.toLong(), stack.limit), ListingState.ESCROW)
        val after = before.toMutableList().apply { this[slot] = null }.toList()
        val candidate = MarketEscrowRecord(id, owner, UUID.randomUUID(), before.toList(), after, slot)
        val fingerprint = BankService.fingerprint("MARKET_REGISTER", catalog.encode(listing),
            YamlConfiguration().apply { set("before", InventoryImages.encode(before)); set("slot", slot) }.saveToString())
        val committed = store.execute(id, fingerprint) { tx ->
            check(tx.documents("market_escrow_pending/$owner/").isEmpty()) { "An unresolved market registration exists" }
            check(catalog.read(tx, id) == null) { "Listing ID already exists" }
            catalog.write(tx, listing)
            tx.put(pendingKey(owner, id), encode(candidate))
            encode(candidate)
        }
        return decode(committed.entry.receipt)
    }

    fun apply(record: MarketEscrowRecord, inventory: DeliveryInventory) {
        check(record.state == EscrowState.APPLYING)
        val persisted = store.get(pendingKey(record.owner, record.listing))
        if (persisted == null) {
            check(store.get("market_escrow_done/${record.owner}/${record.listing}") != null) { "Escrow no longer exists" }
            return // A replay must not remove a newly acquired identical stack.
        }
        check(decode(persisted) == record) { "Escrow changed before applying" }
        // Only the freshly prepared caller or a fresh login may invoke this physical operation.
        val current = inventory.snapshot()
        val saved = inventory.persistedCheckpoint()
        try {
            when {
                saved == record.checkpoint && current == record.after -> Unit
                saved != record.checkpoint && current == record.before -> inventory.applyAndCheckpoint(record.before, record.after, record.checkpoint)
                else -> {
                    review(record, "Inventory or saved checkpoint differs from the escrow plan")
                    throw DeliveryRecoveryRequired(record.owner, record.listing)
                }
            }
            check(inventory.persistedCheckpoint() == record.checkpoint && inventory.snapshot() == record.after)
            complete(record)
        } catch (failure: Exception) {
            if (failure is DeliveryRecoveryRequired) throw failure
            throw DeliveryRecoveryRequired(record.owner, record.listing, failure)
        }
    }

    fun recover(owner: UUID, inventory: DeliveryInventory, freshLogin: Boolean) {
        val records = pending(owner)
        if (records.isEmpty()) return
        check(records.size == 1) { "Multiple escrow operations for $owner" }
        val record = records.single()
        if (!freshLogin || record.state == EscrowState.REVIEW) throw DeliveryRecoveryRequired(owner, record.listing)
        apply(record, inventory)
    }

    fun resolve(request: UUID, actor: String, owner: UUID, listingId: UUID, removed: Boolean, note: String) {
        require(actor.isNotBlank() && note.isNotBlank())
        store.execute(request, BankService.fingerprint("MARKET_ESCROW_RESOLVE", actor, owner.toString(), listingId.toString(), removed.toString(), note)) { tx ->
            val previous = decode(requireNotNull(tx.get(pendingKey(owner, listingId))))
            check(previous.owner == owner && previous.listing == listingId)
            val listing = requireNotNull(catalog.read(tx, listingId))
            check(listing.state == ListingState.ESCROW && listing.seller == owner)
            catalog.write(tx, listing.copy(state = if (removed) ListingState.ACTIVE else ListingState.RETURNED))
            tx.put("market_escrow_done/$owner/$listingId", encode(previous.copy(state = EscrowState.DONE, reason = note)))
            tx.put(pendingKey(owner, listingId), null)
            YamlConfiguration().apply {
                set("actor", actor); set("removed", removed); set("note", note); set("previousEvidence", encode(previous))
            }.saveToString()
        }
    }

    private fun complete(record: MarketEscrowRecord) {
        val id = UUID.nameUUIDFromBytes("market-activate:${record.owner}:${record.listing}".toByteArray(Charsets.UTF_8))
        store.execute(id, "market-activate:${record.owner}:${record.listing}") { tx ->
            check(decode(requireNotNull(tx.get(pendingKey(record.owner, record.listing)))) == record)
            val listing = requireNotNull(catalog.read(tx, record.listing))
            check(listing.state == ListingState.ESCROW && listing.seller == record.owner)
            val source = requireNotNull(record.before[record.slot])
            check(listing.item == ItemGrant(source.payload, source.count.toLong(), source.limit))
            catalog.write(tx, listing.copy(state = ListingState.ACTIVE))
            tx.put("market_escrow_done/${record.owner}/${record.listing}", encode(record.copy(state = EscrowState.DONE)))
            tx.put(pendingKey(record.owner, record.listing), null)
            "ACTIVE ${record.listing}"
        }
    }

    private fun review(record: MarketEscrowRecord, reason: String) = store.atomic { tx ->
        check(decode(requireNotNull(tx.get(pendingKey(record.owner, record.listing)))) == record)
        tx.put(pendingKey(record.owner, record.listing), encode(record.copy(state = EscrowState.REVIEW, reason = reason)))
    }

    private fun encode(record: MarketEscrowRecord) = YamlConfiguration().apply {
        set("schemaVersion", 1); set("listing", record.listing.toString()); set("owner", record.owner.toString())
        set("checkpoint", record.checkpoint.toString()); set("state", record.state.name); set("slot", record.slot)
        set("before", InventoryImages.encode(record.before)); set("after", InventoryImages.encode(record.after)); set("reason", record.reason)
    }.saveToString()

    private fun decode(payload: String): MarketEscrowRecord {
        val data = YamlFiles.parse(payload)
        require(data.get("schemaVersion") == 1)
        val before = InventoryImages.decode(requireNotNull(data.getList("before")))
        val after = InventoryImages.decode(requireNotNull(data.getList("after")))
        val rawSlot = data.get("slot")
        require(rawSlot is Int)
        val record = MarketEscrowRecord(UUID.fromString(data.getString("listing")), UUID.fromString(data.getString("owner")),
            UUID.fromString(data.getString("checkpoint")), before, after, rawSlot,
            EscrowState.valueOf(requireNotNull(data.getString("state"))), data.getString("reason"))
        require(record.slot in before.indices && before[record.slot] != null)
        require(after == before.toMutableList().apply { this[record.slot] = null })
        return record
    }

    companion object { private fun pendingKey(owner: UUID, id: UUID) = "market_escrow_pending/$owner/$id" }
}
