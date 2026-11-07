package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.menu.Pagination
import com.github.lupi13.limpi.storage.YamlFiles
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ShopCategoriesTest {
    private val shop = YamlFiles.read(Path.of("src/main/resources/financial/shopPrice.yml"))
    private val file = YamlFiles.read(Path.of("src/main/resources/financial/shopCategory.yml"))
    private val bundled = ShopCategories { file.get(it) }
    private val codes = shop.getKeys(false).toList()

    @Test fun `every bundled offer is filed under a real tab`() {
        val tabs = ShopTab.categories.map { it.code }
        for (code in codes) assertTrue(file.getString(code) in tabs, code)
    }

    @Test fun `every tab but the old stone entry names something the shop sells`() {
        assertEquals(setOf("stone"), file.getKeys(false) - codes.toSet())
    }

    @Test fun `a tab shows only its own offers, in shop order, and all of them are somewhere`() {
        val parts = ShopTab.categories.map { bundled.filter(codes, it) { code -> code } }
        assertTrue(parts.all { it.isNotEmpty() })
        assertEquals(codes.size, parts.sumOf { it.size })
        assertEquals(codes.toSet(), parts.flatten().toSet())
        assertEquals(codes, bundled.filter(codes, ShopTab.ALL) { it })
        val building = bundled.filter(codes, ShopTab.BUILDING) { it }
        assertEquals(codes.filter { it in building }, building)
    }

    @Test fun `what was asked for sits in its tab`() {
        for (code in listOf("paper", "sugar", "snowball", "amethyst_shard", "diamond")) assertEquals(ShopTab.RESOURCE, bundled.of(code), code)
        assertEquals(ShopTab.BUILDING, bundled.of("cobblestone"))
        for (code in listOf("wheat_seeds", "bread", "cookie")) assertEquals(ShopTab.FARMING, bundled.of(code), code)
        assertEquals(ShopTab.DECORATION, bundled.of("poppy"))
        assertEquals(ShopTab.OTHER, bundled.of("torch"))
    }

    @Test fun `no tab is left over for ability materials`() {
        assertEquals(listOf("전체", "건축", "자원", "농사·음식", "장식", "기타"), ShopTab.entries.map { it.label })
    }

    @Test fun `an unfiled or mistyped offer stays visible under other`() {
        val categories = ShopCategories { mapOf<String, Any>("a" to "building", "b" to "nonsense", "c" to 3, "d" to "all")[it] }
        assertEquals(ShopTab.BUILDING, categories.of("a"))
        assertEquals(ShopTab.BUILDING, categories.of("A"), "offers carry upper case codes")
        for (code in listOf("b", "c", "d", "missing")) assertEquals(ShopTab.OTHER, categories.of(code), code)
        assertEquals(listOf("b", "c", "d", "missing"), categories.filter(listOf("a", "b", "c", "d", "missing"), ShopTab.OTHER) { it })
    }

    @Test fun `each tab pages on its own`() {
        val decoration = bundled.filter(codes, ShopTab.DECORATION) { it }
        assertEquals(38, decoration.size)
        assertEquals(1, Pagination(decoration.size, 0).pageCount)
        assertEquals(0, Pagination(decoration.size, 3).page, "a page from a longer tab falls back into range")
    }
}
