package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class ExchangeTerms(val product: String, val currency: String, val cost: Long, val reward: ItemGrant) {
    init { require(product.isNotBlank() && currency.isNotBlank() && cost >= 0) }
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("product", product); set("currency", currency); set("cost", cost)
        set("reward.payload", reward.payload); set("reward.count", reward.count); set("reward.limit", reward.limit)
    }.saveToString()
    companion object {
        fun decode(payload: String): ExchangeTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
            fun number(path: String): Long { val raw = data.get(path); require(raw is Int || raw is Long); return (raw as Number).toLong() }
            val limit = number("reward.limit"); require(limit in 1..Int.MAX_VALUE.toLong())
            return ExchangeTerms(requireNotNull(data.getString("product")), requireNotNull(data.getString("currency")), number("cost"),
                ItemGrant(requireNotNull(data.getString("reward.payload")), number("reward.count"), limit.toInt()))
        }
    }
}

/** Reward rights and completed removal commit together, never addItem before removing currency. */
class ItemExchange(private val store: StateStore, private val deliveries: DeliveryJournal) : RemovalEffect {
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        require(record.kind == KIND)
        Accounts.read(tx, record.evidence.owner)
        val terms = ExchangeTerms.decode(record.terms); require(terms.cost > 0)
        val e = record.evidence; var removed = 0L
        for (slot in e.before.indices) {
            if (e.before[slot] == e.after[slot]) continue
            val stack = requireNotNull(e.before[slot]); require(stack.payload == terms.currency)
            removed = Math.addExact(removed, (stack.count - (e.after[slot]?.count ?: 0)).toLong())
        }
        require(removed == terms.cost) { "Currency removal does not match frozen cost" }
    }
    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        validate(tx, record)
        val terms = ExchangeTerms.decode(record.terms)
        deliveries.stage(tx, record.evidence.id, record.evidence.owner, "item exchange ${terms.product}", listOf(terms.reward))
        return receipt(record.evidence.owner, terms)
    }
    fun free(request: UUID, owner: UUID, terms: ExchangeTerms): Boolean {
        require(terms.cost == 0L)
        return store.execute(request, BankService.fingerprint("FREE_ITEM_EXCHANGE", owner.toString(), terms.encode())) { tx ->
            Accounts.read(tx, owner)
            deliveries.stage(tx, request, owner, "item exchange ${terms.product}", listOf(terms.reward))
            receipt(owner, terms)
        }.replayed
    }
    private fun receipt(owner: UUID, terms: ExchangeTerms) = YamlConfiguration().apply {
        set("actor", owner.toString()); set("product", terms.product); set("cost", terms.cost)
        set("quantity", terms.reward.count); set("terms", terms.encode())
    }.saveToString()
    companion object { const val KIND = "ITEM_EXCHANGE" }
}
