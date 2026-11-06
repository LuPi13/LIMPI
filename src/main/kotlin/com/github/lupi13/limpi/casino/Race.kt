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

object RacePolicy {
    const val LENGTH = 6
    const val FRAMES = 30

    fun assignOdds(odds: List<Int>, random: Random): List<Int> = odds.shuffled(random)

    fun winner(odds: List<Int>, random: Random): Int {
        var roll = random.nextInt(odds.sum())
        return odds.indices.first { sheep -> (roll < odds[sheep]).also { hit -> if (!hit) roll -= odds[sheep] } }
    }

    /** The house pays fixed odds, so no money passes between players and the race is no way around the transfer fee. */
    fun payout(bet: Long, odds: Int, rtpPercent: Int, maxPayout: Long): Long =
        (BigInteger.valueOf(bet) * BigInteger.valueOf(rtpPercent.toLong()) / BigInteger.valueOf(odds.toLong())).min(BigInteger.valueOf(maxPayout)).toLong()

    /** Multiplier in hundredths, for the screen. */
    fun multiplierCenti(odds: Int, rtpPercent: Int): Long = rtpPercent * 100L / odds

    /**
     * Positions of every sheep per frame, a pure show of the frozen [winner]: only the winner reaches the end, on the last
     * frame, and the same seed draws the same race for every viewer.
     */
    fun path(winner: Int, seed: Long): List<List<Int>> {
        val random = Random(seed)
        val steps = List(RACE_SHEEP) { sheep ->
            if (sheep == winner) (0 until FRAMES - 1).shuffled(random).take(LENGTH - 1).toSet() + (FRAMES - 1)
            else (0 until FRAMES).shuffled(random).take(LENGTH - 3 + random.nextInt(3)).toSet()
        }
        val position = IntArray(RACE_SHEEP)
        return List(FRAMES) { frame ->
            steps.forEachIndexed { sheep, moves -> if (frame in moves) position[sheep]++ }
            position.toList()
        }
    }
}

enum class RacePhase { BETTING, RUNNING }
/** [bets]: per player, per sheep. [winner] and [seed] are frozen at the start. */
data class RaceRound(val id: UUID, val phase: RacePhase, val odds: List<Int>, val bets: Map<UUID, Map<Int, Long>>, val winner: Int, val seed: Long, val version: Long) {
    fun total(owner: UUID) = bets[owner]?.values?.sum() ?: 0L
}
data class RaceResult(val owner: UUID, val staked: Long, val payout: Long)
data class RaceSettlement(val winner: Int, val results: List<RaceResult>, val replayed: Boolean)

class RaceService(private val store: StateStore, private val players: PlayerRepository, private val settings: CasinoSettings, private val random: Random) {
    private fun key(id: UUID) = CasinoKeys.table(CasinoGame.RACE, id)
    private val prefix = CasinoKeys.TABLE_PREFIX + CasinoGame.RACE.code + "/"
    fun round(id: UUID): RaceRound? = store.get(key(id))?.let { decode(key(id), it) }
    fun rounds(): List<RaceRound> = store.documents(prefix).mapNotNull { (key, payload) -> runCatching { decode(key, payload) }.getOrNull() }
    fun drawOdds(): List<Int> = RacePolicy.assignOdds(settings.raceOddsPercent, random)

    private fun read(tx: StateTransaction, id: UUID) = tx.get(key(id))?.let { decode(key(id), it) } ?: throw CasinoStale()

    /** [odds] are the ones on screen and only matter for the first bet, which creates the round. */
    fun bet(request: UUID, owner: UUID, id: UUID, sheep: Int, amount: Long, odds: List<Int>): RaceRound {
        settings.checkBet(amount)
        require(sheep in 0 until RACE_SHEEP)
        val committed = store.execute(request, BankService.fingerprint("CASINO_RACE_BET", owner.toString(), id.toString(), sheep.toString(), amount.toString())) { tx ->
            val round = tx.get(key(id))?.let { decode(key(id), it) } ?: RaceRound(id, RacePhase.BETTING, odds, emptyMap(), -1, 0, 0)
            if (round.phase != RacePhase.BETTING) throw CasinoUnavailable("이미 출발했습니다. 다음 경주에 베팅하세요.")
            if (round.total(owner) + amount > settings.maxBet) throw CasinoUnavailable("한 경주에 최대 ${settings.maxBet}원까지 베팅할 수 있습니다.")
            debit(tx, owner, amount)
            val mine = round.bets[owner].orEmpty()
            tx.put(key(id), encode(round.copy(bets = round.bets + (owner to mine + (sheep to (mine[sheep] ?: 0L) + amount)), version = round.version + 1)))
            YamlConfiguration().apply { set("actor", owner.toString()); set("game", "race"); set("round", id.toString()); set("sheep", sheep); set("bet", amount) }.saveToString()
        }
        players.invalidateChanged(committed)
        return round(id) ?: throw CasinoStale()
    }

