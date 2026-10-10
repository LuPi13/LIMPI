package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class SugarTerms(val slot: Int, val sugar: String, val effect: String, val duration: Int,
                      val world: UUID, val targets: List<UUID>, val range: Double) {
    init {
        require(slot in 0..36 && sugar.isNotBlank() && effect.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")))
        require(duration > 0 && targets.size <= 256 && targets.toSet().size == targets.size && range.isFinite() && range in 0.0..128.0)
    }
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("slot", slot); set("sugar", sugar); set("effect", effect); set("duration", duration)
        set("world", world.toString()); set("targets", targets.map(UUID::toString)); set("range", range)
    }.saveToString()
    companion object {
        fun decode(payload: String): SugarTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
            fun integer(key: String): Int {
                val raw = data.get(key); require(raw is Int || raw is Long)
                val value = (raw as Number).toLong(); require(value in 0..Int.MAX_VALUE.toLong()); return value.toInt()
            }
            val targets = data.get("targets"); require(targets is List<*> && targets.all { it is String })
            val range = data.get("range"); require(range is Number)
            return SugarTerms(integer("slot"), requireNotNull(data.getString("sugar")), requireNotNull(data.getString("effect")), integer("duration"),
                UUID.fromString(data.getString("world")), targets.map { UUID.fromString(it as String) }, range.toDouble())
        }
    }
}

/** Durable frozen outcome; activation is explicitly at most once because live instant effects cannot be atomically committed with SQLite. */
class SugarSettlement(private val store: StateStore) : RemovalEffect {
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        require(record.kind == KIND); Accounts.read(tx, record.evidence.owner)
        val terms = SugarTerms.decode(record.terms); val e = record.evidence
        PhysicalRemoval.validate(e.before, e.after)
        require(e.before.size == 37 && e.before.indices.filter { e.before[it] != e.after[it] } == listOf(terms.slot))
        val source = requireNotNull(e.before[terms.slot])
        require(source.payload == terms.sugar && source.count - (e.after[terms.slot]?.count ?: 0) == 1)
        require(e.owner !in terms.targets && tx.documents("sugar_pending/${e.owner}/").isEmpty())
    }
    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        validate(tx, record)
        tx.put(resultKey(record.evidence.owner, record.evidence.id), record.terms)
        tx.put(pendingKey(record.evidence.owner, record.evidence.id), record.terms)
        return record.terms
    }
    fun pending(owner: UUID): Map<UUID, SugarTerms> = store.documents("sugar_pending/$owner/").mapKeys { UUID.fromString(it.key.removePrefix("sugar_pending/$owner/")) }
        .mapValues { SugarTerms.decode(it.value) }.also { check(it.size <= 1) }
    fun activate(owner: UUID, id: UUID): SugarTerms? {
        val result = store.execute(UUID.nameUUIDFromBytes("sugar-activate:$owner:$id".toByteArray(Charsets.UTF_8)),
            BankService.fingerprint("SUGAR_ACTIVATE", owner.toString(), id.toString())) { tx ->
            val payload = requireNotNull(tx.get(resultKey(owner, id))); SugarTerms.decode(payload)
            check(tx.get(pendingKey(owner, id)) == payload)
            tx.put(pendingKey(owner, id), null)
            payload
        }
        return if (result.replayed) null else SugarTerms.decode(result.entry.receipt)
    }
    companion object {
        const val KIND = "SUGAR"
        private fun resultKey(owner: UUID, id: UUID) = "sugar_result/$owner/$id"
        private fun pendingKey(owner: UUID, id: UUID) = "sugar_pending/$owner/$id"
    }
}
