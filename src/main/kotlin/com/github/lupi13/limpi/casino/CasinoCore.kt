package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.CommittedTransaction
import com.github.lupi13.limpi.storage.StateTransaction
import org.bukkit.configuration.ConfigurationSection
import java.util.UUID

/** A refusal the player can read and act on. */
class CasinoUnavailable(message: String) : IllegalStateException(message)

/** The click belonged to a screen that is no longer current: redraw, say nothing. */
class CasinoStale : IllegalStateException("The casino screen was out of date")

/** A stored round cannot be read. It is kept as it is; an administrator decides (see /limpi review). */
class CasinoDamaged(val key: String, cause: Throwable?) : IllegalStateException("Casino record $key cannot be read", cause)

enum class CasinoGame(val code: String) { MINES("mines"), SLOT("slot"), BLACKJACK("blackjack"), CRASH("crash"), RACE("race") }

object CasinoKeys {
    const val ROUND_PREFIX = "casino_round/"
    const val TABLE_PREFIX = "casino_table/"
    fun round(owner: UUID, game: CasinoGame) = "$ROUND_PREFIX$owner/${game.code}"
    fun table(game: CasinoGame, id: UUID) = "$TABLE_PREFIX${game.code}/$id"
}

internal fun <T> decoding(key: String, block: () -> T): T =
    try { block() } catch (failure: Exception) { throw CasinoDamaged(key, failure) }

internal fun debit(tx: StateTransaction, owner: UUID, amount: Long) {
    val account = Accounts.read(tx, owner)
    val before = Accounts.balance(account)
    if (before.units < amount) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
    Accounts.write(tx, owner, account, before - Money(amount))
}

/** Nothing owed leaves the account untouched: a loser's damaged account must not block the other seats. */
internal fun credit(tx: StateTransaction, owner: UUID, amount: Long) {
    if (amount == 0L) return
    val account = Accounts.read(tx, owner)
    val after = try { Accounts.balance(account) + Money(amount) } catch (_: ArithmeticException) { throw BankException(BankFailure.OVERFLOW) }
    Accounts.write(tx, owner, account, after)
}

internal fun PlayerRepository.invalidateChanged(committed: CommittedTransaction) =
    invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })

enum class SlotSymbol { COAL, IRON, GOLD, EMERALD, DIAMOND, NETHERITE }

data class SlotSymbolRule(val symbol: SlotSymbol, val weight: Int, val triple: Int, val pair: Int) {
    init { require(weight > 0 && triple >= 0 && pair >= 0) { "CasinoSlotSymbols.$symbol needs a positive weight and non-negative payouts" } }
}

const val RACE_SHEEP = 6