    fun start(id: UUID): RaceRound {
        val seed = random.nextLong()
        lateinit var next: RaceRound
        store.atomic { tx ->
            val round = read(tx, id)
            if (round.phase != RacePhase.BETTING) throw CasinoStale()
            next = round.copy(phase = RacePhase.RUNNING, winner = RacePolicy.winner(round.odds, random), seed = seed, version = round.version + 1)
            tx.put(key(id), encode(next))
        }
        return next
    }

    /** Paid when the race ends, not at the start: a balance must not give away the winner while everyone is watching. */
    fun finish(request: UUID, id: UUID): RaceSettlement {
        val committed = store.execute(request, BankService.fingerprint("CASINO_RACE_FINISH", id.toString())) { tx ->
            val round = read(tx, id)
            if (round.phase != RacePhase.RUNNING) throw CasinoStale()
            val results = round.bets.map { (owner, bets) ->
                val staked = bets[round.winner] ?: 0L
                RaceResult(owner, bets.values.sum(), RacePolicy.payout(staked, round.odds[round.winner], settings.raceRtpPercent, settings.maxPayout))
            }
            results.forEach { credit(tx, it.owner, it.payout) }
            tx.put(key(id), null)
            YamlConfiguration().apply {
                set("game", "race"); set("round", id.toString()); set("winner", round.winner)
                results.forEach { set("results.${it.owner}.staked", it.staked); set("results.${it.owner}.payout", it.payout) }
            }.saveToString()
        }
        players.invalidateChanged(committed)
        val receipt = YamlFiles.parse(committed.entry.receipt)
        val section = receipt.getConfigurationSection("results")
        return RaceSettlement(receipt.getInt("winner"), section?.getKeys(false)?.map {
            RaceResult(UUID.fromString(it), section.getLong("$it.staked"), section.getLong("$it.payout"))
        }.orEmpty(), committed.replayed)
    }

    /** Server start: a race that left the gate has its winner and pays; one still taking bets gives them back. */
    fun recover(log: (String) -> Unit) {
        for (round in rounds()) {
            try {
                if (round.phase == RacePhase.RUNNING) finish(UUID.randomUUID(), round.id)
                else {
                    val committed = store.execute(UUID.randomUUID(), BankService.fingerprint("CASINO_RACE_REFUND", round.id.toString())) { tx ->
                        round.bets.forEach { (owner, bets) -> credit(tx, owner, bets.values.sum()) }
                        tx.put(key(round.id), null)
                        YamlConfiguration().apply { set("game", "race"); set("round", round.id.toString()); set("refund", round.bets.values.sumOf { it.values.sum() }) }.saveToString()
                    }
                    players.invalidateChanged(committed)
                }
                log("Race ${round.id} was finished after the restart")
            } catch (failure: Exception) {
                log("Race ${round.id} could not be finished, it was kept: $failure")
            }
        }
    }

    companion object {
        fun encode(round: RaceRound): String = YamlConfiguration().apply {
            set("id", round.id.toString()); set("phase", round.phase.name); set("odds", round.odds); set("winner", round.winner); set("seed", round.seed); set("version", round.version)
            round.bets.forEach { (owner, bets) -> bets.forEach { (sheep, bet) -> set("bets.$owner.$sheep", bet) } }
        }.saveToString()

        fun decode(key: String, payload: String): RaceRound = decoding(key) {
            val data = YamlFiles.parse(payload)
            val odds = data.getIntegerList("odds")
            require(odds.size == RACE_SHEEP && odds.all { it > 0 })
            val phase = RacePhase.valueOf(requireNotNull(data.getString("phase")))
            val winner = data.getInt("winner", -1)
            require(if (phase == RacePhase.RUNNING) winner in 0 until RACE_SHEEP else winner == -1)
            val section = data.getConfigurationSection("bets")
            val bets = section?.getKeys(false)?.associate { owner ->
                val mine = requireNotNull(section.getConfigurationSection(owner))
                UUID.fromString(owner) to mine.getKeys(false).associate { sheep ->
                    val index = sheep.toInt(); require(index in 0 until RACE_SHEEP && mine.getLong(sheep) > 0)
                    index to mine.getLong(sheep)
                }
            }.orEmpty()
            RaceRound(UUID.fromString(data.getString("id")), phase, odds, bets, winner, data.getLong("seed"), data.getLong("version"))
        }
    }
}
