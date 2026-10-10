package com.github.lupi13.limpi.commands

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandIndexTest {
    private fun allow(vararg permissions: String): (String) -> Boolean = { it in permissions }
    @Test fun `case insensitive aliases resolve to the same handler and missing reload stays unknown`() {
        assertEquals("dictionary", CommandDefinitions.ability.resolve(listOf("DICT"))!!.id)
        assertEquals("ceiling", CommandDefinitions.ability.resolve(listOf("PITY"))!!.id)
        assertNull(CommandDefinitions.ability.resolve(listOf("reload")))
        assertEquals("stock restore", CommandDefinitions.financial.resolve(listOf("STOCK", "ReStoRe", "ABCD"))!!.id)
    }
    @Test fun `longest known subcommand wins over root and argument bounds remain exact`() {
        val check = CommandDefinitions.financial.resolve(listOf("check", "inspect", "uuid"))!!
        assertEquals("check inspect", check.id); assertEquals(2, check.minimum); assertEquals(3, check.maximum)
        val stock = CommandDefinitions.financial.resolve(listOf("stock", "typo"))!!
        assertEquals("stock", stock.id); assertTrue(2 > stock.maximum)
        assertEquals(6, CommandDefinitions.financial.resolve(listOf("removal", "resolve"))!!.minimum)
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
        val completion = CommandDefinitions.financial.complete(listOf("market", ""), false, true, { true }) { error("no dynamic data needed") }
        assertEquals(listOf("escrow", "resolve"), completion)
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
        assertEquals(listOf("dict", "dictionary"), CommandDefinitions.ability.complete(listOf("di"), true, true, permission) { emptyList() })
        assertEquals(1, CommandDefinitions.ability.visible(true, true, permission).count { it.id == "dictionary" })
        assertTrue(CommandDefinitions.ability.complete(emptyList(), true, true, permission) { error("empty") }.isEmpty())
    }
    @Test fun `recovery decisions have correct positions and no inaccessible console actions leak`() {
        val index = CommandDefinitions.financial
        assertEquals(listOf("applied", "retained"), index.complete(listOf("transition", "resolve", "owner", "id", ""), false, false,
            allow("limpi.financial.admin.transition")) { if (it == "transitionDecision") listOf("applied", "retained") else error(it) })
        assertFalse(index.complete(listOf(""), true, false, { true }) { emptyList() }.contains("claim"))
    }
    @Test fun `every configured command permission is explicitly declared in the packaged descriptor`() {
        val descriptor = javaClass.classLoader.getResourceAsStream("plugin.yml")!!.use { stream ->
            org.bukkit.configuration.file.YamlConfiguration().apply {
                options().pathSeparator('|') // plugin.yml permission keys contain literal dots.
                loadFromString(stream.readBytes().toString(Charsets.UTF_8))
            }
        }
        val permissions = descriptor.getConfigurationSection("permissions")!!.getKeys(false)
        for (route in CommandDefinitions.ability.routes + CommandDefinitions.financial.routes) {
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
}
