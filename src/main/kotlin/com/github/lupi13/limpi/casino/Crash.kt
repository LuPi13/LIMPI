package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.StateStore
import com.github.lupi13.limpi.storage.StateTransaction
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.math.BigInteger
import java.util.Random
import java.util.UUID

object CrashPolicy {
    /**
     * Hundredths of the multiplier where the round ends, frozen at the start. A roll u in (0, 1] gives rtp/u, so the chance
     * of still being alive at multiplier m is rtp/m and cashing out at any m returns rtp. Rounding up keeps that exact, since
     * cashing out needs m below the end. Up to 1.00 ends the round at once.
     */
    fun crashPointCenti(random: Random, rtpPercent: Int, maxMultiplier: Int): Long {
        val roll = 1.0 - random.nextDouble() // (0, 1]
        val centi = StrictMath.ceil(rtpPercent / roll).coerceAtMost(maxMultiplier * 100.0).toLong()
        return centi.coerceIn(100, maxMultiplier * 100L)
    }

    /** Multiplier on screen and for cashing out, after [ticks] of the running round. */
    fun multiplierCenti(ticks: Long, growthPerSecond: Double): Long =
        StrictMath.floor(100 * StrictMath.exp(growthPerSecond * ticks / 20.0)).toLong()

    fun payout(bet: Long, multiplierCenti: Long, maxPayout: Long): Long =
        (BigInteger.valueOf(bet) * BigInteger.valueOf(multiplierCenti) / BigInteger.valueOf(100)).min(BigInteger.valueOf(maxPayout)).toLong()
}

enum class CrashPhase { BETTING, RUNNING }
data class CrashRound(val id: UUID, val phase: CrashPhase, val bets: Map<UUID, Long>, val cashed: Map<UUID, Long>, val crashCenti: Long, val version: Long) {
    val uncashed: Map<UUID, Long> get() = bets.filterKeys { it !in cashed }
}
data class CrashCash(val payout: Long, val multiplierCenti: Long, val replayed: Boolean)

class CrashService(private val store: StateStore, private val players: PlayerRepository, private val settings: CasinoSettings, private val random: Random) {
    private fun key(id: UUID) = CasinoKeys.table(CasinoGame.CRASH, id)
    private val prefix = CasinoKeys.TABLE_PREFIX + CasinoGame.CRASH.code + "/"
    fun round(id: UUID): CrashRound? = store.get(key(id))?.let { decode(key(id), it) }
    fun rounds(): List<CrashRound> = store.documents(prefix).mapNotNull { (key, payload) -> runCatching { decode(key, payload) }.getOrNull() }

    private fun read(tx: StateTransaction, id: UUID) = tx.get(key(id))?.let { decode(key(id), it) } ?: throw CasinoStale()

    fun bet(request: UUID, owner: UUID, id: UUID, amount: Long): CrashRound {
        settings.checkBet(amount)
        val committed = store.execute(request, BankService.fingerprint("CASINO_CRASH_BET", owner.toString(), id.toString(), amount.toString())) { tx ->
            val round = tx.get(key(id))?.let { decode(key(id), it) } ?: CrashRound(id, CrashPhase.BETTING, emptyMap(), emptyMap(), 0, 0)
            if (round.phase != CrashPhase.BETTING) throw CasinoUnavailable("이미 시작한 판입니다. 다음 판에 베팅하세요.")
            if (owner in round.bets) throw CasinoUnavailable("이미 베팅했습니다.")
            debit(tx, owner, amount)
            tx.put(key(id), encode(round.copy(bets = round.bets + (owner to amount), version = round.version + 1)))
            YamlConfiguration().apply { set("actor", owner.toString()); set("game", "crash"); set("round", id.toString()); set("bet", amount) }.saveToString()
        }
        players.invalidateChanged(committed)
        return round(id) ?: throw CasinoStale()
    }

    /** The end of the round is frozen here, before anyone can see a multiplier. */
    fun start(id: UUID): CrashRound {
        val crash = CrashPolicy.crashPointCenti(random, settings.crashRtpPercent, settings.crashMaxMultiplier)
        lateinit var next: CrashRound
        store.atomic { tx ->
            val round = read(tx, id)
            if (round.phase != CrashPhase.BETTING) throw CasinoStale()
            next = round.copy(phase = CrashPhase.RUNNING, crashCenti = crash, version = round.version + 1)
            tx.put(key(id), encode(next))
        }
        return next
    }

