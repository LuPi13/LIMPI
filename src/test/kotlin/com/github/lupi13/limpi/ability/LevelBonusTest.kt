package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LevelBonusTest {
    @Test fun `only gained levels receive the advertised bonus`() {
        assertEquals(12, LevelBonus.level(9, 10, 2))
        assertEquals(14, LevelBonus.level(9, 10, 4))
        assertEquals(7, LevelBonus.level(10, 7, 4))
        assertEquals(10, LevelBonus.level(10, 10, 4))
    }
    @Test fun `experience consumption never becomes bonus gain`() {
        assertEquals(-20, LevelBonus.experience(-20, 3))
        assertEquals(0, LevelBonus.experience(0, 3))
        assertEquals(60, LevelBonus.experience(20, 3))
    }
    @Test fun `maximum values saturate rather than wrapping negative`() {
        assertEquals(Int.MAX_VALUE, LevelBonus.level(1, Int.MAX_VALUE, 4))
        assertEquals(Int.MAX_VALUE, LevelBonus.experience(Int.MAX_VALUE, 3))
    }
}
