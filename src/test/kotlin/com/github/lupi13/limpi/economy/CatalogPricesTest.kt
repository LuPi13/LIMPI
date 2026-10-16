package com.github.lupi13.limpi.economy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CatalogPricesTest {
    private val sell = mapOf<String, Any?>(
        "diamond" to 120, "iron_ingot" to mapOf("price" to 8, "half" to 64, "floor" to 2),
        "iron_block" to mapOf("same" to "iron_ingot", "count" to 9),
    )
    private val catalog = SellCatalog { sell[it] }
    private fun highest(code: String) = CatalogPrices.highestSell(catalog, code) { sell[it] }

    @Test fun `prices are strict non-negative integers without Int saturation`() {
        assertEquals(3_000_000_000, CatalogPrices.price(3_000_000_000L).units)
        assertEquals(0, CatalogPrices.price(0).units)
        for (raw in listOf(-1, 1.5, "100", null)) assertThrows(IllegalArgumentException::class.java) { CatalogPrices.price(raw) }
    }

    @Test fun `shop price may not undercut what one unit sells for`() {
        assertEquals(120, highest("diamond")); assertEquals(8, highest("iron_ingot")); assertEquals(72, highest("iron_block"))
        assertEquals(0, highest("dirt"))
        assertEquals(500, CatalogPrices.shop(500, highest("diamond")).units)
        assertEquals(120, CatalogPrices.shop(120, highest("diamond")).units)
        assertThrows(IllegalArgumentException::class.java) { CatalogPrices.shop(119, highest("diamond")) }
        assertThrows(IllegalArgumentException::class.java) { CatalogPrices.shop(71, highest("iron_block")) }
        assertEquals(3, CatalogPrices.shop(3, highest("dirt")).units)
    }
}