    /** Pays [multiplierCenti] as shown by the server's own tick. Past the frozen end there is nothing to take. */
    fun cashOut(request: UUID, owner: UUID, id: UUID, multiplierCenti: Long): CrashCash {
        val committed = store.execute(request, BankService.fingerprint("CASINO_CRASH_CASH", owner.toString(), id.toString(), multiplierCenti.toString())) { tx ->
            val round = read(tx, id)
            val bet = round.bets[owner] ?: throw CasinoStale()
            if (round.phase != CrashPhase.RUNNING || owner in round.cashed || multiplierCenti >= round.crashCenti) throw CasinoStale()
            val payout = CrashPolicy.payout(bet, multiplierCenti, settings.maxPayout)
            credit(tx, owner, payout)
            tx.put(key(id), encode(round.copy(cashed = round.cashed + (owner to multiplierCenti), version = round.version + 1)))
            YamlConfiguration().apply { set("actor", owner.toString()); set("game", "crash"); set("round", id.toString()); set("bet", bet); set("multiplier", multiplierCenti); set("payout", payout) }.saveToString()
        }
        players.invalidateChanged(committed)
        val receipt = YamlFiles.parse(committed.entry.receipt)
        return CrashCash(receipt.getLong("payout"), receipt.getLong("multiplier"), committed.replayed)
    }

    /** The round reached its end: whoever has not cashed out has lost. */
    fun finish(request: UUID, id: UUID): CrashRound {
        lateinit var finished: CrashRound
        val committed = store.execute(request, BankService.fingerprint("CASINO_CRASH_FINISH", id.toString())) { tx ->
            val round = read(tx, id)
            if (round.phase != CrashPhase.RUNNING) throw CasinoStale()
            finished = round
            tx.put(key(id), null)
            YamlConfiguration().apply { set("game", "crash"); set("round", id.toString()); set("crash", round.crashCenti); set("lost", round.uncashed.values.sum()) }.saveToString()
        }
        players.invalidateChanged(committed)
        return finished
    }

    /** Server start: nobody could decide during the restart, so bets not yet cashed out go back. Money already taken stays. */
    fun recover(log: (String) -> Unit) {
        for (round in rounds()) {
            try {
                val committed = store.execute(UUID.randomUUID(), BankService.fingerprint("CASINO_CRASH_REFUND", round.id.toString())) { tx ->
                    round.uncashed.forEach { (owner, bet) -> credit(tx, owner, bet) }
                    tx.put(key(round.id), null)
                    YamlConfiguration().apply { set("game", "crash"); set("round", round.id.toString()); set("refund", round.uncashed.values.sum()) }.saveToString()
                }
                players.invalidateChanged(committed)
                log("Crash round ${round.id} was refunded after the restart")
            } catch (failure: Exception) {
                log("Crash round ${round.id} could not be refunded, it was kept: $failure")
            }
        }
    }

    companion object {
        private fun money(section: org.bukkit.configuration.ConfigurationSection?) = section?.getKeys(false)?.associate { UUID.fromString(it) to section.getLong(it) } ?: emptyMap()

        fun encode(round: CrashRound): String = YamlConfiguration().apply {
            set("id", round.id.toString()); set("phase", round.phase.name); set("crash", round.crashCenti); set("version", round.version)
            round.bets.forEach { (owner, bet) -> set("bets.$owner", bet) }
            round.cashed.forEach { (owner, multiplier) -> set("cashed.$owner", multiplier) }
        }.saveToString()

        fun decode(key: String, payload: String): CrashRound = decoding(key) {
            val data = YamlFiles.parse(payload)
            val bets = money(data.getConfigurationSection("bets")); val cashed = money(data.getConfigurationSection("cashed"))
            require(bets.values.all { it > 0 } && bets.keys.containsAll(cashed.keys))
            CrashRound(UUID.fromString(data.getString("id")), CrashPhase.valueOf(requireNotNull(data.getString("phase"))), bets, cashed, data.getLong("crash"), data.getLong("version"))
        }
    }
}
