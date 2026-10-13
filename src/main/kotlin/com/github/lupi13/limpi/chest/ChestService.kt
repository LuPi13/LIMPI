package com.github.lupi13.limpi.chest

import com.github.lupi13.limpi.delivery.DeliveryInventory
import com.github.lupi13.limpi.delivery.DeliveryRecoveryRequired
import com.github.lupi13.limpi.delivery.InventoryImages
import com.github.lupi13.limpi.delivery.StoredStack
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChestUnavailable(message: String) : IllegalStateException(message)

enum class ChestSessionState { OPEN, CLOSING, REVIEW }

/**
 * One open chest window. The DB keeps the chest as it was when opened ([chestBefore]) until the close is committed.
 * The player data file is checkpointed when the window opens and again when it closes, so recovery can tell whether the
 * saved player inventory belongs to the state before opening or after closing.
 */
data class ChestSession(val id: UUID, val owner: UUID, val state: ChestSessionState, val openCheckpoint: UUID,
                        val playerBefore: List<StoredStack?>, val chestBefore: List<StoredStack?>,
                        val closeCheckpoint: UUID? = null, val playerAfter: List<StoredStack?>? = null,
                        val chestAfter: List<StoredStack?>? = null, val reason: String? = null) {
    init {
        require(playerBefore.isNotEmpty() && chestBefore.isNotEmpty())
        require((closeCheckpoint == null) == (playerAfter == null) && (playerAfter == null) == (chestAfter == null))
        require(playerAfter == null || playerAfter.size == playerBefore.size)
        require(chestAfter == null || chestAfter.size == chestBefore.size)
        require(state != ChestSessionState.CLOSING || closeCheckpoint != null)
    }
}

enum class ChestRecovery { NONE, ROLLED_BACK, COMPLETED }

/** Purchases, auto store, death drops and open windows. Every change goes through here so the cached state stays current. */
class ChestService(private val store: StateStore, private val players: PlayerRepository) {
    private val cache = ConcurrentHashMap<UUID, ChestState>()
    private val absorbed = ConcurrentHashMap<UUID, Int>()

    init {
        // Entities that were stored in a chest; if an older world save brings one back it is trimmed on load.
        store.documents("chest_absorbed/").values.forEach { payload ->
            val data = YamlFiles.parse(payload)
            absorbed[UUID.fromString(data.getString("entity"))] = data.getInt("remaining")
        }
    }

    fun state(owner: UUID): ChestState = cache[owner] ?: ChestDocuments.decode(owner, store.get(ChestDocuments.key(owner))).second.also { cache[owner] = it }
    fun release(owner: UUID) { cache.remove(owner) }

    /** Expected item count of an absorbed entity after the absorption, or null when it was never absorbed. */
    fun absorbedRemaining(entity: UUID): Int? = absorbed[entity]

    fun buy(request: UUID, owner: UUID, product: ChestProduct, displayedPrice: Long, prices: ChestPrices): ChestState {
        require(displayedPrice >= 0)
        val committed = store.execute(request, BankService.fingerprint("CHEST_BUY", owner.toString(), product.name, displayedPrice.toString())) { tx ->
            requireNoSession(tx, owner)
            val (data, state) = ChestDocuments.decode(owner, tx.get(ChestDocuments.key(owner)))
            val price = prices.quote(state, product) ?: throw ChestUnavailable(when (product) {
                ChestProduct.UNLOCK -> "이미 상자를 해금했습니다."
                ChestProduct.UPGRADE -> if (state.unlocked) "상자가 이미 최대 크기입니다." else "먼저 상자를 해금하세요."
                ChestProduct.AUTO_STORE -> if (state.unlocked) "이미 자동 저장을 구매했습니다." else "먼저 상자를 해금하세요."
                ChestProduct.ENDER -> if (state.unlocked) "이미 엔더화를 구매했습니다." else "먼저 상자를 해금하세요."
            })
            if (price.units != displayedPrice) throw ChestUnavailable("가격이 변경되었습니다. 화면을 다시 열어주세요.")
            val account = Accounts.read(tx, owner); val before = Accounts.balance(account)
            if (before.units < price.units) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            Accounts.write(tx, owner, account, before - price)
            val next = when (product) {
                ChestProduct.UNLOCK -> ChestState(1, false, false, false, List(9) { null })
                ChestProduct.UPGRADE -> state.copy(rows = state.rows + 1, slots = state.slots + List(9) { null })
                ChestProduct.AUTO_STORE -> state.copy(autoOwned = true, autoEnabled = true)
                ChestProduct.ENDER -> state.copy(ender = true)
            }
            tx.put(ChestDocuments.key(owner), ChestDocuments.encode(owner, data, next))
            YamlConfiguration().apply {
                set("actor", owner.toString()); set("product", product.name); set("price", price.units)
                set("before", before.units); set("after", before.units - price.units); set("rows", next.rows)
            }.saveToString()
        }
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        cache.remove(owner)
        return state(owner)
    }

