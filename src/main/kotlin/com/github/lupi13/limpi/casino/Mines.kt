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

object MinesPolicy {
    const val CELLS = 25
    val MINE_COUNTS = 1 until CELLS

    fun layout(mines: Int, random: Random): Set<Int> {
        require(mines in MINE_COUNTS)
        return (0 until CELLS).toList().shuffled(random).take(mines).toSet()
    }

    /** The fair odds of surviving [revealed] picks as an exact fraction, so a long round loses nothing to rounding. */
    private fun fairOdds(mines: Int, revealed: Int): Pair<BigInteger, BigInteger> {
        require(mines in MINE_COUNTS && revealed in 0..CELLS - mines)
        var top = BigInteger.ONE; var bottom = BigInteger.ONE
        for (pick in 0 until revealed) {
            top *= BigInteger.valueOf((CELLS - pick).toLong())
            bottom *= BigInteger.valueOf((CELLS - mines - pick).toLong())
        }
        return top to bottom
    }

    /** Rounded down, and never above [maxPayout] whatever the odds. */
    fun payout(bet: Long, mines: Int, revealed: Int, rtpPercent: Int, maxPayout: Long): Long {
        val (top, bottom) = fairOdds(mines, revealed)
        val exact = BigInteger.valueOf(bet) * BigInteger.valueOf(rtpPercent.toLong()) * top / (BigInteger.valueOf(100) * bottom)
        return exact.min(BigInteger.valueOf(maxPayout)).toLong()
    }

    /** For display only: hundredths of the multiplier, rounded down. */
    fun multiplierCenti(mines: Int, revealed: Int, rtpPercent: Int): Long {
        val (top, bottom) = fairOdds(mines, revealed)
        return (BigInteger.valueOf(rtpPercent.toLong()) * top / bottom).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }
}

data class MinesRound(val id: UUID, val owner: UUID, val bet: Long, val mines: Set<Int>, val revealed: Set<Int>, val version: Long) {
    val safeLeft get() = MinesPolicy.CELLS - mines.size - revealed.size
}

sealed interface MinesOutcome {
    val round: MinesRound
    data class Safe(override val round: MinesRound) : MinesOutcome
    data class Exploded(override val round: MinesRound, val cell: Int) : MinesOutcome
    /** [automatic]: every safe cell was opened or the payout limit was reached. */
    data class Cashed(override val round: MinesRound, val payout: Long, val automatic: Boolean) : MinesOutcome
}

class MinesService(private val store: StateStore, private val players: PlayerRepository, private val settings: CasinoSettings, private val random: Random) {
    private fun key(owner: UUID) = CasinoKeys.round(owner, CasinoGame.MINES)

    fun current(owner: UUID): MinesRound? = store.get(key(owner))?.let { decode(key(owner), it) }

    fun start(request: UUID, owner: UUID, bet: Long, mineCount: Int): MinesRound {
        settings.checkBet(bet)
        if (mineCount !in MinesPolicy.MINE_COUNTS) throw CasinoUnavailable("지뢰는 ${MinesPolicy.MINE_COUNTS.first}~${MinesPolicy.MINE_COUNTS.last}개입니다.")
        // Drawn before the transaction so a retry of the same request cannot draw differently.
        val round = MinesRound(request, owner, bet, MinesPolicy.layout(mineCount, random), emptySet(), 0)
        val committed = store.execute(request, BankService.fingerprint("CASINO_MINES_START", owner.toString(), bet.toString(), mineCount.toString())) { tx ->
            if (tx.get(key(owner)) != null) throw CasinoUnavailable("진행 중인 판이 있습니다.")
            debit(tx, owner, bet)
            tx.put(key(owner), encode(round))
            YamlConfiguration().apply { set("actor", owner.toString()); set("game", "mines"); set("bet", bet); set("mines", mineCount) }.saveToString()
        }
        players.invalidateChanged(committed)
        return current(owner) ?: throw CasinoStale()
    }

    fun reveal(request: UUID, owner: UUID, cell: Int, version: Long): MinesOutcome {
        require(cell in 0 until MinesPolicy.CELLS)
        val round = expected(owner, version)
        if (cell in round.revealed) throw CasinoStale()
        if (cell in round.mines) {
            settle(request, round, version, 0, "EXPLODED")
            return MinesOutcome.Exploded(round, cell)
        }
        val next = round.copy(revealed = round.revealed + cell, version = round.version + 1)
        val payout = payout(next)
        if (next.safeLeft == 0 || payout >= settings.maxPayout) {
            settle(request, next, version, payout, "AUTO_CASHED")
            return MinesOutcome.Cashed(next, payout, automatic = true)
        }
        store.atomic { tx -> verify(tx, owner, version); tx.put(key(owner), encode(next)) }
        return MinesOutcome.Safe(next)
    }

    fun cashOut(request: UUID, owner: UUID, version: Long): MinesOutcome.Cashed {
        val round = expected(owner, version)
        if (round.revealed.isEmpty()) throw CasinoUnavailable("칸을 하나 이상 열어야 수령할 수 있습니다.")
        val payout = payout(round)
        settle(request, round, version, payout, "CASHED")
        return MinesOutcome.Cashed(round, payout, automatic = false)
    }

    fun payout(round: MinesRound) = MinesPolicy.payout(round.bet, round.mines.size, round.revealed.size, settings.minesRtpPercent, settings.maxPayout)

    private fun expected(owner: UUID, version: Long): MinesRound {
        val round = current(owner) ?: throw CasinoStale()
        if (round.version != version) throw CasinoStale()
        return round
    }

    private fun verify(tx: StateTransaction, owner: UUID, version: Long) {
        val stored = tx.get(key(owner)) ?: throw CasinoStale()
        if (decode(key(owner), stored).version != version) throw CasinoStale()
    }

    private fun settle(request: UUID, round: MinesRound, storedVersion: Long, payout: Long, outcome: String) {
        val committed = store.execute(request, BankService.fingerprint("CASINO_MINES_SETTLE", round.owner.toString(), round.id.toString(), outcome, payout.toString())) { tx ->
            verify(tx, round.owner, storedVersion)
            credit(tx, round.owner, payout)
            tx.put(key(round.owner), null)
            YamlConfiguration().apply {
                set("actor", round.owner.toString()); set("game", "mines"); set("round", round.id.toString()); set("outcome", outcome)
                set("bet", round.bet); set("mines", round.mines.size); set("revealed", round.revealed.size); set("payout", payout)
            }.saveToString()
        }
        players.invalidateChanged(committed)
    }

    companion object {
        fun encode(round: MinesRound): String = YamlConfiguration().apply {
            set("id", round.id.toString()); set("owner", round.owner.toString()); set("bet", round.bet)
            set("mines", round.mines.sorted()); set("revealed", round.revealed.sorted()); set("version", round.version)
        }.saveToString()

        fun decode(key: String, payload: String): MinesRound = decoding(key) {
            val data = YamlFiles.parse(payload)
            val mines = data.getIntegerList("mines").toSet(); val revealed = data.getIntegerList("revealed").toSet()
            require(data.isLong("bet") || data.isInt("bet")); require(data.getLong("bet") > 0)
            require(mines.size in MinesPolicy.MINE_COUNTS && mines.all { it in 0 until MinesPolicy.CELLS })
            require(revealed.all { it in 0 until MinesPolicy.CELLS && it !in mines })
            MinesRound(UUID.fromString(data.getString("id")), UUID.fromString(data.getString("owner")), data.getLong("bet"), mines, revealed, data.getLong("version"))
        }
    }
}
