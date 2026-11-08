package com.github.lupi13.limpi.menu

import com.github.lupi13.limpi.abilities.Grade
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShopLinksTest {
    @Test fun `the server shop button sits in the empty middle of the bottom row`() {
        val pageButtons = setOf(45, 53); val tabs = setOf(46, 47, 48, 50, 51, 52)
        assertEquals(49, ShopLinks.SERVER_SHOP_SLOT)
        assertFalse(ShopLinks.SERVER_SHOP_SLOT in pageButtons + tabs)
        assertTrue(ShopLinks.SERVER_SHOP_SLOT >= 45, "not over the offers (0~44)")
    }

    @Test fun `the ability shop button is after every ticket and inside the nine slots`() {
        val ticketSlots = 1..(1 + Grade.entries.size) // 뽑기권 + 등급별 선택권
        assertFalse(ShopLinks.ABILITY_SHOP_SLOT in ticketSlots)
        assertTrue(ShopLinks.ABILITY_SHOP_SLOT in 0 until 9)
    }

    @Test fun `a button shows only when the other shop is on and may be opened`() {
        assertTrue(ShopLinks.shown(systemEnabled = true, permitted = true))
        assertFalse(ShopLinks.shown(systemEnabled = false, permitted = true))
        assertFalse(ShopLinks.shown(systemEnabled = true, permitted = false))
        assertFalse(ShopLinks.shown(systemEnabled = false, permitted = false))
    }
}
