package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.casino.CasinoSettings
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CasinoGuideTest {
    private fun plain(line: Component) = PlainTextComponentSerializer.plainText().serialize(line)
    private fun text(code: String, settings: CasinoSettings = CasinoSettings()) = CasinoGuide.lines(code, "이름", settings).map(::plain)
    private fun String.assertContains(part: String) = assertTrue(contains(part), "\"$part\" is not in: $this")

    @Test
    fun `every game has a title and ends with the limits`() {
        for (code in listOf("mines", "slot", "blackjack", "crash", "race")) {
            val lines = text(code)
            assertEquals("[ 이름 ]", lines.first())
            assertEquals("베팅 100~100,000원 / 한 판 최대 지급 10,000,000원", lines.last())
            assertTrue(lines.size in 7..11, "$code has ${lines.size} lines")
            lines.forEach { line -> assertFalse(line.contains('{') || line.contains('<'), "markup left in: $line") }
        }
    }

    @Test
    fun `an unknown game is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CasinoGuide.lines("roulette", "이름", CasinoSettings()) }
    }

    @Test
    fun `the default numbers match the rules`() {
        val mines = text("mines").joinToString("\n")
        mines.assertContains("지뢰 수(1~24개)")
        mines.assertContains("지뢰 1개 1.01배, 3개 1.10배, 24개 24.25배")
        val slot = text("slot").joinToString("\n")
        slot.assertContains("3개 배수: 석탄 1.2배 ~ 네더라이트 100배")
        slot.assertContains("2개 배수: 석탄 0.2배 ~ 네더라이트 2배")
        val blackjack = text("blackjack").joinToString("\n")
        blackjack.assertContains("최대 4명")
        blackjack.assertContains("첫 베팅 후 15초")
        blackjack.assertContains("20초 안에")
        val crash = text("crash").joinToString("\n")
        crash.assertContains("약 9초에 2배")
        crash.assertContains("약 3%는")
        val race = text("race").joinToString("\n")
        race.assertContains("확률 30%면 3.23배, 5%면 19.40배")
        race.assertContains("합계 100,000원")
    }

    @Test
    fun `the numbers follow the settings`() {
        val settings = CasinoSettings(minBet = 500, maxBet = 2_000_000, maxPayout = 50_000_000, minesRtpPercent = 90, crashRtpPercent = 95, crashGrowth = 0.2,
            crashBettingSeconds = 7, raceRtpPercent = 80, raceOddsPercent = listOf(50, 20, 10, 10, 5, 5), raceBettingSeconds = 11,
            blackjackBettingSeconds = 8, blackjackTurnSeconds = 12)
        assertEquals("베팅 500~2,000,000원 / 한 판 최대 지급 50,000,000원", text("mines", settings).last())
        text("mines", settings).joinToString("\n").assertContains("지뢰 1개 0.93배") // 90 x 25 / 24 = 93.75, rounded down like the screen
        val crash = text("crash", settings).joinToString("\n")
        crash.assertContains("첫 베팅 후 7초")
        crash.assertContains("약 3초에 2배")
        crash.assertContains("약 5%는")
        val race = text("race", settings).joinToString("\n")
        race.assertContains("확률 50%면 1.60배, 5%면 16.00배")
        race.assertContains("첫 베팅 후 11초")
        val blackjack = text("blackjack", settings).joinToString("\n")
        blackjack.assertContains("첫 베팅 후 8초")
        blackjack.assertContains("12초 안에")
    }

    @Test
    fun `no instant crash is promised at a full return`() {
        val crash = text("crash", CasinoSettings(crashRtpPercent = 100)).joinToString("\n")
        assertFalse(crash.contains("시작하자마자"))
    }

    @Test
    fun `numbers are green, buttons yellow and warnings red`() {
        val lines = CasinoGuide.lines("crash", "크래시", CasinoSettings())
        assertEquals(NamedTextColor.GOLD, lines.first().color())
        val parts = lines[1].children().map { it as TextComponent }
        assertEquals(NamedTextColor.GREEN, parts.first { it.content() == "15" }.color())
        val button = lines[3].children().map { it as TextComponent }.first { it.content() == "수령" }
        assertEquals(NamedTextColor.YELLOW, button.color())
        assertEquals(NamedTextColor.WHITE, lines[3].color())
        assertEquals(NamedTextColor.RED, lines.first { plain(it).startsWith("주의:") }.color())
    }
}
