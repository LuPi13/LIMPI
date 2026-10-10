package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class SelectionTerms(val ticket: String, val slot: Int, val grade: String, val previous: String, val ability: String) {
    init {
        require(ticket.isNotBlank() && slot >= 0 && ability.isNotBlank() && previous != ability)
        require(grade in setOf("COMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "TROLL"))
    }
    fun encode() = YamlConfiguration().apply {
        set("schemaVersion", 1); set("ticket", ticket); set("slot", slot)
        set("grade", grade); set("previous", previous); set("ability", ability)
    }.saveToString()
    companion object {
        fun decode(payload: String): SelectionTerms {
            val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
            val raw = data.get("slot"); require(raw is Int || raw is Long)
            val slot = (raw as Number).toLong(); require(slot in 0..Int.MAX_VALUE.toLong())
            fun string(path: String) = data.get(path) as? String ?: error("Missing/nonstring $path")
            return SelectionTerms(string("ticket"), slot.toInt(), string("grade"), string("previous"), string("ability"))
        }
    }
}
data class SelectionResult(val owner: UUID, val id: UUID, val terms: SelectionTerms)

/** A ticket buys a durable application request, never a best-effort immediate ability mutation. */
class AbilitySelection(private val store: StateStore) : RemovalEffect {
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        require(record.kind == KIND)
        val terms = SelectionTerms.decode(record.terms)
        val e = record.evidence
        PhysicalRemoval.validate(e.before, e.after)
        require(terms.slot in e.before.indices)
        require(e.before.indices.filter { e.before[it] != e.after[it] } == listOf(terms.slot))
        val stack = requireNotNull(e.before[terms.slot])
        require(stack.payload == terms.ticket && stack.count - (e.after[terms.slot]?.count ?: 0) == 1)
        val profile = Accounts.read(tx, e.owner)
        val code = profile.get("ability")
        require(code == null || code is String)
        require(code.orEmpty() == terms.previous) { "Applied ability changed after preparation" }
        val unlocked = profile.get("unlockedAbilities")
        require(unlocked is List<*> && unlocked.all { it is String } && terms.ability in unlocked) { "Ability is not unlocked" }
        require(tx.documents("ability_selection_pending/${e.owner}/").isEmpty() &&
            tx.documents("gacha_apply_pending/${e.owner}/").isEmpty()) { "An ability application is unresolved" }
        require(tx.get(resultKey(e.owner, e.id)) == null)
    }
    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        validate(tx, record)
        val result = SelectionResult(record.evidence.owner, record.evidence.id, SelectionTerms.decode(record.terms))
        val payload = encode(result)
        tx.put(resultKey(result.owner, result.id), payload)
        tx.put(pendingKey(result.owner, result.id), payload)
        return payload
    }
    fun find(owner: UUID, id: UUID): SelectionResult? = store.get(resultKey(owner, id))?.let { decode(it, owner, id) }
    fun pending(owner: UUID): List<SelectionResult> = store.documents("ability_selection_pending/$owner/").map { (key, value) ->
        val id = UUID.fromString(key.removePrefix("ability_selection_pending/$owner/"))
        decode(value, owner, id)
    }.also { check(it.size <= 1) { "Multiple unresolved ability selections" } }
    fun acknowledge(request: UUID, owner: UUID, id: UUID, ability: String): Boolean =
        store.execute(request, BankService.fingerprint("ABILITY_SELECTION_ACK", owner.toString(), id.toString(), ability)) { tx ->
            val result = decode(requireNotNull(tx.get(resultKey(owner, id))), owner, id)
            require(result.terms.ability == ability && Accounts.read(tx, owner).get("ability") == ability)
            tx.get(pendingKey(owner, id))?.let { check(decode(it, owner, id) == result) }
            tx.put(pendingKey(owner, id), null)
            encode(result)
        }.replayed
    private fun encode(result: SelectionResult) = YamlConfiguration().apply {
        set("schemaVersion", 1); set("owner", result.owner.toString()); set("id", result.id.toString()); set("terms", result.terms.encode())
    }.saveToString()
    private fun decode(payload: String, owner: UUID, id: UUID): SelectionResult {
        val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
        check(data.getString("owner") == owner.toString() && data.getString("id") == id.toString())
        return SelectionResult(owner, id, SelectionTerms.decode(requireNotNull(data.getString("terms"))))
    }
    companion object {
        const val KIND = "ABILITY_SELECTION"
        private fun resultKey(owner: UUID, id: UUID) = "ability_selection_result/$owner/$id"
        private fun pendingKey(owner: UUID, id: UUID) = "ability_selection_pending/$owner/$id"
    }
}
