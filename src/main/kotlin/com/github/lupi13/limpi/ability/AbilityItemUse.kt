package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import org.bukkit.configuration.file.YamlConfiguration
import com.github.lupi13.limpi.storage.*

/** Frozen post-wear return, or no return when a consumable breaks. */
data class AbilityItemTerms(val ability: String, val slot: Int, val payload: String, val returned: ItemGrant?) {
    init { require(ability.isNotBlank() && slot >= 0 && payload.isNotBlank()); require(returned == null || returned.count == 1L) }
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("ability", ability); set("slot", slot); set("payload", payload)
        set("returns", returned != null)
        returned?.let { set("returned.payload", it.payload); set("returned.count", it.count); set("returned.limit", it.limit) }
    }.saveToString()
    companion object {
        fun decode(payload: String): AbilityItemTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1 && data.get("returns") is Boolean)
            fun integer(path: String): Long {
                val raw = data.get(path); require(raw is Int || raw is Long); return (raw as Number).toLong()
            }
            val slot = integer("slot"); require(slot in 0..Int.MAX_VALUE.toLong())
            val returned = if (data.getBoolean("returns")) {
                val limit = integer("returned.limit"); require(limit in 1..Int.MAX_VALUE.toLong())
                ItemGrant(requireNotNull(data.getString("returned.payload")), integer("returned.count"), limit.toInt())
            } else null
            return AbilityItemTerms(requireNotNull(data.getString("ability")), slot.toInt(), requireNotNull(data.getString("payload")), returned)
        }
    }
}

class AbilityItemUse(private val deliveries: DeliveryJournal) : RemovalEffect {
    companion object { const val KIND = "ABILITY_ITEM_USE" }
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        require(record.kind == KIND); Accounts.read(tx, record.evidence.owner)
        val terms = AbilityItemTerms.decode(record.terms); val e = record.evidence
        PhysicalRemoval.validate(e.before, e.after)
        require(terms.slot in e.before.indices && e.before.indices.filter { e.before[it] != e.after[it] } == listOf(terms.slot))
        val stack = requireNotNull(e.before[terms.slot])
        require(stack.payload == terms.payload && stack.count - (e.after[terms.slot]?.count ?: 0) == 1)
    }
    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        validate(tx, record)
        val terms = AbilityItemTerms.decode(record.terms); val e = record.evidence
        terms.returned?.let { deliveries.stage(tx, e.id, e.owner, "ability item return ${terms.ability}", listOf(it)) }
        return terms.encode()
    }
}
