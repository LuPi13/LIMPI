package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.StateStore
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.math.BigInteger
import java.util.Random
import java.util.UUID

object SlotPolicy {
    const val CELLS = 9
    /** Three rows and two diagonals of a 3x3 grid, in reading order. */
    val LINES = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 4, 8), listOf(6, 4, 2))

    fun draw(symbols: List<SlotSymbolRule>, random: Random): List<SlotSymbol> {
        val total = symbols.sumOf { it.weight }
        return List(CELLS) {
            var roll = random.nextInt(total)
            symbols.first { rule -> (roll < rule.weight).also { hit -> if (!hit) roll -= rule.weight } }.symbol
        }
    }

    /** Multiples of the line stake won by one line: all three alike, or the first two alike. */
    fun lineMultiplier(a: SlotSymbol, b: SlotSymbol, c: SlotSymbol, rules: Map<SlotSymbol, SlotSymbolRule>): Int = when {
        a == b && b == c -> rules.getValue(a).triple
        a == b -> rules.getValue(a).pair
        else -> 0
    }

    fun winningLines(grid: List<SlotSymbol>, symbols: List<SlotSymbolRule>): List<Int> {
        val rules = symbols.associateBy { it.symbol }
        return LINES.indices.filter { lineMultiplier(grid[LINES[it][0]], grid[LINES[it][1]], grid[LINES[it][2]], rules) > 0 }
    }

    /** The bet is split evenly over the lines; the total is rounded down once and capped. */
    fun payout(bet: Long, grid: List<SlotSymbol>, symbols: List<SlotSymbolRule>, maxPayout: Long): Long {
        require(grid.size == CELLS)
        val rules = symbols.associateBy { it.symbol }
        val multiples = LINES.sumOf { (a, b, c) -> lineMultiplier(grid[a], grid[b], grid[c], rules).toLong() }
        return (BigInteger.valueOf(bet) * BigInteger.valueOf(multiples) / BigInteger.valueOf(LINES.size.toLong()))
            .min(BigInteger.valueOf(maxPayout)).toLong()
    }

    /** Exact expected return of one line (and so of the whole spin, the lines being equal stakes) before rounding and the cap. */
    fun returnRate(symbols: List<SlotSymbolRule>): Double {
        val total = symbols.sumOf { it.weight }.toDouble()
        return symbols.sumOf { rule ->
            val chance = rule.weight / total
            chance * chance * chance * rule.triple + chance * chance * (1 - chance) * rule.pair
        }
    }
}

data class SlotSpin(val grid: List<SlotSymbol>, val bet: Long, val payout: Long, val replayed: Boolean)

/** Bet, frozen result and pay are one commit: the reels on screen only show what is already decided. */
class SlotService(private val store: StateStore, private val players: PlayerRepository, private val settings: CasinoSettings, private val random: Random) {
    fun spin(request: UUID, owner: UUID, bet: Long): SlotSpin {
        settings.checkBet(bet)
        val grid = SlotPolicy.draw(settings.slotSymbols, random)
        val payout = SlotPolicy.payout(bet, grid, settings.slotSymbols, settings.maxPayout)
        val committed = store.execute(request, BankService.fingerprint("CASINO_SLOT", owner.toString(), bet.toString())) { tx ->
            debit(tx, owner, bet)
            credit(tx, owner, payout)
            YamlConfiguration().apply {
                set("actor", owner.toString()); set("game", "slot"); set("bet", bet); set("payout", payout)
                set("grid", grid.map { it.name })
            }.saveToString()
        }
        players.invalidateChanged(committed)
        val receipt = YamlFiles.parse(committed.entry.receipt)
        return SlotSpin(receipt.getStringList("grid").map(SlotSymbol::valueOf), receipt.getLong("bet"), receipt.getLong("payout"), committed.replayed)
    }
}
