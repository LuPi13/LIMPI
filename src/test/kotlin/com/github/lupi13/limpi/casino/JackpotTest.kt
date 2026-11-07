package com.github.lupi13.limpi.casino

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JackpotTest {
    private val settings = CasinoSettings()

    @Test
    fun `both the multiplier and the profit are needed`() {
        assertTrue(JackpotPolicy.reached(JackpotGame.CRASH, 10_000, 500_000, settings))
        assertFalse(JackpotPolicy.reached(JackpotGame.CRASH, 10_000, 499_999, settings), "just under 50 times")
        assertFalse(JackpotPolicy.reached(JackpotGame.CRASH, 100, 100_000, settings), "1000 times, but a profit of 99,900")
        assertFalse(JackpotPolicy.reached(JackpotGame.CRASH, 100_000, 1_000_000, settings), "a large profit at 10 times")
    }

    @Test
    fun `the minimum profit is inclusive`() {
        assertTrue(JackpotPolicy.reached(JackpotGame.MINES, 10_000, 500_000, CasinoSettings(jackpotMinProfit = 490_000)))
        assertFalse(JackpotPolicy.reached(JackpotGame.MINES, 10_000, 500_000, CasinoSettings(jackpotMinProfit = 490_001)))
    }

    @Test
    fun `each game has its own multiplier`() {
        val stake = 10_000L
        assertTrue(JackpotPolicy.reached(JackpotGame.SLOT, stake, stake * 40, settings))
        assertFalse(JackpotPolicy.reached(JackpotGame.SLOT, stake, stake * 40 - 1, settings))
        assertTrue(JackpotPolicy.reached(JackpotGame.MINES, stake, stake * 50, settings))
        assertFalse(JackpotPolicy.reached(JackpotGame.MINES, stake, stake * 50 - 1, settings))
        assertTrue(JackpotPolicy.reached(JackpotGame.CRASH, stake, stake * 50, settings))
        assertFalse(JackpotPolicy.reached(JackpotGame.CRASH, stake, stake * 50 - 1, settings))
        assertTrue(JackpotPolicy.reached(JackpotGame.RACE, stake, stake * 19, settings))
        assertFalse(JackpotPolicy.reached(JackpotGame.RACE, stake, stake * 19 - 1, settings))
    }

    @Test
    fun `the longest shot of the race reaches it, unless the bet was split`() {
        val alone = RacePolicy.payout(10_000, 5, settings.raceRtpPercent, settings.maxPayout)
        assertEquals(194_000, alone)
        assertTrue(JackpotPolicy.reached(JackpotGame.RACE, 10_000, alone, settings))
        assertFalse(JackpotPolicy.reached(JackpotGame.RACE, 20_000, alone, settings), "10,000 more on another sheep")
        val second = RacePolicy.payout(10_000, 9, settings.raceRtpPercent, settings.maxPayout)
        assertFalse(JackpotPolicy.reached(JackpotGame.RACE, 10_000, second, settings))
    }

    @Test
    fun `a capped payout is judged and reported by what was paid`() {
        assertEquals(10_000, JackpotPolicy.multiplierCenti(100_000, 10_000_000))
        assertEquals(5_033, JackpotPolicy.multiplierCenti(3_000, 151_000), "rounded down")
        assertTrue(JackpotPolicy.reached(JackpotGame.CRASH, 100_000, 10_000_000, settings))
    }

    @Test
    fun `the announcement names the player, the game, the multiplier and the amount`() {
        val message = com.github.lupi13.limpi.events.casino.CasinoJackpot.message("Steve", JackpotGame.SLOT, 4_000, net.kyori.adventure.text.Component.text("400000"))
        assertEquals("Steve님이 슬롯머신에서 40.00배의 수익(400000원)을 얻었습니다!", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message))
    }

    @Test
    fun `no stake is no jackpot`() {
        assertFalse(JackpotPolicy.reached(JackpotGame.RACE, 0, 1_000_000, settings))
    }

    @Test
    fun `settings are read and bad values refused`() {
        val config = YamlConfiguration()
        config.set("CasinoJackpotMinProfit", 5_000)
        config.set("CasinoJackpotMinesMultiplier", 10)
        config.set("CasinoJackpotSlotMultiplier", 11)
        config.set("CasinoJackpotCrashMultiplier", 12)
        config.set("CasinoJackpotRaceMultiplier", 13)
        val read = CasinoSettings.read(config)
        assertEquals(listOf(5_000L, 10L, 11L, 12L, 13L), listOf(read.jackpotMinProfit, read.jackpotMinesMultiplier.toLong(), read.jackpotSlotMultiplier.toLong(), read.jackpotCrashMultiplier.toLong(), read.jackpotRaceMultiplier.toLong()))
        config.set("CasinoJackpotMinProfit", 0)
        assertThrows(IllegalArgumentException::class.java) { CasinoSettings.read(config) }
        config.set("CasinoJackpotMinProfit", 5_000)
        config.set("CasinoJackpotRaceMultiplier", 1)
        assertThrows(IllegalArgumentException::class.java) { CasinoSettings.read(config) }
    }
}
