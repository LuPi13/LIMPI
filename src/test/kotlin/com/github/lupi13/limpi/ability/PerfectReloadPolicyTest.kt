package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.ability.PerfectReloadPolicy.State
import com.github.lupi13.limpi.config.AbilitySpecs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PerfectReloadPolicyTest {
    @Test fun `the perfect window is the configured bars and only those count`() {
        assertEquals(listOf(9, 10, 11), (0..20).filter { PerfectReloadPolicy.perfect(9, 11, it) })
        assertEquals(listOf(4), (0..20).filter { PerfectReloadPolicy.perfect(4, 4, it) })
        assertFalse(PerfectReloadPolicy.perfect(1, 3, 0)) // 아직 아무 칸도 채워지지 않음
    }

    @Test fun `window bounds must lie inside the gauge in order`() {
        PerfectReloadPolicy.validate(19, 9, 11); PerfectReloadPolicy.validate(1, 1, 1); PerfectReloadPolicy.validate(19, 1, 19)
        assertThrows(IllegalArgumentException::class.java) { PerfectReloadPolicy.validate(19, 12, 11) }
        assertThrows(IllegalArgumentException::class.java) { PerfectReloadPolicy.validate(10, 9, 11) }
        assertThrows(IllegalArgumentException::class.java) { PerfectReloadPolicy.validate(10, 0, 3) }
    }

    @Test fun `perfect reload settings default to bars 9 to 11 of 19 and reject a window past the gauge`() {
        val spec = AbilitySpecs.all.getValue("perfect_reload")
        val (settings, _) = spec.prepare(YamlConfiguration())
        assertEquals(listOf(19, 9, 11, 2), listOf("gauge_length", "perfect_start", "perfect_end", "quick_charge_bonus").map(settings::getInt))
        assertThrows(IllegalArgumentException::class.java) { spec.prepare(YamlConfiguration().apply { set("gauge_length", 10) }) }
        assertThrows(IllegalArgumentException::class.java) { spec.prepare(YamlConfiguration().apply { set("perfect_start", 12) }) }
    }

    @Test fun `running gauge shows the green window from the start and fills yellow and the result paints every bar`() {
        val y = NamedTextColor.YELLOW; val g = NamedTextColor.GREEN; val x = NamedTextColor.GRAY
        assertEquals(listOf(x, x, g, g, g, x, x), PerfectReloadPolicy.colors(7, 3, 5, 0, State.RUNNING))
        assertEquals(listOf(y, y, y, y, g, x, x), PerfectReloadPolicy.colors(7, 3, 5, 4, State.RUNNING))
        assertEquals(List(7) { y }, PerfectReloadPolicy.colors(7, 3, 5, 7, State.RUNNING))
        assertEquals(listOf(g, x, x, x, x, x, x), PerfectReloadPolicy.colors(7, 1, 1, 0, State.RUNNING))
        assertEquals(List(7) { NamedTextColor.AQUA }, PerfectReloadPolicy.colors(7, 3, 5, 4, State.PERFECT))
        assertEquals(List(7) { NamedTextColor.RED }, PerfectReloadPolicy.colors(7, 3, 5, 2, State.MISSED))
    }

    @Test fun `action bar text is the white title followed by one bar per tick`() {
        val title = Component.text("완벽한 재장전", NamedTextColor.WHITE)
        val text = PerfectReloadPolicy.render(title, 19, 9, 11, 10, State.RUNNING)
        assertEquals("완벽한 재장전 " + "┃".repeat(19), PlainTextComponentSerializer.plainText().serialize(text))
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
