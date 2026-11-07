package com.github.lupi13.limpi.stock

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

class StockRumorTest {
    /** config.yml defaults. */
    private val policy = StockRumorPolicy(15, 55, 15, 40)
    private val codes = listOf("AAAA", "BBBB", "CCCC", "DDDD", "EEEE")

    @Test fun `a rumor comes as often as set, on any stock, within the size set`() {
        val random = Random(3)
        val rumors = List(200_000) { policy.roll(codes, random) }.filterNotNull()
        assertEquals(0.15, rumors.size / 200_000.0, 0.005)
        assertEquals(codes.toSet(), rumors.map { it.code }.toSet())
        assertEquals((15..40).toSet(), rumors.map { it.percent }.toSet())
        assertEquals(0.5, rumors.count { it.good } / rumors.size.toDouble(), 0.01)
        assertEquals(0.55, rumors.count { it.good == it.rises } / rumors.size.toDouble(), 0.01)
        // Good and bad rumors are as common, so overall a stock goes up as often as down.
        assertEquals(0.5, rumors.count { it.rises } / rumors.size.toDouble(), 0.01)
    }

    @Test fun `no chance or no stocks means no rumor, and a full chance always tells one`() {
        val random = Random(5)
        repeat(1_000) { assertNull(StockRumorPolicy(0, 55, 15, 40).roll(codes, random)) }
        assertNull(policy.copy(chancePercent = 100).roll(emptyList(), random))
        repeat(1_000) { assertNotNull(policy.copy(chancePercent = 100).roll(codes, random)) }
        repeat(1_000) { val rumor = StockRumorPolicy(100, 100, 15, 15).roll(codes, random)!!; assertEquals(rumor.good, rumor.rises); assertEquals(15, rumor.percent) }
        repeat(1_000) { val rumor = StockRumorPolicy(100, 0, 15, 40).roll(codes, random)!!; assertNotEquals(rumor.good, rumor.rises) }
    }

    @Test fun `settings out of range fail before the scheduler starts`() {
        for (bad in listOf<() -> StockRumorPolicy>({ StockRumorPolicy(101, 55, 15, 40) }, { StockRumorPolicy(-1, 55, 15, 40) }, { StockRumorPolicy(15, 101, 15, 40) },
            { StockRumorPolicy(15, 55, 0, 40) }, { StockRumorPolicy(15, 55, 41, 40) }, { StockRumorPolicy(15, 55, 15, 1001) }))
            assertThrows(IllegalArgumentException::class.java) { bad() }
    }

    @Test fun `up and down are the same size in log, so rumors do not sink a stock`() {
        val up = StockRumor("AAAA", true, true, 40)
        val down = StockRumor("AAAA", true, false, 40)
        assertEquals(Math.log(1.4), up.jump, 1e-12)
        assertEquals(-up.jump, down.jump, 1e-12)
        assertThrows(IllegalArgumentException::class.java) { StockRumor("AAAA", true, true, 0) }
    }

    @Test fun `a rumor moves the base and the price together, and the price stays there`() {
        val noise = StockPricePolicy(0.0, 0.02)
        val base = StockBasePolicy(0.0, 0.0, 5.0)
        val moved = base.next(0.0, 0.0, StockRumor("AAAA", true, true, 40).jump)
        val price = noise.next(1_000, 1_000, 0.0, moved, 0.0)
        assertEquals(1_400L, price)
        assertEquals(1_400L, noise.next(1_000, price, 0.0, moved), "nothing pulls it back")
        assertEquals(714L, noise.next(1_000, 1_000, 0.0, base.next(0.0, 0.0, StockRumor("AAAA", true, false, 40).jump), 0.0))
    }

    @Test fun `at the end of its range a rumor moves the price no further than the base`() {
        val noise = StockPricePolicy(0.0, 0.02)
        val base = StockBasePolicy(0.0, 0.0, 5.0)
        val top = Math.log(5.0)
        val moved = base.next(top, 0.0, StockRumor("AAAA", true, true, 40).jump)
        assertEquals(top, moved, 1e-12)
        assertEquals(5_000L, noise.next(1_000, 5_000, 0.0, moved, top), "no gap for it to fall back through")
    }

    /** Buying on a good rumor and selling after it, through the default 3% fee each way. */
    private fun followerReturn(truth: Double): Double {
        val gross = (15..40).map { truth * (1 + it / 100.0) + (1 - truth) / (1 + it / 100.0) }.average()
        return gross * 0.97 / 1.03 - 1
    }

    @Test fun `by default following a rumor does not print money, and the truth setting is what keeps it so`() {
        assertEquals(-0.006, followerReturn(0.55), 0.001)
        assertTrue(followerReturn(0.60) > 0.015)
    }

    @Test fun `a rumor is kept with the prices, and a damaged one is no rumor`() {
        val data = YamlConfiguration()
        assertNull(StockRumor.read(data))
        val rumor = StockRumor("AAAA", true, false, 33)
        rumor.write(data)
        val reloaded = YamlConfiguration().apply { loadFromString(data.saveToString()) }
        assertEquals(rumor, StockRumor.read(reloaded))
        StockRumor.clear(reloaded)
        assertNull(StockRumor.read(reloaded)); assertFalse(reloaded.isSet("_meta.rumor"))
        for ((key, bad) in listOf("code" to 3, "good" to "yes", "rises" to 1, "percent" to "33", "percent" to 0)) {
            val damaged = YamlConfiguration().apply { rumor.write(this); set("_meta.rumor.$key", bad) }
            assertNull(StockRumor.read(damaged), key)
        }
    }

    @Test fun `a rumor beside the prices is not taken for a stock`() {
        val data = YamlConfiguration().apply { set("AAAA.currentPrice", 100); StockRumor("AAAA", true, true, 20).write(this) }
        assertEquals(listOf("AAAA"), StockCatalog.allCodes(data))
    }

    @Test fun `every line names the stock once with no particle stuck to it`() {
        for (line in StockRumorText.GOOD + StockRumorText.BAD) {
            assertEquals(2, line.split("{name}").size, line)
            val (before, after) = StockRumorText.split(line)
            assertEquals(line, "$before{name}$after")
            assertTrue(after.startsWith(" "), line)
            assertTrue(line.endsWith("."), line)
        }
        assertEquals(StockRumorText.GOOD.size, StockRumorText.GOOD.toSet().size)
        assertEquals(StockRumorText.BAD.size, StockRumorText.BAD.toSet().size)
        assertTrue((StockRumorText.GOOD intersect StockRumorText.BAD.toSet()).isEmpty())
    }

    @Test fun `every line can come up, from the list the rumor asks for`() {
        val random = Random(9)
        assertEquals(StockRumorText.GOOD.toSet(), List(5_000) { StockRumorText.pick(true, random) }.toSet())
        assertEquals(StockRumorText.BAD.toSet(), List(5_000) { StockRumorText.pick(false, random) }.toSet())
    }
}
