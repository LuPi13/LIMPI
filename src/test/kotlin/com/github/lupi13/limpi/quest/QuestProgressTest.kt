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
    @Test fun `lowered repeat targets are centralized and quoted by quest texts`() {
        assertEquals(1000, QuestProgressDefinitions.CANNON_SHOTS.target)
        assertEquals(50, QuestProgressDefinitions.CANNON_KILLS.target)
        assertEquals(50, QuestProgressDefinitions.HAWK_EYE.target)
        assertEquals(50, QuestConditions.EXP_ABSORBER_LEVEL)
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        assertTrue(plain.serialize(NoHurt.howToGet).contains("총 1000회"))
        assertTrue(plain.serialize(NogadaKing.howToGet).contains("50킬"))
        assertTrue(plain.serialize(GiveAll.howToGet).contains("50개"))
        assertTrue(plain.serialize(ExpAbsorber.howToGet).contains("50레벨"))
        assertTrue(plain.serialize(ColdDodge.howToGet).contains("신속 II 이상"))
        // A stored value above a lowered target still reads as complete.
        val profile = YamlConfiguration().apply { set("quest.hand_cannon_shots", 3000) }
        assertEquals(1000, QuestProgressDefinitions.CANNON_SHOTS.counters.single().current(profile))
    }
    @Test fun `cold dodge needs speed II or higher as well as horizontal speed`() {
        assertFalse(QuestConditions.coldDodge(1.0, null))
        assertFalse(QuestConditions.coldDodge(1.0, 0))
        assertFalse(QuestConditions.coldDodge(0.26, 1))
        assertTrue(QuestConditions.coldDodge(0.265, 1))
        assertTrue(QuestConditions.coldDodge(0.5, 4))
    }
    @Test fun `hidden quest hint appears only after enough uses and never for visible or cleared quests`() {
        val hidden = QuestManager.quests.filter { it.isHidden }
        assertEquals(10, hidden.size)
        assertTrue(hidden.all { it.hint != null })
        assertTrue(QuestManager.quests.filter { !it.isHidden }.all { it.hint == null })
        val question = net.kyori.adventure.text.Component.text("???", net.kyori.adventure.text.format.NamedTextColor.GRAY)
        val hint = DarkTime.hint!!
        assertEquals(QuestHints.TIME_LEAPER_USES, hint.counter)
        val profile = YamlConfiguration().apply { set("quest.${hint.counter}", hint.threshold - 1) }
        assertEquals(question, DarkTime.visibleCondition(profile, false))
        profile.set("quest.${hint.counter}", hint.threshold)
        val shown = DarkTime.visibleCondition(profile, false)
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        assertTrue(plain.serialize(shown).startsWith("힌트: "))
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GRAY, shown.color())
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.FALSE, shown.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC))
        assertEquals(DarkTime.howToGet, DarkTime.visibleCondition(profile, true))
        assertTrue(DarkTime.visibleProgress(profile, false).isEmpty())
        for (invalid in listOf<Any>(-1, 7.5, "99")) {
            profile.set("quest.${hint.counter}", invalid)
            assertEquals(question, DarkTime.visibleCondition(profile, false))
        }
        // Dodge hints reuse 회피의 달인's counter; 폭파를 위한 대담함 reuses the hand cannon shot counter.
        assertEquals(QuestProgressDefinitions.DODGE.counter, ColdDodge.hint!!.counter)
        assertEquals(QuestProgressDefinitions.CANNON_SHOTS.counter, BoldForBoom.hint!!.counter)
        assertTrue(QuestHints.DODGE_THRESHOLD <= QuestProgressDefinitions.DODGE.target && BoldForBoom.hint!!.threshold <= QuestProgressDefinitions.CANNON_SHOTS.target)
    }
}
