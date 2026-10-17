package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration

data class AlchemyTerms(val slot: Int, val copper: String, val outcome: String, val result: ItemGrant, val lottoEligible: Boolean = false) {
    init { require(slot in 0..36 && copper.isNotBlank() && outcome in AlchemyPolicy.OUTPUTS && result.count == 1L) }
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("slot", slot); set("copper", copper); set("outcome", outcome)
        set("result.payload", result.payload); set("result.count", result.count); set("result.limit", result.limit)
        set("lottoEligible", lottoEligible)
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
                ItemGrant(requireNotNull(data.getString("result.payload")), integer("result.count"), limit.toInt()),
                (data.get("lottoEligible") ?: false).also { require(it is Boolean) } as Boolean)
        }
    }
}

/** 연금술은 이제 게임 안에서만 처리합니다. 이전 버전이 남긴 미완료 기록을 복구하려고만 등록해 둡니다. */
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
        if (terms.lottoEligible && terms.outcome == "netherite_ingot") {
            val key = "quest_trigger/${record.evidence.owner}/lotto"
            if (tx.get(key) == null) tx.put(key, record.evidence.id.toString())
        }
        return terms.encode()
    }
    companion object { const val KIND = "ALCHEMY" }
}