data class CasinoSettings(
    val minBet: Long = 100, val maxBet: Long = 100_000, val maxPayout: Long = 10_000_000,
    val minesRtpPercent: Int = 97, val crashRtpPercent: Int = 97, val crashGrowth: Double = 0.08, val crashMaxMultiplier: Int = 1000,
    val crashBettingSeconds: Int = 15, val raceRtpPercent: Int = 97, val raceOddsPercent: List<Int> = listOf(30, 25, 18, 13, 9, 5),
    val raceBettingSeconds: Int = 30, val blackjackBettingSeconds: Int = 15, val blackjackTurnSeconds: Int = 20,
    val slotSymbols: List<SlotSymbolRule> = listOf(
        SlotSymbolRule(SlotSymbol.COAL, 34, 6, 1), SlotSymbolRule(SlotSymbol.IRON, 26, 11, 1), SlotSymbolRule(SlotSymbol.GOLD, 18, 25, 1),
        SlotSymbolRule(SlotSymbol.EMERALD, 12, 60, 2), SlotSymbolRule(SlotSymbol.DIAMOND, 7, 200, 5), SlotSymbolRule(SlotSymbol.NETHERITE, 3, 500, 10)),
) {
    init {
        require(minBet >= 1) { "CasinoMinBet must be at least 1" }
        require(maxBet >= minBet) { "CasinoMaxBet must not be below CasinoMinBet" }
        require(maxPayout >= maxBet) { "CasinoMaxPayout must not be below CasinoMaxBet" }
        for ((key, value) in listOf("CasinoMinesRtpPercent" to minesRtpPercent, "CasinoCrashRtpPercent" to crashRtpPercent, "CasinoRaceRtpPercent" to raceRtpPercent)) {
            require(value in 1..100) { "$key must be 1 to 100" }
        }
        require(crashGrowth > 0.0 && crashGrowth.isFinite()) { "CasinoCrashGrowth must be positive" }
        require(crashMaxMultiplier >= 2) { "CasinoCrashMaxMultiplier must be at least 2" }
        for ((key, value) in listOf("CasinoCrashBettingSeconds" to crashBettingSeconds, "CasinoRaceBettingSeconds" to raceBettingSeconds,
            "CasinoBlackjackBettingSeconds" to blackjackBettingSeconds, "CasinoBlackjackTurnSeconds" to blackjackTurnSeconds)) {
            require(value >= 1) { "$key must be at least 1" }
        }
        require(raceOddsPercent.size == RACE_SHEEP && raceOddsPercent.all { it > 0 } && raceOddsPercent.sum() == 100) {
            "CasinoRaceOddsPercent must be $RACE_SHEEP positive numbers adding up to 100"
        }
        require(slotSymbols.map { it.symbol }.toSet() == SlotSymbol.entries.toSet() && slotSymbols.size == SlotSymbol.entries.size) {
            "CasinoSlotSymbols must define every symbol once"
        }
    }

    /** Throws [CasinoUnavailable] with the allowed range; the screens show it as it is. */
    fun checkBet(amount: Long) {
        if (amount !in minBet..maxBet) throw CasinoUnavailable("베팅 금액은 $minBet~${maxBet}원입니다.")
    }

    companion object {
        /** Refuses invalid values with the name of the key, so the casino stays off instead of paying out a typo. */
        fun read(config: ConfigurationSection): CasinoSettings {
            val fallback = CasinoSettings()
            fun integer(key: String, default: Int): Int {
                val value = config.get(key) ?: return default
                require(value is Int) { "$key must be a whole number" }
                return value
            }
            fun long(key: String, default: Long): Long = when (val value = config.get(key)) {
                null -> default
                is Int, is Long -> (value as Number).toLong()
                else -> throw IllegalArgumentException("$key must be a whole number")
            }
            val symbols = config.getConfigurationSection("CasinoSlotSymbols")?.let { section ->
                section.getKeys(false).map { name ->
                    val symbol = requireNotNull(SlotSymbol.entries.firstOrNull { it.name == name }) { "Unknown slot symbol $name" }
                    val rule = requireNotNull(section.getConfigurationSection(name)) { "CasinoSlotSymbols.$name must be a section" }
                    SlotSymbolRule(symbol, rule.getInt("weight"), rule.getInt("triple"), rule.getInt("pair"))
                }
            } ?: fallback.slotSymbols
            val odds = if (config.contains("CasinoRaceOddsPercent")) config.getIntegerList("CasinoRaceOddsPercent") else fallback.raceOddsPercent
            return CasinoSettings(
                long("CasinoMinBet", fallback.minBet), long("CasinoMaxBet", fallback.maxBet), long("CasinoMaxPayout", fallback.maxPayout),
                integer("CasinoMinesRtpPercent", fallback.minesRtpPercent), integer("CasinoCrashRtpPercent", fallback.crashRtpPercent),
                config.getDouble("CasinoCrashGrowth", fallback.crashGrowth), integer("CasinoCrashMaxMultiplier", fallback.crashMaxMultiplier),
                integer("CasinoCrashBettingSeconds", fallback.crashBettingSeconds), integer("CasinoRaceRtpPercent", fallback.raceRtpPercent), odds,
                integer("CasinoRaceBettingSeconds", fallback.raceBettingSeconds), integer("CasinoBlackjackBettingSeconds", fallback.blackjackBettingSeconds),
                integer("CasinoBlackjackTurnSeconds", fallback.blackjackTurnSeconds), symbols,
            )
        }
    }
}

/** The one bet control: left/right step by the leading digit (100, 200 ... 1000, 2000 ...), shift by ten times. */
object BetSelector {
    private fun unit(amount: Long): Long { var unit = 1L; while (unit * 10 <= amount) unit *= 10; return unit }
    fun up(amount: Long, settings: CasinoSettings): Long = (amount + unit(amount)).coerceIn(settings.minBet, settings.maxBet)
    fun down(amount: Long, settings: CasinoSettings): Long = (amount - unit((amount - 1).coerceAtLeast(1))).coerceIn(settings.minBet, settings.maxBet)
    fun tenTimes(amount: Long, settings: CasinoSettings): Long = (if (amount > settings.maxBet / 10) settings.maxBet else amount * 10).coerceIn(settings.minBet, settings.maxBet)
    fun tenth(amount: Long, settings: CasinoSettings): Long = (amount / 10).coerceIn(settings.minBet, settings.maxBet)
}
