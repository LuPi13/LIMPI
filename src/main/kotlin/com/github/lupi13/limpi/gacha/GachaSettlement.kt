package com.github.lupi13.limpi.gacha

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/** Server-created terms, frozen before ticket removal. No Bukkit ability objects or random source. */
data class GachaTerms(val ticket: String, val slot: Int, val before: GachaState, val outcome: DrawOutcome,
                      val pickupTime: Long, val forceTroll: Boolean) {
    init {
        require(ticket.isNotBlank() && slot >= 0 && outcome.ability.isNotBlank() && pickupTime >= 0)
        require(outcome.after == before.advance(outcome.grade, outcome.pickup)) { "Invalid frozen counters" }
        require(!forceTroll || outcome.grade == DrawGrade.TROLL)
    }
    fun encode(): String = YamlConfiguration().apply {
        set("schemaVersion", 1); set("ticket", ticket); set("slot", slot)
        set("grade", outcome.grade.name); set("ability", outcome.ability); set("pickup", outcome.pickup)
        set("pickupTime", pickupTime); set("forceTroll", forceTroll)
        GachaConfiguration.write(createSection("before"), before)
        GachaConfiguration.write(createSection("after"), outcome.after)
    }.saveToString()
    companion object {
        fun decode(payload: String): GachaTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
            fun integer(path: String): Long {
                val value = data.get(path); require(value is Int || value is Long) { "Non-integral $path" }
                return (value as Number).toLong()
            }
            fun state(path: String): GachaState {
                val section = requireNotNull(data.getConfigurationSection(path))
                for (field in listOf("LEGENDARY.Count", "EPIC.Count", "LEGENDARY.Ceiling", "EPIC.Ceiling")) integer("$path.$field")
                return GachaConfiguration.state(section)
            }
            fun boolean(path: String) = (data.get(path) as? Boolean) ?: error("Non-boolean $path")
            val slot = integer("slot"); require(slot in 0..Int.MAX_VALUE.toLong())
            return GachaTerms(requireNotNull(data.getString("ticket")), slot.toInt(), state("before"),
                DrawOutcome(DrawGrade.valueOf(requireNotNull(data.getString("grade"))), requireNotNull(data.getString("ability")),
                    boolean("pickup"), state("after")), integer("pickupTime"), boolean("forceTroll"))
        }
    }
}

data class GachaResult(val id: UUID, val owner: UUID, val terms: GachaTerms)

/** Ticket consumption is confirmed by RemovalJournal; unlock, pity and result then commit together. */
class GachaSettlement(private val store: StateStore) : RemovalEffect {
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        require(record.kind == KIND)
        val terms = GachaTerms.decode(record.terms)
        val evidence = record.evidence
        PhysicalRemoval.validate(evidence.before, evidence.after)
        require(terms.slot in evidence.before.indices)
        val changed = evidence.before.indices.filter { evidence.before[it] != evidence.after[it] }
        require(changed == listOf(terms.slot)) { "A draw must consume exactly one source slot" }
        val ticket = requireNotNull(evidence.before[terms.slot])
        require(ticket.payload == terms.ticket && ticket.count - (evidence.after[terms.slot]?.count ?: 0) == 1) {
            "A draw must consume exactly one matching ticket"
        }
        val profile = Accounts.read(tx, evidence.owner)
        require(GachaConfiguration.state(profile) == terms.before) { "Gacha counters changed after preparation" }
        unlocks(profile) // Fail before any physical consumption if the existing list is malformed.
        require(tx.get(resultKey(evidence.owner, evidence.id)) == null)
        require(tx.documents("gacha_apply_pending/${evidence.owner}/").isEmpty()) { "A forced ability application is unresolved" }
    }

    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        validate(tx, record)
        val terms = GachaTerms.decode(record.terms)
        val e = record.evidence
        val profile = Accounts.read(tx, e.owner)
        val unlocked = unlocks(profile).toMutableList()
        if (terms.outcome.ability !in unlocked) unlocked.add(terms.outcome.ability)
        profile.set("unlockedAbilities", unlocked)
        GachaConfiguration.write(profile, terms.outcome.after)
        tx.put(SqliteProfilePersistence.key(e.owner), profile.saveToString())
        val receipt = encode(GachaResult(e.id, e.owner, terms))
        tx.put(resultKey(e.owner, e.id), receipt)
        if (terms.forceTroll) tx.put(applyKey(e.owner, e.id), receipt)
        return receipt
    }

    fun find(owner: UUID, id: UUID): GachaResult? = store.get(resultKey(owner, id))?.let { decode(it, owner, id) }

    /** A new draw is refused until its predecessor's forced application is acknowledged. */
    fun pendingApplications(owner: UUID): List<GachaResult> = store.documents("gacha_apply_pending/$owner/").map { (key, value) ->
        val id = UUID.fromString(key.removePrefix("gacha_apply_pending/$owner/"))
        decode(value, owner, id).also { check(it.terms.forceTroll) }
    }.also { check(it.size <= 1) { "Multiple unresolved forced applications" } }

    /** Platform acknowledges only after applying/verifying the ability; acknowledgement cannot apply it. */
    fun acknowledgeApplication(request: UUID, owner: UUID, id: UUID, ability: String): Boolean =
        store.execute(request, BankService.fingerprint("GACHA_APPLY_ACK", owner.toString(), id.toString(), ability)) { tx ->
            val result = decode(requireNotNull(tx.get(resultKey(owner, id))), owner, id)
            require(result.terms.forceTroll && result.terms.outcome.ability == ability)
            val pending = tx.get(applyKey(owner, id))
            if (pending != null) check(decode(pending, owner, id) == result)
            require(Accounts.read(tx, owner).getString("ability") == ability) { "Forced ability has not been persisted" }
            tx.put(applyKey(owner, id), null)
            encode(result)
        }.replayed

    private fun unlocks(profile: YamlConfiguration): List<String> {
        val raw = profile.get("unlockedAbilities") ?: return emptyList()
        require(raw is List<*> && raw.all { it is String && it.isNotBlank() }) { "Malformed unlocked abilities" }
        return raw.map { it as String }
    }
    private fun encode(result: GachaResult): String = YamlConfiguration().apply {
        set("schemaVersion", 1); set("id", result.id.toString()); set("owner", result.owner.toString()); set("terms", result.terms.encode())
    }.saveToString()
    private fun decode(payload: String, owner: UUID, id: UUID): GachaResult {
        val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
        check(UUID.fromString(data.getString("owner")) == owner && UUID.fromString(data.getString("id")) == id)
        return GachaResult(id, owner, GachaTerms.decode(requireNotNull(data.getString("terms"))))
    }
    companion object {
        const val KIND = "GACHA"
        private fun resultKey(owner: UUID, id: UUID) = "gacha_result/$owner/$id"
        private fun applyKey(owner: UUID, id: UUID) = "gacha_apply_pending/$owner/$id"
    }
}
