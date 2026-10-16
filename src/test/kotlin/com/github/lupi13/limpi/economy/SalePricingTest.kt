package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.storage.YamlFiles
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SalePricingTest {
    private val hour = 3_600_000L
    private val recovery = 12 * hour
    private val totem = SellRule.Decaying(300, 5, 30)
    private val config = mapOf<String, Any?>(
        "diamond" to 120,
        "totem_of_undying" to mapOf("price" to 300, "half" to 5, "floor" to 30),
        "wheat" to mapOf("price" to 2, "half" to 256, "floor" to 1),
        "hay_block" to mapOf("same" to "wheat", "count" to 9),
        "ender_pearl" to mapOf("price" to 20, "half" to 16, "floor" to 0),
        "flat" to mapOf("price" to 5, "half" to 1, "floor" to 5),
    )
    private val catalog = SellCatalog { config[it] }
    private fun quote(vararg lines: Pair<String, Long>, counters: Map<String, SaleCounter> = emptyMap(), now: Long = 0) =
        catalog.quote(lines.mapIndexed { slot, (code, amount) -> SaleLine(slot, code, amount) }, counters, now, recovery)
    /** The definition, unit by unit, to check the closed form against. */
    private fun direct(rule: SellRule.Decaying, start: Double, units: Long) =
        (0 until units).sumOf { maxOf(rule.floor.toDouble(), rule.price * Math.pow(0.5, (start + it) / rule.half)) }

    @Test fun `unlisted items are worth nothing and fixed prices are exact`() {
        assertEquals(0, quote("dirt" to 64).total.units)
        assertEquals(120 * 64 + 0, quote("diamond" to 64, "dirt" to 64).total.units)
        assertEquals(setOf(0, 1), quote("diamond" to 1, "dirt" to 1).slots)
    }

    @Test fun `closed form matches the unit by unit definition including the floor crossing`() {
        for (rule in listOf(totem, SellRule.Decaying(2, 256, 1), SellRule.Decaying(20, 16, 0), SellRule.Decaying(12, 64, 1), SellRule.Decaying(7, 3, 7)))
            for (start in listOf(0.0, 0.4, 3.0, 17.2, 300.0))
                for (units in listOf(1L, 2L, 5L, 64L, 1000L))
                    assertEquals(direct(rule, start, units), SellCatalog.sum(rule, start, units), 1e-6 * maxOf(1.0, direct(rule, start, units)), "$rule $start $units")
    }

    @Test fun `first sale pays the full price and later units decay toward the floor`() {
        assertEquals(300, quote("totem_of_undying" to 1).total.units)
        assertEquals(788, quote("totem_of_undying" to 3).total.units)
        assertEquals(direct(totem, 0.0, 50).toLong(), quote("totem_of_undying" to 50).total.units)
        // 2-unit wheat keeps paying more than 1 per unit instead of truncating every unit to 1.
        assertTrue(quote("wheat" to 64).total.units > 64 + 50)
        // A zero floor bounds what a farm can ever earn before the count recovers.
        assertTrue(quote("ender_pearl" to 20_000).total.units <= 20 / (1 - Math.pow(0.5, 1.0 / 16)))
        assertEquals(5 * 64, quote("flat" to 64).total.units)
    }

    @Test fun `splitting a stack across slots or sales costs the same as one sale`() {
        val whole = quote("totem_of_undying" to 10)
        val split = quote("totem_of_undying" to 4, "totem_of_undying" to 6)
        assertEquals(whole.total, split.total)
        val first = quote("totem_of_undying" to 4)
        val second = quote("totem_of_undying" to 6, counters = first.counters)
        assertTrue(Math.abs(first.total.units + second.total.units - whole.total.units) <= 1)
        assertEquals(10_000, whole.counters.getValue("totem_of_undying").milli)
    }

    @Test fun `storage blocks share the base item's count`() {
        val blocks = quote("hay_block" to 2)
        assertEquals(quote("wheat" to 18).total, blocks.total)
        assertEquals(18_000, blocks.counters.getValue("wheat").milli)
        assertFalse("hay_block" in blocks.counters)
        val after = quote("wheat" to 1, counters = blocks.counters)
        assertEquals(1, after.total.units)
    }

    @Test fun `sold count halves every recovery period`() {
        val counters = quote("totem_of_undying" to 10).counters
        val counter = counters.getValue("totem_of_undying")
        assertEquals(10.0, counter.effective(0, recovery), 1e-9)
        assertEquals(5.0, counter.effective(recovery, recovery), 1e-9)
        assertEquals(2.5, counter.effective(2 * recovery, recovery), 1e-9)
        assertEquals(10.0, counter.effective(-hour, recovery), 1e-9)
        val later = quote("totem_of_undying" to 1, counters = counters, now = recovery)
        assertEquals((300 * Math.pow(0.5, 1.0)).toLong(), later.total.units)
        assertEquals(6_000, later.counters.getValue("totem_of_undying").milli)
        assertEquals(recovery, later.counters.getValue("totem_of_undying").at)
    }

    @Test fun `rules are strict and an invalid item is left out of the sale`() {
        for (raw in listOf(-1, 1.5, "10", mapOf("price" to 10, "half" to 0, "floor" to 1), mapOf("price" to 10, "half" to 2, "floor" to 11),
                mapOf("price" to 10, "half" to 2), mapOf("price" to 10, "half" to 2, "floor" to 1, "flor" to 1),
                mapOf("same" to "wheat"), mapOf("same" to "wheat", "count" to 0)))
            assertThrows(IllegalArgumentException::class.java, { SellRule.parse(raw) }, raw.toString())
        val broken = SellCatalog { mapOf("bad" to 1.5, "chain" to mapOf("same" to "alias", "count" to 2), "alias" to mapOf("same" to "diamond", "count" to 1),
            "orphan" to mapOf("same" to "missing", "count" to 2), "diamond" to 120)[it] }
        val skipped = mutableListOf<String>()
        val result = broken.quote(listOf(SaleLine(0, "bad", 1), SaleLine(1, "chain", 1), SaleLine(2, "orphan", 1), SaleLine(3, "diamond", 1)), emptyMap(), 0, recovery) { line, _ -> skipped += line.code }
        assertEquals(listOf("bad", "chain", "orphan"), skipped); assertEquals(setOf(3), result.slots); assertEquals(120, result.total.units)
    }

    @Test fun `overflow is reported instead of wrapping`() {
        val huge = SellCatalog { mapOf("a" to Long.MAX_VALUE, "b" to mapOf("price" to Long.MAX_VALUE, "half" to 1_000_000, "floor" to Long.MAX_VALUE / 2))[it] }
        assertThrows(ArithmeticException::class.java) { huge.quote(listOf(SaleLine(0, "a", 2)), emptyMap(), 0, recovery) }
        assertThrows(ArithmeticException::class.java) { huge.quote(listOf(SaleLine(0, "b", 64)), emptyMap(), 0, recovery) }
    }

    @Test fun `stored counts round trip and corrupt records are rejected`() {
        val counters = mapOf("wheat" to SaleCounter(1500, 42), "totem_of_undying" to SaleCounter(0, 7))
        assertEquals(counters, SaleCounters.decode(SaleCounters.encode(counters)))
        assertEquals(emptyMap<String, SaleCounter>(), SaleCounters.decode(null))
        assertThrows(IllegalArgumentException::class.java) { SaleCounters.decode("wheat:\n  milli: 1.5\n  at: 1\n") }
        assertThrows(IllegalArgumentException::class.java) { SaleCounters.decode("wheat: 3\n") }
    }

    /** The bundled files are what a new server starts with: every entry must load and no shop price may undercut a sale. */
    @Test fun `bundled default price files are valid`() {
        val sell = YamlFiles.read(Path.of("src/main/resources/financial/sellPrice.yml"))
        val shop = YamlFiles.read(Path.of("src/main/resources/financial/shopPrice.yml"))
        assertTrue(Files.exists(Path.of("src/main/resources/financial/sellPrice.yml")))
        val bundled = SellCatalog.of(sell)
        assertTrue(sell.getKeys(false).size > 100)
        for (code in sell.getKeys(false)) assertDoesNotThrow({ bundled.rule(code) }, code)
        assertEquals(SellRule.Decaying(12, 64, 1), bundled.rule("gold_ingot"))
        assertEquals(SellRule.Fixed(120), bundled.rule("diamond"))
        assertEquals(SellRule.Fixed(0), bundled.rule("dirt"))
        assertTrue(shop.getKeys(false).size > 50)
        for (code in shop.getKeys(false)) {
            val highest = CatalogPrices.highestSell(bundled, code) { sell.get(it) }
            assertTrue(CatalogPrices.shop(shop.get(code), highest).units >= 3 * highest, code)
        }
        assertFalse(shop.contains("emerald"))
    }
}
