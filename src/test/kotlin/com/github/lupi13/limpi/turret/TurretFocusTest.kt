package com.github.lupi13.limpi.turret

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TurretFocusTest {
    @Test fun `reservations expire and keep the latest end`() {
        val focus = TurretFocus<String>()
        focus.reserve("a", 20); focus.reserve("a", 15)
        assertTrue(focus.busy("a", 19)); assertFalse(focus.busy("a", 20)); assertFalse(focus.busy("b", 0))
        focus.reserve("b", 5); focus.prune(10)
        assertEquals(1, focus.size)
    }

    @Test fun `choose prefers the first free usable target and falls back to a busy one`() {
        val busy = setOf("a", "b"); val unusable = setOf("c")
        val checked = mutableListOf<String>()
        fun pick(ranked: List<String>, limit: Int = 4) = TurretFocus.choose(ranked, limit, { checked += it; it !in unusable }, { it in busy })
        assertEquals("d", pick(listOf("a", "b", "c", "d")))
        assertEquals(listOf("a", "c", "d"), checked) // 대안(a)이 생긴 뒤 바쁜 b는 시야 검사 생략
        assertEquals("a", pick(listOf("a", "b"))) // 모두 바쁘면 첫 대상
        assertNull(pick(listOf("c")))
        assertEquals("a", pick(listOf("a", "b", "c", "d"), limit = 2)) // 앞쪽 몇 개만 봄
        assertNull(pick(emptyList()))
    }

    @Test fun `flight time estimate`() {
        assertEquals(7, TurretFocus.flightTicks(10.0, 1.5))
        assertEquals(0, TurretFocus.flightTicks(10.0, 0.0))
        assertEquals(0, TurretFocus.flightTicks(-1.0, 1.0))
    }
}
