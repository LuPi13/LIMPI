package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/** Whole selected stack, including armor/offhand; the receiver is frozen with the victim's removal. */
data class PickPocketTerms(val receiver: UUID, val slot: Int, val stolen: StoredStack) {
    init { require(slot in 0..40) }
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("receiver", receiver.toString()); set("slot", slot)
        set("payload", stolen.payload); set("count", stolen.count); set("limit", stolen.limit)
    }.saveToString()
    companion object {
        fun decode(payload: String): PickPocketTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
            fun integer(key: String): Int {
                val raw = data.get(key); require(raw is Int || raw is Long)
                val value = (raw as Number).toLong(); require(value in 0..Int.MAX_VALUE.toLong()); return value.toInt()
            }
            return PickPocketTerms(UUID.fromString(data.getString("receiver")), integer("slot"),
                StoredStack(requireNotNull(data.getString("payload")), integer("count"), integer("limit")))
        }
    }
}

class PickPocketSettlement(private val deliveries: DeliveryJournal) : RemovalEffect {
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        require(record.kind == KIND)
        val terms = PickPocketTerms.decode(record.terms); val e = record.evidence
        require(terms.receiver != e.owner)
        Accounts.read(tx, e.owner); Accounts.read(tx, terms.receiver)
        PhysicalRemoval.validate(e.before, e.after)
        require(e.before.size == 41 && e.before.indices.filter { e.before[it] != e.after[it] } == listOf(terms.slot))
        require(e.before[terms.slot] == terms.stolen && e.after[terms.slot] == null)
    }
    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        validate(tx, record)
        val terms = PickPocketTerms.decode(record.terms)
        deliveries.stage(tx, record.evidence.id, terms.receiver, "pick pocket from ${record.evidence.owner}",
            listOf(ItemGrant(terms.stolen.payload, terms.stolen.count.toLong(), terms.stolen.limit)))
        return terms.encode()
    }
    companion object { const val KIND = "PICK_POCKET" }
}