    /** null toggles. Returns the new setting. */
    fun setAuto(owner: UUID, enabled: Boolean?): Boolean {
        var result = false
        store.atomic { tx ->
            val (data, state) = ChestDocuments.decode(owner, tx.get(ChestDocuments.key(owner)))
            if (!state.autoOwned) throw ChestUnavailable("자동 저장을 구매하지 않았습니다. /chest shop에서 구매할 수 있습니다.")
            result = enabled ?: !state.autoEnabled
            tx.put(ChestDocuments.key(owner), ChestDocuments.encode(owner, data, state.copy(autoEnabled = result)))
        }
        cache.remove(owner)
        return result
    }

    /**
     * Store as much of a ground item as fits. [entityCount] is the entity's count before absorbing; the record lets a
     * reloaded older copy of the entity be trimmed back so the stored part is not duplicated.
     */
    fun absorb(request: UUID, owner: UUID, entity: UUID, payload: String, entityCount: Int, limit: Int): Int {
        val cached = state(owner)
        if (!cached.autoEnabled || ChestSlots.insert(cached.slots, payload, entityCount, limit).second == 0) return 0
        val committed = store.execute(request, BankService.fingerprint("CHEST_ABSORB", owner.toString(), entity.toString(), payload, entityCount.toString())) { tx ->
            requireNoSession(tx, owner)
            val (data, state) = ChestDocuments.decode(owner, tx.get(ChestDocuments.key(owner)))
            check(state.autoEnabled) { "Auto store is off" }
            val (slots, accepted) = ChestSlots.insert(state.slots, payload, entityCount, limit)
            check(accepted > 0) { "Chest is full" }
            tx.put(ChestDocuments.key(owner), ChestDocuments.encode(owner, data, state.copy(slots = slots)))
            tx.put(absorbedKey(entity), YamlConfiguration().apply {
                set("entity", entity.toString()); set("owner", owner.toString()); set("remaining", entityCount - accepted)
            }.saveToString())
            YamlConfiguration().apply { set("owner", owner.toString()); set("entity", entity.toString()); set("accepted", accepted) }.saveToString()
        }
        cache.remove(owner)
        val accepted = YamlFiles.parse(committed.entry.receipt).getInt("accepted")
        absorbed[entity] = entityCount - accepted
        return accepted
    }

    /** Empties a non-ender chest on death and returns what must be dropped; the ledger keeps the removed image. */
    fun deathDrop(request: UUID, owner: UUID): List<StoredStack> {
        val cached = state(owner)
        if (!cached.unlocked || cached.ender || cached.slots.all { it == null }) return emptyList()
        val committed = store.execute(request, BankService.fingerprint("CHEST_DEATH_DROP", owner.toString())) { tx ->
            requireNoSession(tx, owner)
            val (data, state) = ChestDocuments.decode(owner, tx.get(ChestDocuments.key(owner)))
            val dropped = if (state.ender) emptyList() else state.slots
            if (dropped.any { it != null }) tx.put(ChestDocuments.key(owner), ChestDocuments.encode(owner, data, state.copy(slots = List(state.slots.size) { null })))
            YamlConfiguration().apply { set("owner", owner.toString()); set("dropped", InventoryImages.encode(dropped)) }.saveToString()
        }
        cache.remove(owner)
        return InventoryImages.decode(requireNotNull(YamlFiles.parse(committed.entry.receipt).getList("dropped"))).filterNotNull()
    }

