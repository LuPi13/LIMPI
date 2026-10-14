package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.storage.CachedPlayerRepository
import com.github.lupi13.limpi.storage.SqliteProfilePersistence
import com.github.lupi13.limpi.storage.SqliteStateStore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.logging.Logger

/** The real executors must route "help admin" to the administrative list (Financial once dispatched on the first word only). */
class HelpDispatchTest {
    @TempDir lateinit var directory: Path

    private fun plugin(): Plugin {
        val config = YamlConfiguration().apply {
            set("HelpUrl", "https://example.org/LIMPI/"); set("PlayerDataDirectory", directory.toAbsolutePath().toString())
        }
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Plugin::class.java)) { _, method, _ ->
            when (method.name) {
                "getConfig" -> config; "getName" -> "LIMPI"; "namespace" -> "limpi"; "getLogger" -> Logger.getLogger("test"); "toString" -> "plugin fixture"
                else -> null
            }
        } as Plugin
    }

    private fun console(messages: MutableList<String>) = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandSender::class.java)) { _, method, args ->
        when (method.name) {
            "hasPermission" -> true
            "sendMessage" -> { when (val value = args?.firstOrNull()) {
                is Component -> messages += PlainTextComponentSerializer.plainText().serialize(value)
                is String -> messages += value
            }; null }
            "getName" -> "CONSOLE"; "toString" -> "console fixture"
            else -> null
        }
    } as CommandSender

    private fun run(executor: CommandExecutor, vararg input: String): List<String> {
        val messages = mutableListOf<String>()
        executor.onCommand(console(messages), dummyCommand, "x", arrayOf(*input))
        return messages
    }

    private val dummyCommand = object : org.bukkit.command.Command("test") {
        override fun execute(sender: CommandSender, label: String, args: Array<out String>) = true
    }

    @Test fun `help admin shows the administrative list in every system`() {
        val plugin = plugin()
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val players = CachedPlayerRepository(SqliteProfilePersistence(store))
            val journal = com.github.lupi13.limpi.delivery.DeliveryJournal(store)
            val catalog = com.github.lupi13.limpi.market.MarketCatalog(store)
            val controller = com.github.lupi13.limpi.platform.DeliveryController(plugin, com.github.lupi13.limpi.delivery.DeliveryService(journal),
                com.github.lupi13.limpi.market.MarketEscrow(store, catalog), com.github.lupi13.limpi.delivery.RemovalJournal(store, players, emptyMap()),
                com.github.lupi13.limpi.ability.AbilityTransitions(store, players), com.github.lupi13.limpi.chest.ChestService(store, players))
            val executors = mapOf(
                "financial" to Financial(plugin, AccountCommands(com.github.lupi13.limpi.economy.BankService(store, players), players, plugin),
                    DeliveryCommands(journal, controller, plugin), true, MarketCommands(plugin),
                    CheckCommands(plugin, com.github.lupi13.limpi.items.Check(plugin)), CatalogCommands(plugin), RemovalCommands(plugin), TransitionCommands(plugin)),
                "ability" to Ability(plugin, true), "chest" to Chest(plugin, true), "notice" to Notice(plugin))
            for ((root, executor) in executors) {
                val normal = run(executor, "help")
                assertEquals("━━━━━ /$root 도움말 ━━━━━ [웹 도움말]", normal.first(), root)
                assertEquals("[관리자 명령어 보기]", normal.last { it.startsWith("[") && !it.contains(": ") }, root)
                val admin = run(executor, "help", "admin")
                assertEquals("━━━━━ /$root 관리자 명령어 ━━━━━ [관리자 명령어]", admin.first(), root)
                assertTrue(admin.any { it.startsWith("/$root ") }, root)
                assertTrue(admin.contains("관리자 명령어: https://example.org/LIMPI/admin/#$root"), "$root $admin")
                assertTrue(normal.contains("웹 도움말: https://example.org/LIMPI/$root/"), "$root $normal")
            }
        }
    }
}
