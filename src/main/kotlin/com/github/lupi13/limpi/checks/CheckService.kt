package com.github.lupi13.limpi.checks

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class CheckState { ISSUED, REDEEMED }
data class CheckRecord(val id: UUID, val issuer: UUID, val amount: Money, val state: CheckState,
    val redeemer: UUID? = null, val redemption: UUID? = null)
enum class CheckFailure(val message: String) {
    UNKNOWN("서버 발행 기록이 없는 수표입니다."), REDEEMED("이미 사용한 수표입니다. 다시 입금하지 않습니다."),
    LEGACY_CONVERTED("이미 전환한 기존 수표입니다."),
}
class CheckException(val reason: CheckFailure) : IllegalStateException(reason.message)
data class CheckReceipt(val transaction: UUID, val check: UUID, val amount: Long, val replayed: Boolean)

class CheckService(private val store: StateStore, private val deliveries: DeliveryJournal, private val players: PlayerRepository) {
    fun find(id: UUID): CheckRecord? = store.get(key(id))?.let { decode(it).also { value -> check(value.id == id) } }

    fun issue(request: UUID, issuer: UUID, amount: Long, item: (UUID, Money) -> ItemGrant): CheckReceipt {
        require(amount > 0) { "수표 금액은 양수여야 합니다." }
        val committed = store.execute(request, BankService.fingerprint("CHECK_ISSUE", issuer.toString(), amount.toString())) { tx ->
            val account = Accounts.read(tx, issuer); val before = Accounts.balance(account); val money = Money(amount)
            if (before.units < amount) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            check(tx.get(key(request)) == null)
            val check = CheckRecord(request, issuer, money, CheckState.ISSUED)
            val grant = item(request, money); require(grant.count == 1L && grant.limit == 1)
            Accounts.write(tx, issuer, account, before - money)
            tx.put(key(request), encode(check))
            deliveries.stage(tx, request, issuer, "check issue $request", listOf(grant))
            receipt(request, check, issuer.toString(), before.units, before.units - amount)
        }
        return result(committed)
    }

    /** Redemption makes every physical copy of this UUID valueless, even if inventory cleanup/save fails. */
    fun redeem(request: UUID, id: UUID, recipient: UUID): CheckReceipt {
        val committed = store.execute(request, BankService.fingerprint("CHECK_REDEEM", id.toString(), recipient.toString())) { tx ->
            val value = tx.get(key(id)) ?: throw CheckException(CheckFailure.UNKNOWN)
            val check = decode(value); check(check.id == id)
            if (check.state != CheckState.ISSUED) throw CheckException(CheckFailure.REDEEMED)
            val account = Accounts.read(tx, recipient); val before = Accounts.balance(account)
            val after = try { before + check.amount } catch (_: ArithmeticException) { throw BankException(BankFailure.OVERFLOW) }
            Accounts.write(tx, recipient, account, after)
            tx.put(key(id), encode(check.copy(state = CheckState.REDEEMED, redeemer = recipient, redemption = request)))
            receipt(request, check, recipient.toString(), before.units, after.units)
        }
        return result(committed)
    }

    /** No legacy public hash proves issuance. Only an audited operator decision can import a claim. */
    fun importLegacy(request: UUID, actor: String, fingerprint: String, owner: UUID, issuer: UUID, amount: Long,
                     note: String, item: (UUID, Money) -> ItemGrant): CheckReceipt {
        require(amount > 0 && actor.isNotBlank() && note.isNotBlank())
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        val committed = store.execute(request, BankService.fingerprint("CHECK_IMPORT", actor, fingerprint, owner.toString(), issuer.toString(), amount.toString(), note)) { tx ->
            Accounts.read(tx, owner); Accounts.read(tx, issuer)
            if (tx.get("check_legacy/$fingerprint") != null) throw CheckException(CheckFailure.LEGACY_CONVERTED)
            check(tx.get(key(request)) == null)
            val check = CheckRecord(request, issuer, Money(amount), CheckState.ISSUED)
            val grant = item(request, check.amount); require(grant.count == 1L && grant.limit == 1)
            tx.put(key(request), encode(check)); tx.put("check_legacy/$fingerprint", request.toString())
            deliveries.stage(tx, request, owner, "approved legacy check $fingerprint", listOf(grant))
            YamlConfiguration().apply {
                set("check", request.toString()); set("amount", amount); set("actor", actor); set("owner", owner.toString())
                set("issuer", issuer.toString()); set("legacyFingerprint", fingerprint); set("note", note)
            }.saveToString()
        }
        return result(committed)
    }

    private fun result(committed: CommittedTransaction): CheckReceipt {
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        val data = YamlFiles.parse(committed.entry.receipt)
        return CheckReceipt(committed.entry.id, UUID.fromString(data.getString("check")), data.getLong("amount"), committed.replayed)
    }
    private fun receipt(request: UUID, check: CheckRecord, actor: String, before: Long, after: Long) = YamlConfiguration().apply {
        set("transaction", request.toString()); set("check", check.id.toString()); set("amount", check.amount.units)
        set("actor", actor); set("before", before); set("after", after)
    }.saveToString()
    private fun encode(value: CheckRecord) = YamlConfiguration().apply {
        set("schemaVersion", 1); set("id", value.id.toString()); set("issuer", value.issuer.toString()); set("amount", value.amount.units)
        set("state", value.state.name); set("redeemer", value.redeemer?.toString()); set("redemption", value.redemption?.toString())
    }.saveToString()
    private fun decode(payload: String): CheckRecord {
        val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1)
        val raw = data.get("amount"); require(raw is Int || raw is Long)
        val money = Money((raw as Number).toLong()); require(money.units > 0)
        val record = CheckRecord(UUID.fromString(data.getString("id")), UUID.fromString(data.getString("issuer")), money,
            CheckState.valueOf(requireNotNull(data.getString("state"))), data.getString("redeemer")?.let(UUID::fromString),
            data.getString("redemption")?.let(UUID::fromString))
        require(record.state != CheckState.REDEEMED || (record.redeemer != null && record.redemption != null))
        return record
    }
    companion object { private fun key(id: UUID) = "check/$id" }
}
