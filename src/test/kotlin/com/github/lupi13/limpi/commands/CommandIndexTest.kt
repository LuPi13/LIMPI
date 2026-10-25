package com.github.lupi13.limpi.commands

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandIndexTest {
    private fun allow(vararg permissions: String): (String) -> Boolean = { it in permissions }
    @Test fun `case insensitive aliases resolve to the same handler and reload is registered`() {
        assertEquals("dictionary", CommandDefinitions.ability.resolve(listOf("DICT"))!!.id)
        assertEquals("ceiling", CommandDefinitions.ability.resolve(listOf("PITY"))!!.id)
        assertEquals("reload", CommandDefinitions.ability.resolve(listOf("reload"))!!.id)
        assertEquals("stock restore", CommandDefinitions.financial.resolve(listOf("STOCK", "ReStoRe", "ABCD"))!!.id)
    }
    @Test fun `longest known subcommand wins over root and argument bounds remain exact`() {
        val check = CommandDefinitions.financial.resolve(listOf("check", "inspect", "uuid"))!!
        assertEquals("check inspect", check.id); assertEquals(2, check.minimum); assertEquals(3, check.maximum)
        val stock = CommandDefinitions.financial.resolve(listOf("stock", "typo"))!!
        assertEquals("stock", stock.id); assertTrue(2 > stock.maximum)
        assertEquals(6, CommandDefinitions.limpi.resolve(listOf("removal", "resolve"))!!.minimum)
        assertEquals("chest resolve", CommandDefinitions.limpi.resolve(listOf("CHEST", "resolve", "o", "i", "after", "note"))!!.id)
        assertNull(CommandDefinitions.limpi.resolve(listOf("sugar")))
    }
    @Test fun `console help excludes player actions and includes permitted administrative commands`() {
        val routes = CommandDefinitions.financial.visible(true, false, allow("limpi.financial.admin.stock", "limpi.financial.admin.set"))
        assertTrue(routes.any { it.id == "stock add" }); assertTrue(routes.any { it.id == "set" })
        assertFalse(routes.any { it.id in setOf("me", "send", "stock", "claim") })
    }
    @Test fun `disabled financial module exposes only recovery in both help and tab completion`() {
        val routes = CommandDefinitions.financial.visible(false, true) { true }
        assertTrue(routes.any { it.id == "check redeem" }); assertFalse(routes.any { it.id == "check" })
        assertFalse(routes.any { it.id.startsWith("stock") || it.id == "send" })
        // Recovery moved to /limpi; a disabled financial system offers no market subcommands.
        assertTrue(CommandDefinitions.financial.complete(listOf("market", ""), false, true, { true }) { error("no dynamic data needed") }.isEmpty())
    }
    @Test fun `permissions hide both help and dynamic completions without evaluating providers`() {
        val routes = CommandDefinitions.ability.visible(true, true, allow("limpi.ability.me"))
        assertEquals(listOf("help", "me"), routes.map { it.id })
        assertTrue(CommandDefinitions.ability.complete(listOf("set", ""), true, true, { false }) { error("unauthorized provider") }.isEmpty())
    }
    @Test fun `nested literals and dynamic argument values share case insensitive prefix filtering`() {
        val permission = allow("limpi.financial.admin.stock")
        assertEquals(listOf("restore"), CommandDefinitions.financial.complete(listOf("STOCK", "res"), true, false, permission) { emptyList() })
        assertEquals(listOf("ABCD"), CommandDefinitions.financial.complete(listOf("stock", "remove", "ab"), true, false, permission) { listOf("ABCD", "EFGH", "all") })
        assertTrue(CommandDefinitions.financial.complete(listOf("stock", "remove", "ABCD", ""), true, false, permission) { error("too many arguments") }.isEmpty())
    }
    @Test fun `aliases appear in completion but only once in help and empty input is safe`() {
        val permission = allow("limpi.ability.dictionary")
        assertEquals(listOf("dictionary", "dict"), CommandDefinitions.ability.complete(listOf("di"), true, true, permission) { emptyList() })
        assertEquals(1, CommandDefinitions.ability.visible(true, true, permission).count { it.id == "dictionary" })
        assertTrue(CommandDefinitions.ability.complete(emptyList(), true, true, permission) { error("empty") }.isEmpty())
    }
    @Test fun `recovery decisions have correct positions and no inaccessible console actions leak`() {
        val index = CommandDefinitions.limpi
        assertEquals(listOf("applied", "retained"), index.complete(listOf("transition", "resolve", "owner", "id", ""), true, false,
            allow("limpi.admin.recovery.transition")) { if (it == "transitionDecision") listOf("applied", "retained") else error(it) })
        assertEquals(listOf("before", "after"), index.complete(listOf("chest", "resolve", "owner", "id", ""), true, false,
            allow("limpi.admin.recovery.chest")) { if (it == "chestDecision") listOf("before", "after") else error(it) })
        assertFalse(CommandDefinitions.financial.complete(listOf(""), true, false, { true }) { emptyList() }.contains("claim"))
    }
    @Test fun `every configured command permission is explicitly declared in the packaged descriptor`() {
        val descriptor = javaClass.classLoader.getResourceAsStream("plugin.yml")!!.use { stream ->
            org.bukkit.configuration.file.YamlConfiguration().apply {
                options().pathSeparator('|') // plugin.yml permission keys contain literal dots.
                loadFromString(stream.readBytes().toString(Charsets.UTF_8))
            }
        }
        val permissions = descriptor.getConfigurationSection("permissions")!!.getKeys(false)
        for (route in CommandDefinitions.ability.routes + CommandDefinitions.financial.routes + CommandDefinitions.chest.routes + CommandDefinitions.estate.routes + CommandDefinitions.notice.routes + CommandDefinitions.limpi.routes) {
            route.permission?.let { assertTrue(it in permissions, "Missing declared permission $it") }
        }
    }
    @Test fun `console dispatch validates empty input permissions availability and shape without a server`() {
        val messages = mutableListOf<Any?>()
        val sender = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.bukkit.command.CommandSender::class.java)) { _, method, args ->
            when (method.name) {
                "hasPermission" -> args!![0] == "limpi.financial.admin.stock"
                "sendMessage" -> { messages.add(args?.firstOrNull()); null }
                "getName" -> "CONSOLE"
                "toString" -> "console fixture"
                else -> null
            }
        } as org.bukkit.command.CommandSender
        val index = CommandDefinitions.financial
        assertEquals("help", CommandAccess.route(sender, "financial", emptyArray(), index, true)!!.first.id)
        assertNull(CommandAccess.route(sender, "financial", arrayOf("stock"), index, true))
        assertNull(CommandAccess.route(sender, "financial", arrayOf("stock", "add"), index, true))
        assertNull(CommandAccess.route(sender, "financial", arrayOf("stock", "add", "name", "ABCD", "DIAMOND", "100"), index, false))
        assertNull(CommandAccess.route(sender, "financial", arrayOf("set", "name", "100"), index, true))
        assertEquals("stock add", CommandAccess.route(sender, "financial", arrayOf("STOCK", "ADD", "name", "ABCD", "DIAMOND", "100"), index, true)!!.first.id)
        assertEquals(4, messages.size)
    }
    @Test fun `chest opening and recovery stay available when the chest system is off`() {
        val routes = CommandDefinitions.chest.visible(false, true) { true }.map { it.id }
        assertTrue(routes.containsAll(listOf("help", "open", "inspect")))
        assertFalse(routes.any { it in setOf("shop", "auto") })
        val auto = CommandDefinitions.chest.resolve(listOf("AUTO", "on"))!!
        assertEquals("auto", auto.id); assertEquals(1, auto.minimum); assertEquals(2, auto.maximum)
        assertEquals(listOf("on", "off"), CommandDefinitions.chest.complete(listOf("auto", ""), true, true, { true }) { if (it == "toggle") listOf("on", "off") else emptyList() })
        assertTrue(CommandDefinitions.chest.visible(true, false) { true }.none { it.id in setOf("open", "shop", "auto") })
    }
    @Test fun `estate protection management stays available when the estate system is off while buying stops`() {
        val index = CommandDefinitions.estate
        val off = index.visible(false, true) { true }.map { it.id }
        assertTrue(off.containsAll(listOf("help", "show", "list", "where", "sell", "trust", "untrust", "inspect", "remove")))
        assertTrue(off.none { it in setOf("select", "sky", "accept", "deny", "overlaps") })
        assertEquals(listOf("refund", "keep"), index.complete(listOf("remove", "p", "home", ""), true, false, { true }) { if (it == "estateRefund") listOf("refund", "keep") else emptyList() })
        // The confirm button adds the shown refund as a hidden third argument.
        assertEquals(3, index.resolve(listOf("sell", "home", "50"))!!.maximum)
        assertTrue(index.visible(true, false) { true }.map { it.id }.containsAll(listOf("inspect", "remove")))
        assertTrue(index.visible(true, false) { true }.none { it.id in setOf("select", "accept", "sell", "trust") })
    }
    @Test fun `notice reading is open to everyone and console while writing and deleting stay admin`() {
        val index = CommandDefinitions.notice
        assertEquals(listOf("help", "show", "list"), index.visible(true, false) { !it.contains(".admin.") }.map { it.id })
        assertEquals(listOf("help", "show", "list", "unread"), index.visible(true, true) { !it.contains(".admin.") }.map { it.id })
        assertEquals("limpi.notice.admin.write", index.resolve(listOf("WRITE", "[{\"text\":\"a\"}]", "b"))!!.permission)
        assertEquals(Int.MAX_VALUE, index.resolve(listOf("write"))!!.maximum)
        val show = index.resolve(listOf("show", "2026-10-14"))!!
        assertEquals(2, show.minimum); assertEquals(3, show.maximum)
        val values = { kind: String -> if (kind == "noticeDates") listOf("2026-10-14") else if (kind == "noticeNumbers") listOf("001", "002") else emptyList() }
        assertEquals(listOf("2026-10-14"), index.complete(listOf("show", "2026"), true, true, { true }, values))
        assertEquals(listOf("001", "002"), index.complete(listOf("delete", "2026-10-14", "0"), true, false, { true }, values))
        assertTrue(index.complete(listOf("delete", ""), true, false, { !it.contains(".admin.") }, values).isEmpty())
    }
    @Test fun `aliases are read as strings from the packaged descriptor`() {
        // An unquoted YAML 1.1 "no" becomes boolean false and registers /false instead of /no.
        val descriptor = javaClass.classLoader.getResourceAsStream("plugin.yml")!!.use { stream ->
            org.bukkit.configuration.file.YamlConfiguration().apply { loadFromString(stream.readBytes().toString(Charsets.UTF_8)) }
        }
        val aliases = descriptor.getConfigurationSection("commands")!!.getKeys(false).associateWith { descriptor.getList("commands.$it.aliases").orEmpty() }
        assertEquals(mapOf("financial" to listOf("fi", "fc"), "ability" to listOf("ab"), "chest" to listOf("ch"), "estate" to listOf("es"), "notice" to listOf("no", "nt"), "limpi" to emptyList<Any>()), aliases)
        assertEquals(CommandHelp.ALIASES, aliases.filterValues { it.isNotEmpty() }.mapValues { (_, list) -> list.map { it as String } })
    }
}
