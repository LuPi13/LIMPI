package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.ability.PerfectReloadPolicy.State
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PerfectReloadPolicyTest {
    @Test fun `the perfect window is the middle bars and only those count`() {
        assertEquals(8, PerfectReloadPolicy.windowStart(19, 3))
        assertEquals((1..19).filter { PerfectReloadPolicy.perfect(19, 3, it) }, listOf(9, 10, 11))
        assertFalse(PerfectReloadPolicy.perfect(19, 3, 0)) // 아직 아무 칸도 채워지지 않음
        assertFalse(PerfectReloadPolicy.perfect(19, 3, 20))
        assertEquals((1..10).filter { PerfectReloadPolicy.perfect(10, 4, it) }, listOf(4, 5, 6, 7))
        assertEquals((1..5).filter { PerfectReloadPolicy.perfect(5, 9, it) }, (1..5).toList()) // 성공 칸이 길이를 넘으면 전부
    }

    @Test fun `running gauge shows the green middle from the start and fills yellow and the result paints every bar`() {
        val y = NamedTextColor.YELLOW; val g = NamedTextColor.GREEN; val x = NamedTextColor.GRAY
        assertEquals(listOf(x, x, g, g, g, x, x), PerfectReloadPolicy.colors(7, 3, 0, State.RUNNING))
        assertEquals(listOf(y, y, y, y, g, x, x), PerfectReloadPolicy.colors(7, 3, 4, State.RUNNING))
        assertEquals(List(7) { y }, PerfectReloadPolicy.colors(7, 3, 7, State.RUNNING))
        assertEquals(List(7) { NamedTextColor.AQUA }, PerfectReloadPolicy.colors(7, 3, 4, State.PERFECT))
        assertEquals(List(7) { NamedTextColor.RED }, PerfectReloadPolicy.colors(7, 3, 2, State.MISSED))
    }

    @Test fun `action bar text is the white title followed by one bar per tick`() {
        val title = Component.text("완벽한 재장전", NamedTextColor.WHITE)
        val text = PerfectReloadPolicy.render(title, 19, 3, 10, State.RUNNING)
        assertEquals("완벽한 재장전 |||||||||||||||||||", PlainTextComponentSerializer.plainText().serialize(text))
        assertEquals(NamedTextColor.WHITE, text.color())
    }

    @Test fun `vanilla crossbow charge time drops a quarter second per quick charge level`() {
        assertEquals(listOf(25, 20, 15, 10, 5, 0, 0), (0..6).map { PerfectReloadPolicy.chargeTicks(it) })
        assertEquals(0, PerfectReloadPolicy.chargeTicks(Int.MAX_VALUE))
    }

    @Test fun `boosted loading reaches full charge exactly when one more quick charge level would`() {
        for (level in 0..3) {
            val normal = PerfectReloadPolicy.chargeTicks(level)
            val boosted = PerfectReloadPolicy.chargeTicks(level + 1)
            assertEquals(normal, PerfectReloadPolicy.boostedUsedTicks(boosted, level, 1))
            assertTrue(PerfectReloadPolicy.boostedUsedTicks(boosted - 1, level, 1) < normal)
            var previous = 0
            for (elapsed in 0..boosted) {
                val used = PerfectReloadPolicy.boostedUsedTicks(elapsed, level, 1)
                assertTrue(used >= elapsed && used >= previous && used <= normal); previous = used
            }
        }
        assertEquals(0, PerfectReloadPolicy.boostedUsedTicks(0, 0, 1))
        assertEquals(5, PerfectReloadPolicy.boostedUsedTicks(1, 4, 1)) // 빠른 장전 5단계면 바로 장전
        assertEquals(3, PerfectReloadPolicy.boostedUsedTicks(3, 5, 1)) // 이미 0틱이면 그대로
        assertEquals(25, PerfectReloadPolicy.boostedUsedTicks(1, 0, Int.MAX_VALUE))
    }
}
