package com.github.lupi13.limpi.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandHelpTest {
    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
    private val player: (String) -> Boolean = { !it.contains(".admin.") }
    private val admin: (String) -> Boolean = { true }
    private val web = WebHelp.links("https://example.org/LIMPI/", true, null)

    private fun clicks(component: Component): List<ClickEvent<*>> =
        listOfNotNull(component.clickEvent()) + component.children().flatMap(::clicks)

    @Test fun `player help lists usage lines only and hides administrative commands`() {
        val lines = CommandHelp.lines("financial", CommandDefinitions.financial, true, false, true, player, WebHelp.links("https://example.org/LIMPI/", false, "financial"))
        val text = lines.map(::plain)
        assertEquals("━━━━━ /financial 도움말 ━━━━━ [웹 도움말]", text[0])
        assertEquals("/fi, /fc 로도 입력할 수 있습니다.", text[1])
        assertTrue("/financial send <플레이어> <금액> - 송금" in text)
        assertTrue(text.none { it.contains("spy") || it.contains("resolve") || it.contains("관리자") || it.startsWith("/financial help") })
    }

    @Test fun `admin help is a separate list linked from the player help`() {
        val lines = CommandHelp.lines("financial", CommandDefinitions.financial, true, false, true, admin, web)
        assertEquals("[관리자 명령어 보기]", plain(lines.last()))
        assertEquals(ClickEvent.runCommand("/financial help admin"), lines.last().clickEvent())
        val adminLines = CommandHelp.lines("financial", CommandDefinitions.financial, true, true, true, admin, web).map(::plain)
        assertEquals("━━━━━ /financial 관리자 명령어 ━━━━━ [관리자 명령어]", adminLines[0])
        assertTrue(adminLines.any { it.startsWith("/financial set ") })
        assertTrue(adminLines.none { it.startsWith("/financial review") })
        assertTrue(adminLines.none { it.startsWith("/financial send ") || it.startsWith("/financial me ") })
        assertEquals("[일반 명령어 보기]", adminLines.last())
    }

    @Test fun `a command without arguments runs on click and one with arguments fills the chat box`() {
        val me = CommandHelp.line("financial", CommandDefinitions.financial.resolve(listOf("me"))!!)
        assertEquals(ClickEvent.runCommand("/financial me"), me.clickEvent())
        val send = CommandHelp.line("financial", CommandDefinitions.financial.resolve(listOf("send"))!!)
        assertEquals(ClickEvent.suggestCommand("/financial send "), send.clickEvent())
        val auto = CommandHelp.line("chest", CommandDefinitions.chest.resolve(listOf("auto"))!!)
        assertEquals(ClickEvent.suggestCommand("/chest auto "), auto.clickEvent())
    }

    @Test fun `help admin is refused and not completed without an administrative permission`() {
        for (index in CommandDefinitions.all.values) {
            assertNotNull(index.resolve(listOf("help", "admin"))!!.adminHelp)
            assertFalse(index.visible(true, true, player).any { it.adminHelp })
            assertTrue(index.visible(true, true, admin).any { it.adminHelp })
            assertEquals(listOf("help"), index.complete(listOf("he"), true, true, player) { emptyList() })
            assertTrue(index.complete(listOf("help", ""), true, true, player) { emptyList() }.isEmpty())
            assertEquals(listOf("admin"), index.complete(listOf("help", ""), true, true, admin) { emptyList() })
        }
    }

    @Test fun `every route has a short description that fits one chat line`() {
        for ((root, index) in CommandDefinitions.all + ("limpi" to CommandDefinitions.limpi)) for (route in index.routes) {
            assertTrue(route.short.isNotBlank() && route.short.length <= 16, "/$root ${route.usage}: ${route.short}")
        }
    }

    @Test fun `a disabled system says so and keeps recovery commands`() {
        val text = CommandHelp.lines("chest", CommandDefinitions.chest, false, false, true, player, emptyList()).map(::plain)
        assertEquals("━━━━━ /chest 도움말 ━━━━━", text[0])
        assertTrue("이 시스템은 꺼져 있어 일부 명령만 쓸 수 있습니다." in text)
        assertTrue(text.any { it.startsWith("/chest open") })
        assertTrue(text.none { it.startsWith("/chest shop") })
    }

    @Test fun `rates is open to players and the console`() {
        val rates = CommandDefinitions.ability.resolve(listOf("rates"))!!
        assertFalse(rates.playerOnly); assertEquals("limpi.ability.pity", rates.permission)
        assertTrue(CommandHelp.lines("ability", CommandDefinitions.ability, true, false, false, player, emptyList()).map(::plain)
            .contains("/ability rates - 뽑기 확률 규칙"))
    }

    @Test fun `every recovery command is under limpi help admin`() {
        val adminLines = CommandHelp.lines("limpi", CommandDefinitions.limpi, true, true, false, admin, web).map(::plain)
        assertEquals("━━━━━ /limpi 관리자 명령어 ━━━━━ [관리자 명령어]", adminLines[0])
        for (command in listOf("review", "delivery", "delivery resolve", "removal", "removal resolve", "transition", "transition resolve",
                "escrow", "escrow resolve", "estate discard", "chest resolve"))
            assertTrue(adminLines.any { it.startsWith("/limpi $command ") }, command)
        assertEquals("[일반 명령어 보기]", adminLines.last())
        assertTrue(CommandDefinitions.limpi.routes.filter { it.path.first() != "help" }.all { it.recovery && it.permission!!.startsWith("limpi.admin.recovery.") })
        assertTrue(CommandHelp.lines("limpi", CommandDefinitions.limpi, true, true, false, player, web).map(::plain).contains("사용할 수 있는 명령어가 없습니다."))
    }

    @Test fun `limpi help points to every system help`() {
        val lines = Limpi.lines(web)
        assertEquals("━━━━━ LIMPI 도움말 ━━━━━ [웹 도움말] [관리자 명령어]", plain(lines[0]))
        assertEquals(CommandDefinitions.all.keys.map { ClickEvent.runCommand("/$it help") }, lines.drop(1).map { it.clickEvent() })
        assertEquals(listOf(ClickEvent.openUrl("https://example.org/LIMPI/"), ClickEvent.openUrl("https://example.org/LIMPI/admin/")), clicks(lines[0]))
    }

    @Test fun `the web home is the configured address`() {
        assertEquals("https://example.org/LIMPI/", WebHelp.home(" https://example.org/LIMPI "))
        assertNull(WebHelp.home("  ")); assertNull(WebHelp.home(null))
    }
}
