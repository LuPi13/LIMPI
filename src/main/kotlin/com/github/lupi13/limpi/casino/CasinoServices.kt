package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.StateStore
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.security.SecureRandom
import java.util.Random
import java.util.UUID

/** A stored casino round that cannot be read, with the player it belongs to when the key names one. */
data class DamagedCasinoRecord(val key: String, val owner: UUID?, val payload: String)

class CasinoServices(private val store: StateStore, players: PlayerRepository, val settings: CasinoSettings, random: Random = SecureRandom()) {
    val mines = MinesService(store, players, settings, random)
    val slot = SlotService(store, players, settings, random)
    val blackjack = BlackjackService(store, players, settings, random)
    val crash = CrashService(store, players, settings, random)

    /** Server start: shared rounds cannot wait for anyone, so they finish now. Mines rounds simply stay until their player returns. */
    fun recover(log: (String) -> Unit) {
        blackjack.recover(log)
        crash.recover(log)
    }

    /** For administrators: what this player has at stake right now. */
    fun describe(owner: UUID): List<String> {
        val lines = mutableListOf<String>()
        runCatching { mines.current(owner) }.fold(
            { round -> round?.let { lines += "마인즈 베팅 ${it.bet}원, 지뢰 ${it.mines.size}개, 연 칸 ${it.revealed.size}개" } },
            { lines += "마인즈 기록 손상 (/limpi review)" })
        blackjack.tables().filter { it.seat(owner) != null }.forEach { lines += "블랙잭 베팅 ${it.seat(owner)!!.bet}원, ${it.phase}, 카드 ${it.seat(owner)!!.cards.size}장" }
        crash.rounds().filter { owner in it.uncashed }.forEach { lines += "크래시 베팅 ${it.bets[owner]}원, ${it.phase}" }
        return lines
    }

    /** Every round document that does not decode. Kept untouched until [discard]. */
    fun damaged(): List<DamagedCasinoRecord> {
        val rounds = store.documents(CasinoKeys.ROUND_PREFIX).filterNot { (key, payload) -> runCatching { decodeRound(key, payload) }.isSuccess }
            .map { (key, payload) -> DamagedCasinoRecord(key, runCatching { UUID.fromString(key.split('/')[1]) }.getOrNull(), payload) }
        val tables = store.documents(CasinoKeys.TABLE_PREFIX).filterNot { (key, payload) -> runCatching { decodeTable(key, payload) }.isSuccess }
            .map { (key, payload) -> DamagedCasinoRecord(key, null, payload) }
        return rounds + tables
    }

    private fun decodeRound(key: String, payload: String) {
        when (key.substringAfterLast('/')) {
            CasinoGame.MINES.code -> MinesService.decode(key, payload)
            else -> throw IllegalArgumentException("Unknown casino round $key")
        }
    }

    private fun decodeTable(key: String, payload: String) {
        when (key.removePrefix(CasinoKeys.TABLE_PREFIX).substringBefore('/')) {
            CasinoGame.BLACKJACK.code -> BlackjackService.decode(key, payload)
            CasinoGame.CRASH.code -> CrashService.decode(key, payload)
            else -> throw IllegalArgumentException("Unknown casino table $key")
        }
    }

    /** The unreadable payload stays in the ledger; any stake it held is the administrator's call (/financial add). */
    fun discard(request: UUID, actor: String, key: String, note: String) {
        val record = damaged().firstOrNull { it.key == key } ?: throw CasinoUnavailable("읽을 수 없는 카지노 기록이 아닙니다: $key")
        store.execute(request, BankService.fingerprint("CASINO_DISCARD", actor, key, note)) { tx ->
            tx.put(key, null)
            YamlConfiguration().apply { set("actor", actor); set("key", key); set("note", note); set("payload", record.payload) }.saveToString()
        }
    }
}
