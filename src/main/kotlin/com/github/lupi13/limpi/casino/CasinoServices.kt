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

    /** For administrators: what this player has at stake right now. */
    fun describe(owner: UUID): List<String> {
        val lines = mutableListOf<String>()
        runCatching { mines.current(owner) }.fold(
            { round -> round?.let { lines += "마인즈 베팅 ${it.bet}원, 지뢰 ${it.mines.size}개, 연 칸 ${it.revealed.size}개" } },
            { lines += "마인즈 기록 손상 (/limpi review)" })
        return lines
    }

    /** Every round document that does not decode. Kept untouched until [discard]. */
    fun damaged(): List<DamagedCasinoRecord> {
        val rounds = store.documents(CasinoKeys.ROUND_PREFIX).filterNot { (key, payload) -> runCatching { decodeRound(key, payload) }.isSuccess }
            .map { (key, payload) -> DamagedCasinoRecord(key, runCatching { UUID.fromString(key.split('/')[1]) }.getOrNull(), payload) }
        return rounds
    }

    private fun decodeRound(key: String, payload: String) {
        when (key.substringAfterLast('/')) {
            CasinoGame.MINES.code -> MinesService.decode(key, payload)
            else -> throw IllegalArgumentException("Unknown casino round $key")
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
