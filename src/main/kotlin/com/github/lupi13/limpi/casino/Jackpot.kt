package com.github.lupi13.limpi.casino

import java.math.BigInteger

/** Blackjack is left out: it pays at most 2.5 times the stake, nothing to tell the whole server about. */
enum class JackpotGame(val label: String) { MINES("마인즈"), SLOT("슬롯머신"), CRASH("크래시"), RACE("양 경주") }

object JackpotPolicy {
    /** What was actually paid per what was actually staked, so split race bets and capped payouts report the truth. Rounded down. */
    fun multiplierCenti(stake: Long, payout: Long): Long {
        require(stake > 0 && payout >= 0)
        return (BigInteger.valueOf(payout) * BigInteger.valueOf(100) / BigInteger.valueOf(stake)).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }

    /** Needs both: a multiplier alone would announce small bets, a profit alone the ordinary wins of large ones. */
    fun reached(game: JackpotGame, stake: Long, payout: Long, settings: CasinoSettings): Boolean {
        if (stake <= 0 || payout - stake < settings.jackpotMinProfit) return false
        val needed = when (game) {
            JackpotGame.MINES -> settings.jackpotMinesMultiplier
            JackpotGame.SLOT -> settings.jackpotSlotMultiplier
            JackpotGame.CRASH -> settings.jackpotCrashMultiplier
            JackpotGame.RACE -> settings.jackpotRaceMultiplier
        }
        return BigInteger.valueOf(payout) >= BigInteger.valueOf(stake) * BigInteger.valueOf(needed.toLong())
    }
}
