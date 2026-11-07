package com.github.lupi13.limpi.economy

import java.util.Locale

/** The shop window's tabs, in the order they are shown. [ALL] holds every offer and is not a category of its own. */
enum class ShopTab(val code: String, val label: String) {
    ALL("all", "전체"), BUILDING("building", "건축"), RESOURCE("resource", "자원"),
    FARMING("farming", "농사·음식"), DECORATION("decoration", "장식"), OTHER("other", "기타");

    companion object {
        /** The tabs an item can be filed under. */
        val categories = entries.filter { it != ALL }
    }
}

/** shopCategory.yml: only sorts what shopPrice.yml sells, so a missing or mistyped entry shows under [ShopTab.OTHER] instead of hiding an offer. */
class ShopCategories(private val read: (String) -> Any?) {
    fun of(code: String): ShopTab {
        val value = read(code.lowercase(Locale.ROOT))
        return ShopTab.categories.firstOrNull { it.code == value } ?: ShopTab.OTHER
    }

    /** Keeps the shop's order. */
    fun <T> filter(offers: List<T>, tab: ShopTab, code: (T) -> String): List<T> =
        if (tab == ShopTab.ALL) offers else offers.filter { of(code(it)) == tab }
}
