package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration

data class AlchemyTerms(val slot: Int, val copper: String, val outcome: String, val result: ItemGrant) {
    init { require(slot in 0..36 && copper.isNotBlank() && outcome in AlchemyPolicy.OUTPUTS && result.count == 1L) }
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("slot", slot); set("copper", copper); set("outcome", outcome)
        set("result.payload", result.payload); set("result.count", result.count); set("result.limit", result.limit)
    }.saveToString()
    companion object {
        fun decode(payload: String): AlchemyTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
            fun integer(key: String): Long {
                val raw = data.get(key); require(raw is Int || raw is Long); return (raw as Number).toLong()
            }
            val slot = integer("slot"); val limit = integer("result.limit")
            require(slot in 0..36 && limit in 1..Int.MAX_VALUE.toLong())
            return AlchemyTerms(slot.toInt(), requireNotNull(data.getString("copper")), requireNotNull(data.getString("outcome")),
                ItemGrant(requireNotNull(data.getString("result.payload")), integer("result.count"), limit.toInt()))
        }
    }
}

class AlchemySettlement(private val deliveries: DeliveryJournal) : RemovalEffect {
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        require(record.kind == KIND); Accounts.read(tx, record.evidence.owner)
        val terms = AlchemyTerms.decode(record.terms); val e = record.evidence
        PhysicalRemoval.validate(e.before, e.after)
        require(e.before.size == 37 && e.before.indices.filter { e.before[it] != e.after[it] } == listOf(terms.slot))
        val source = requireNotNull(e.before[terms.slot])
        require(source.payload == terms.copper && source.count - (e.after[terms.slot]?.count ?: 0) == 10)
    }
    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        validate(tx, record)
        val terms = AlchemyTerms.decode(record.terms)
        deliveries.stage(tx, record.evidence.id, record.evidence.owner, "alchemy ${terms.outcome}", listOf(terms.result))
        return terms.encode()
    }
    companion object { const val KIND = "ALCHEMY" }
}