    fun pending(owner: UUID): List<ChestSession> = store.documents("chest_session/$owner/")
        .map { (key, payload) -> decode(payload).also { check(key == sessionKey(it.owner, it.id) && it.owner == owner) } }

    /** Records the window before the open checkpoint is saved; nothing else may use this player's checkpoint meanwhile. */
    fun open(owner: UUID, playerBefore: List<StoredStack?>): ChestSession {
        lateinit var session: ChestSession
        store.atomic { tx ->
            requireNoSession(tx, owner)
            check(tx.documents("removal_pending/$owner/").isEmpty() && tx.documents("market_escrow_pending/$owner/").isEmpty() &&
                tx.documents("ability_transition_pending/$owner/").isEmpty() &&
                tx.documents("delivery_pending/$owner/").values.none { YamlFiles.parse(it).getString("state") in listOf("APPLYING", "REVIEW") }) {
                "An unresolved inventory operation exists"
            }
            val state = ChestDocuments.decode(owner, tx.get(ChestDocuments.key(owner))).second
            if (!state.unlocked) throw ChestUnavailable("상자를 해금하지 않았습니다.")
            session = ChestSession(UUID.randomUUID(), owner, ChestSessionState.OPEN, UUID.randomUUID(), playerBefore.toList(), state.slots)
            tx.put(sessionKey(owner, session.id), encode(session))
        }
        return session
    }

    /** Durable after-images and the close checkpoint id, written before the player data is saved. */
    fun closing(session: ChestSession, playerAfter: List<StoredStack?>, chestAfter: List<StoredStack?>): ChestSession {
        check(session.state == ChestSessionState.OPEN)
        val next = session.copy(state = ChestSessionState.CLOSING, closeCheckpoint = UUID.randomUUID(),
            playerAfter = playerAfter.toList(), chestAfter = chestAfter.toList())
        store.atomic { tx ->
            check(decode(requireNotNull(tx.get(sessionKey(session.owner, session.id)))) == session) { "Chest session changed while closing" }
            tx.put(sessionKey(session.owner, session.id), encode(next))
        }
        return next
    }

    fun complete(session: ChestSession) {
        check(session.state == ChestSessionState.CLOSING)
        val id = UUID.nameUUIDFromBytes("chest-close:${session.owner}:${session.id}".toByteArray(Charsets.UTF_8))
        store.execute(id, "chest-close:${session.owner}:${session.id}") { tx ->
            check(decode(requireNotNull(tx.get(sessionKey(session.owner, session.id)))) == session) { "Chest session changed while completing" }
            finish(tx, session, requireNotNull(session.chestAfter))
            "CLOSED ${session.id}"
        }
        cache.remove(session.owner)
    }

    /** Fresh login only: decide from the saved player data file which side of the window is authoritative. */
    fun recover(owner: UUID, inventory: DeliveryInventory, freshLogin: Boolean): ChestRecovery {
        val sessions = pending(owner)
        if (sessions.isEmpty()) return ChestRecovery.NONE
        check(sessions.size == 1) { "Multiple chest sessions for $owner" }
        val session = sessions.single()
        if (!freshLogin || session.state == ChestSessionState.REVIEW) throw DeliveryRecoveryRequired(owner, session.id)
        val saved = inventory.persistedCheckpoint(); val current = inventory.snapshot()
        return when {
            session.state == ChestSessionState.CLOSING && saved == session.closeCheckpoint && current == session.playerAfter -> {
                complete(session); ChestRecovery.COMPLETED
            }
            saved == session.openCheckpoint && current == session.playerBefore -> { rollback(session); ChestRecovery.ROLLED_BACK }
            // The open checkpoint never reached the file, so the window was never shown.
            session.state == ChestSessionState.OPEN && saved != session.openCheckpoint -> { rollback(session); ChestRecovery.ROLLED_BACK }
            else -> {
                review(session, "Saved player data matches neither the opened nor the closed chest window")
                throw DeliveryRecoveryRequired(owner, session.id)
            }
        }
    }

