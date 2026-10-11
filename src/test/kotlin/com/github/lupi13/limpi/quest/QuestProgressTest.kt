package com.github.lupi13.limpi.quest

import com.github.lupi13.limpi.quests.*
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class QuestProgressTest {
    @Test fun `registry uses centralized counters and health units preserve decimal progress`() {
        assertEquals(QuestProgressDefinitions.DODGE, DodgeMaster.progress)
        assertEquals(QuestProgressDefinitions.HAWK_EYE, GiveAll.progress)
        assertEquals(QuestProgressDefinitions.CANNON_KILLS, NogadaKing.progress)
        assertEquals(QuestProgressDefinitions.CANNON_SHOTS, NoHurt.progress)
        assertEquals(QuestProgressDefinitions.TIME_HEALED, LightTime.progress)
        assertEquals(QuestProgressDefinitions.TIME_LOST, DarkTime.progress)
        assertEquals(QuestProgressDefinitions.HARMONY, LineBetweenSwordAndArrow.progress)
        val healed = LightTime.progress!!.counters.single()
        val profile = YamlConfiguration().apply { set("quest.${healed.counter}", 375L) }
        assertEquals("37.5", healed.format(healed.current(profile)))
        assertEquals("200", healed.format(healed.target))
        assertTrue(DarkTime.isHidden)
        assertEquals(2, LineBetweenSwordAndArrow.progress!!.counters.size)
    }
    @Test fun `missing counters read zero and malformed counters are rejected without overwriting`() {
        val counter = QuestProgressDefinitions.DODGE.counters.single()
        val profile = YamlConfiguration()
        assertEquals(0, counter.current(profile))
        profile.set("quest.${counter.counter}", 101L)
        assertEquals(100, counter.current(profile))
        for (invalid in listOf<Any>(-1, 0.5, "broken")) {
            profile.set("quest.${counter.counter}", invalid)
            assertThrows(IllegalArgumentException::class.java) { counter.current(profile) }
            assertEquals(invalid, profile.get("quest.${counter.counter}"))
        }
    }
    @Test fun `unfinished hidden quest reveals no condition or progress but completed quest does`() {
        val profile = YamlConfiguration().apply { set("quest.time_leaper_lost", 500) }
        assertEquals(net.kyori.adventure.text.Component.text("???", net.kyori.adventure.text.format.NamedTextColor.GRAY), DarkTime.visibleCondition(false))
        assertTrue(DarkTime.visibleProgress(profile, false).isEmpty())
        assertEquals(DarkTime.howToGet, DarkTime.visibleCondition(true))
        assertEquals(listOf("체력 50/50"), DarkTime.visibleProgress(profile, true))
        profile.set("quest.harmony_sword", 4); profile.set("quest.harmony_arrow", 7)
        assertEquals(listOf("검 4/10", "화살 7/10"), LineBetweenSwordAndArrow.visibleProgress(profile, false))
    }
}
