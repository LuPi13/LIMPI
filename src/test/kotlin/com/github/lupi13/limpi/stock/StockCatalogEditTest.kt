package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import org.bukkit.configuration.file.YamlConfiguration
import com.github.lupi13.limpi.storage.YamlFiles
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StockCatalogEditTest {
    private fun source() = YamlConfiguration().apply {
        set("ABCD.name", "first"); set("ABCD.material", "DIAMOND"); set("ABCD.initialPrice", 100); set("ABCD.currentPrice", 150)
        set("ABCD.unknown", "keep"); set("EFGH.name", "second"); set("EFGH.currentPrice", 200); set("metadata", "keep")
    }
    @Test fun `delisting preserves the whole definition and does not mutate the source`() {
        val original = source(); val result = StockCatalogEdit.edit(original, "remove", "ABCD", "now")
        assertEquals(listOf("EFGH"), StockCatalog.codes(result)); assertEquals(listOf("ABCD", "EFGH"), StockCatalog.allCodes(result))
        assertEquals(150L, StockCatalog.price(result, "ABCD").units); assertEquals("keep", result.getString("ABCD.unknown"))
        assertEquals(listOf("ABCD", "EFGH"), StockCatalog.codes(original)); assertEquals("keep", result.getString("metadata"))
    }
    @Test fun `delisted code and name cannot be reused and restore returns its original quote`() {
        val removed = StockCatalogEdit.edit(source(), "remove", "ABCD", "removed")
        assertThrows(IllegalArgumentException::class.java) { StockCatalogEdit.add(removed, "new", "ABCD", "GOLD_INGOT", Money(1), "now") }
        assertThrows(IllegalArgumentException::class.java) { StockCatalogEdit.add(removed, "first", "IJKL", "GOLD_INGOT", Money(1), "now") }
        val restored = StockCatalogEdit.edit(removed, "restore", "ABCD", "restored")
        assertTrue("ABCD" in StockCatalog.codes(restored)); assertEquals(150L, StockCatalog.price(restored, "ABCD").units)
    }
    @Test fun `all changes only definitions and preserves metadata while refix validates before changing anything`() {
        val removed = StockCatalogEdit.edit(source(), "remove", "all", "now")
        assertTrue(StockCatalog.codes(removed).isEmpty()); assertEquals("keep", removed.getString("metadata"))
        assertThrows(IllegalArgumentException::class.java) { StockCatalogEdit.edit(removed, "restore", "EFGH", "later") }
        val repaired = YamlFiles.parse(removed.saveToString()).apply { set("EFGH.initialPrice", 200) }
        val restored = StockCatalogEdit.edit(repaired, "restore", "all", "later")
        val result = StockCatalogEdit.edit(restored, "refix", "all", "refix")
        assertEquals(150L, result.getLong("ABCD.initialPrice")); assertEquals(200L, result.getLong("EFGH.initialPrice"))
        val malformed = source(); malformed.set("EFGH.currentPrice", "bad"); val before = malformed.saveToString()
        assertThrows(IllegalArgumentException::class.java) { StockCatalogEdit.edit(malformed, "refix", "all", "now") }
        assertEquals(before, malformed.saveToString())
    }
    @Test fun `new definition adds timestamp in the same snapshot and preserves unrelated fields`() {
        val original = source(); val result = StockCatalogEdit.add(original, "third", "IJKL", "DIAMOND", Money(3_000_000_000), "now")
        assertEquals(3_000_000_000L, result.getLong("IJKL.currentPrice")); assertEquals("now", result.getString("lastStockStand"))
        assertEquals("keep", result.getString("metadata")); assertFalse(original.isSet("IJKL"))
    }
    @Test fun `invalid listed state or restore quote is not treated as a normal stock`() {
        val malformed = source(); malformed.set("ABCD.listed", "false")
        assertThrows(IllegalArgumentException::class.java) { StockCatalog.codes(malformed) }
        val removed = StockCatalogEdit.edit(source(), "remove", "ABCD", "now"); removed.set("ABCD.currentPrice", 0)
        assertThrows(IllegalArgumentException::class.java) { StockCatalogEdit.edit(removed, "restore", "ABCD", "later") }
        assertFalse(removed.getBoolean("ABCD.listed"))
    }
}