    /** Operator decision while the owner is offline: keep the chest as opened, or apply the recorded close. */
    fun resolve(request: UUID, actor: String, owner: UUID, id: UUID, applyAfter: Boolean, note: String) {
        require(actor.isNotBlank() && note.isNotBlank())
        store.execute(request, BankService.fingerprint("CHEST_RESOLVE", actor, owner.toString(), id.toString(), applyAfter.toString(), note)) { tx ->
            val previous = decode(requireNotNull(tx.get(sessionKey(owner, id))) { "No unresolved chest session" })
            check(previous.owner == owner && previous.id == id)
            finish(tx, previous, if (applyAfter) requireNotNull(previous.chestAfter) { "The window was never closed; use before" } else previous.chestBefore)
            YamlConfiguration().apply {
                set("actor", actor); set("applyAfter", applyAfter); set("note", note); set("previousEvidence", encode(previous))
            }.saveToString()
        }
        cache.remove(owner)
    }

    private fun rollback(session: ChestSession) {
        val id = UUID.nameUUIDFromBytes("chest-rollback:${session.owner}:${session.id}".toByteArray(Charsets.UTF_8))
        store.execute(id, "chest-rollback:${session.owner}:${session.id}") { tx ->
            check(decode(requireNotNull(tx.get(sessionKey(session.owner, session.id)))) == session) { "Chest session changed while rolling back" }
            finish(tx, session, session.chestBefore)
            "ROLLED_BACK ${session.id}"
        }
        cache.remove(session.owner)
    }

    private fun review(session: ChestSession, reason: String) = store.atomic { tx ->
        check(decode(requireNotNull(tx.get(sessionKey(session.owner, session.id)))) == session)
        tx.put(sessionKey(session.owner, session.id), encode(session.copy(state = ChestSessionState.REVIEW, reason = reason)))
    }

    private fun finish(tx: StateTransaction, session: ChestSession, slots: List<StoredStack?>) {
        val (data, state) = ChestDocuments.decode(session.owner, tx.get(ChestDocuments.key(session.owner)))
        check(state.slots == session.chestBefore) { "Chest changed while its window was open" }
        tx.put(ChestDocuments.key(session.owner), ChestDocuments.encode(session.owner, data, state.copy(slots = slots)))
        tx.put(sessionKey(session.owner, session.id), null)
    }

    private fun requireNoSession(tx: StateTransaction, owner: UUID) =
        check(tx.documents("chest_session/$owner/").isEmpty()) { "A chest window is still being recorded" }

    private fun encode(session: ChestSession) = YamlConfiguration().apply {
        set("schemaVersion", 1); set("id", session.id.toString()); set("owner", session.owner.toString())
        set("state", session.state.name); set("reason", session.reason)
        set("openCheckpoint", session.openCheckpoint.toString()); set("closeCheckpoint", session.closeCheckpoint?.toString())
        set("playerBefore", InventoryImages.encode(session.playerBefore)); set("chestBefore", InventoryImages.encode(session.chestBefore))
        set("playerAfter", session.playerAfter?.let(InventoryImages::encode)); set("chestAfter", session.chestAfter?.let(InventoryImages::encode))
    }.saveToString()

    private fun decode(payload: String): ChestSession {
        val data = YamlFiles.parse(payload); require(data.get("schemaVersion") == 1) { "Unsupported chest session schema" }
        fun image(path: String) = data.getList(path)?.let(InventoryImages::decode)
        return ChestSession(UUID.fromString(data.getString("id")), UUID.fromString(data.getString("owner")),
            ChestSessionState.valueOf(requireNotNull(data.getString("state"))), UUID.fromString(data.getString("openCheckpoint")),
            requireNotNull(image("playerBefore")), requireNotNull(image("chestBefore")),
            data.getString("closeCheckpoint")?.let(UUID::fromString), image("playerAfter"), image("chestAfter"), data.getString("reason"))
    }

    companion object {
        fun sessionKey(owner: UUID, id: UUID) = "chest_session/$owner/$id"
        private fun absorbedKey(entity: UUID) = "chest_absorbed/$entity"
    }
}
