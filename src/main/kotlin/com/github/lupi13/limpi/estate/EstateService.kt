package com.github.lupi13.limpi.estate

import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.CommittedTransaction
import com.github.lupi13.limpi.storage.StateStore
import com.github.lupi13.limpi.storage.StateTransaction
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/**
 * Every estate change goes through here. The DB is the source; after each commit the in-memory snapshot (and the
 * protection index) is rebuilt from it. Estates never touch inventories, so a purchase or sale is one transaction.
 */
class EstateService(private val store: StateStore, private val players: PlayerRepository,
                    private val clock: () -> Long = System::currentTimeMillis) {
    private class Snapshot(val estates: Map<UUID, Estate>, val damaged: Map<String, String>, val index: EstateIndex)

    @Volatile private var snapshot = load()

    private fun load(): Snapshot {
        val estates = mutableMapOf<UUID, Estate>(); val damaged = mutableMapOf<String, String>()
        decodeAll(store.documents(EstateDocuments.PREFIX), estates, damaged)
        return Snapshot(estates, damaged, EstateIndex(estates.values))
    }

    private fun decodeAll(documents: Map<String, String>, estates: MutableMap<UUID, Estate>, damaged: MutableMap<String, String>) {
        for ((key, payload) in documents) {
            try { EstateDocuments.decode(key, payload).second.let { estates[it.id] = it } }
            catch (failure: Exception) { damaged[key] = payload }
        }
    }

    fun refresh() { snapshot = load() }

    fun at(world: UUID, x: Int, y: Int, z: Int): Estate? = snapshot.index.at(world, x, y, z)
    fun hasWorld(world: UUID): Boolean = snapshot.index.hasWorld(world)
    fun all(): Collection<Estate> = snapshot.estates.values
    fun get(id: UUID): Estate? = snapshot.estates[id]
    fun owned(owner: UUID): List<Estate> = snapshot.estates.values.filter { it.owner == owner }.sortedBy { it.name.lowercase() }
    fun shared(member: UUID): List<Estate> = snapshot.estates.values.filter { member in it.members }.sortedBy { it.name.lowercase() }
    fun byName(owner: UUID, name: String): Estate? = snapshot.estates.values.firstOrNull { it.owner == owner && EstatePolicy.sameName(it.name, name) }
    /** Documents that could not be decoded, by key, with their raw payload. */
    fun damaged(): Map<String, String> = snapshot.damaged
    fun damagedOwners(): Set<UUID> = snapshot.damaged.values.mapNotNull(EstateDocuments::ownerOf).toSet()

    /** An owner with a damaged estate record cannot manage estates until an administrator has looked at it. */
    fun requireUsable(owner: UUID) {
        if (owner in damagedOwners()) throw EstateUnavailable("사유지 기록에 오류가 있어 지금은 사용할 수 없습니다. 관리자에게 문의하세요.")
    }

    fun quote(owner: UUID, region: EstateRegion, worldName: String, settings: EstateSettings): Money {
        requireUsable(owner)
        if (snapshot.damaged.isNotEmpty()) throw EstateUnavailable(DAMAGED)
        return EstatePolicy.price(owner, region, worldName, settings, snapshot.estates.values)
    }

    fun buy(request: UUID, owner: UUID, name: String, region: EstateRegion, worldName: String, displayedPrice: Long, settings: EstateSettings): Estate {
        require(displayedPrice >= 0)
        if (!EstatePolicy.validName(name)) throw EstateUnavailable("사유지 이름은 공백 없이 1~${EstatePolicy.MAX_NAME}자로 정해주세요.")
        val id = UUID.nameUUIDFromBytes("estate:$request".toByteArray())
        val fingerprint = BankService.fingerprint("ESTATE_BUY", owner.toString(), name, region.toString(), displayedPrice.toString())
        commit(store.execute(request, fingerprint) { tx ->
            val (estates, damaged) = estates(tx)
            if (damaged.isNotEmpty()) throw EstateUnavailable(DAMAGED)
            if (estates.any { it.owner == owner && EstatePolicy.sameName(it.name, name) }) throw EstateUnavailable("같은 이름의 사유지가 이미 존재합니다.")
            val price = EstatePolicy.price(owner, region, worldName, settings, estates)
            if (price.units != displayedPrice) throw EstateUnavailable("가격이 변경되었습니다. 구역을 다시 선택해주세요.")
            val account = Accounts.read(tx, owner); val before = Accounts.balance(account)
            if (before.units < price.units) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            Accounts.write(tx, owner, account, before - price)
            val estate = Estate(id, owner, name, region, worldName, price, clock(), emptySet())
            tx.put(EstateDocuments.key(id), EstateDocuments.encode(YamlConfiguration(), estate))
            receipt("buy", owner.toString(), estate, price.units, before.units, before.units - price.units)
        })
        return get(id) ?: throw EstateUnavailable("이미 처리된 요청입니다.")
    }

    /** Refund for selling [estate] now. */
    fun refund(estate: Estate, settings: EstateSettings): Money = settings.refund(estate.paid)

    fun sell(request: UUID, owner: UUID, estateId: UUID, displayedRefund: Long, settings: EstateSettings): Pair<Estate, Money> {
        requireUsable(owner)
        var result: Pair<Estate, Money>? = null
        val fingerprint = BankService.fingerprint("ESTATE_SELL", owner.toString(), estateId.toString(), displayedRefund.toString())
        val committed = store.execute(request, fingerprint) { tx ->
            val estate = read(tx, estateId)?.second?.takeIf { it.owner == owner } ?: throw EstateUnavailable("존재하지 않는 사유지입니다.")
            val refund = settings.refund(estate.paid)
            if (refund.units != displayedRefund) throw EstateUnavailable("돌려받는 금액이 변경되었습니다. 다시 시도하세요.")
            val (before, after) = credit(tx, owner, refund)
            tx.put(EstateDocuments.key(estateId), null)
            result = estate to refund
            receipt("sell", owner.toString(), estate, refund.units, before, after)
        }
        commit(committed)
        return result ?: replayed(committed)
    }

    /** Adds or removes a trusted builder. Returns false when nothing changed. */
    fun setMember(owner: UUID, estateId: UUID, member: UUID, trusted: Boolean): Boolean {
        requireUsable(owner)
        if (member == owner) throw EstateUnavailable("본인은 이미 사유지의 주인입니다.")
        var changed = false
        store.atomic { tx ->
            val (data, estate) = read(tx, estateId)?.takeIf { it.second.owner == owner } ?: throw EstateUnavailable("존재하지 않는 사유지입니다.")
            val members = if (trusted) estate.members + member else estate.members - member
            changed = members != estate.members
            if (changed) tx.put(EstateDocuments.key(estateId), EstateDocuments.encode(data, estate.copy(members = members)))
        }
        if (changed) refresh()
        return changed
    }

    /** Administrator removal; the owner gets the normal sale refund only when [refund] is set. */
    fun remove(request: UUID, actor: String, estateId: UUID, refund: Boolean, reason: String, settings: EstateSettings): Pair<Estate, Money> {
        require(actor.isNotBlank() && reason.isNotBlank())
        var result: Pair<Estate, Money>? = null
        val fingerprint = BankService.fingerprint("ESTATE_REMOVE", actor, estateId.toString(), refund.toString(), reason)
        val committed = store.execute(request, fingerprint) { tx ->
            val estate = read(tx, estateId)?.second ?: throw EstateUnavailable("존재하지 않는 사유지입니다.")
            val amount = if (refund) settings.refund(estate.paid) else Money(0)
            val (before, after) = credit(tx, estate.owner, amount)
            tx.put(EstateDocuments.key(estateId), null)
            result = estate to amount
            receipt("remove", actor, estate, amount.units, before, after, reason)
        }
        commit(committed)
        return result ?: replayed(committed)
    }

    /** Removes a document that cannot be decoded; its payload is kept in the ledger receipt. */
    fun discard(request: UUID, actor: String, id: UUID, reason: String) {
        require(actor.isNotBlank() && reason.isNotBlank())
        val key = EstateDocuments.key(id)
        commit(store.execute(request, BankService.fingerprint("ESTATE_DISCARD", actor, key, reason)) { tx ->
            val payload = tx.get(key) ?: throw EstateUnavailable("해당 사유지 기록이 없습니다.")
            if (runCatching { EstateDocuments.decode(key, payload) }.isSuccess) throw EstateUnavailable("정상 기록은 버릴 수 없습니다. /estate remove를 사용하세요.")
            tx.put(key, null)
            YamlConfiguration().apply {
                set("operation", "discard"); set("actor", actor); set("key", key); set("reason", reason); set("payload", payload)
            }.saveToString()
        })
    }

    private fun estates(tx: StateTransaction): Pair<List<Estate>, Map<String, String>> {
        val estates = mutableMapOf<UUID, Estate>(); val damaged = mutableMapOf<String, String>()
        decodeAll(tx.documents(EstateDocuments.PREFIX), estates, damaged)
        return estates.values.toList() to damaged
    }

    private fun read(tx: StateTransaction, id: UUID): Pair<YamlConfiguration, Estate>? {
        val key = EstateDocuments.key(id)
        val payload = tx.get(key) ?: return null
        return try { EstateDocuments.decode(key, payload) } catch (failure: Exception) {
            throw EstateUnavailable("사유지 기록에 오류가 있어 지금은 사용할 수 없습니다. 관리자에게 문의하세요.")
        }
    }

    private fun credit(tx: StateTransaction, owner: UUID, amount: Money): Pair<Long, Long> {
        val account = Accounts.read(tx, owner); val before = Accounts.balance(account)
        val after = try { before + amount } catch (failure: ArithmeticException) { throw BankException(BankFailure.OVERFLOW) }
        if (amount.units > 0) Accounts.write(tx, owner, account, after)
        return before.units to after.units
    }

    private fun receipt(operation: String, actor: String, estate: Estate, amount: Long, before: Long, after: Long, reason: String? = null): String =
        YamlConfiguration().apply {
            if (reason != null) set("reason", reason)
            set("operation", operation); set("actor", actor); set("estate", estate.id.toString()); set("owner", estate.owner.toString())
            set("name", estate.name); set("region", estate.region.describe()); set("world", estate.worldName)
            set("amount", amount); set("before", before); set("after", after)
        }.saveToString()

    /** A retried request whose first attempt already committed: report what that attempt did. */
    private fun replayed(committed: CommittedTransaction): Pair<Estate, Money> {
        val data = YamlFiles.parse(committed.entry.receipt)
        throw EstateUnavailable("이미 처리된 요청입니다: ${data.getString("name")} (${data.getLong("amount")}원)")
    }

    private fun commit(committed: CommittedTransaction) {
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        refresh()
    }

    companion object {
        const val DAMAGED = "확인이 필요한 사유지 기록이 있어 지금은 구매할 수 없습니다. 관리자에게 문의하세요."
    }
}
