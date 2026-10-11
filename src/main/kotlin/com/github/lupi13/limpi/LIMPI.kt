package com.github.lupi13.limpi

import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.commands.Ability
import com.github.lupi13.limpi.commands.AbilityTab
import com.github.lupi13.limpi.commands.AccountCommands
import com.github.lupi13.limpi.commands.DeliveryCommands
import com.github.lupi13.limpi.commands.MarketCommands
import com.github.lupi13.limpi.commands.CheckCommands
import com.github.lupi13.limpi.commands.CatalogCommands
import com.github.lupi13.limpi.commands.RemovalCommands
import com.github.lupi13.limpi.commands.TransitionCommands
import com.github.lupi13.limpi.commands.Financial
import com.github.lupi13.limpi.commands.FinancialTab
import com.github.lupi13.limpi.events.*
import com.github.lupi13.limpi.items.Check
import com.github.lupi13.limpi.menu.ReadOnlyMenuListener
import com.github.lupi13.limpi.platform.DeliveryGuard
import com.github.lupi13.limpi.platform.AbilityTicketGuard
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.platform.AbilityDamage
import org.bukkit.plugin.java.JavaPlugin
import java.io.PrintStream

class LIMPI : JavaPlugin() {
    private var gacha: GachaEvents? = null
    private var backups: com.github.lupi13.limpi.platform.BackupScheduler? = null
    override fun onEnable() {
        // Plugin startup logic
        System.setOut(PrintStream(System.out, true, "UTF-8"))

        // File Setting
        var time = System.currentTimeMillis()
        logger.info("File setting...")
        config.options().copyDefaults()
        saveDefaultConfig()
        AbilityManager.initialize(this)
        FileManager.initialize(this)
        backups = com.github.lupi13.limpi.platform.BackupScheduler(this)
        AbilityRuntime.initialize(this)
        server.onlinePlayers.forEach { player ->
            // One malformed profile blocks only that player; it must not abort enable for everyone else.
            try { FileManager.makePlayerData(player) } catch (failure: Exception) { FileManager.delivery.reject(player, failure); return@forEach }
            FileManager.delivery.claim(player)
        }
        if (config.getBoolean("EnableFinancialSystem")) {
            FileManager.setup()
        }
        if (config.getBoolean("EnableAbilitySystem")) {
            AbilityManager.setupAbilityFiles()
        }
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        // Event Registering
        val checks = Check(this)
        time = System.currentTimeMillis()
        logger.info("Events Registering...")
        server.pluginManager.registerEvents(JoinAndQuit(), this)
        server.pluginManager.registerEvents(DeliveryGuard(FileManager.delivery, FileManager.deliveries, FileManager.escrow, FileManager.removals, logger), this)
        server.pluginManager.registerEvents(AbilityDamage, this)
        server.pluginManager.registerEvents(ReadOnlyMenuListener(), this)
        server.pluginManager.registerEvents(AbilityTicketGuard(), this)
        server.pluginManager.registerEvents(AbilityRuntime, this)
        server.pluginManager.registerEvents(com.github.lupi13.limpi.platform.AbilityPotions, this)
        if (config.getBoolean("EnableFinancialSystem")) {
            server.pluginManager.registerEvents(checks, this)
            server.pluginManager.registerEvents(SellEvents(), this)
            server.pluginManager.registerEvents(ShopEvents(), this)
            server.pluginManager.registerEvents(StockEvents(), this)
            server.pluginManager.registerEvents(MarketEvents(), this)
            StockEvents.stockFlow()
        }
        if (config.getBoolean("EnableAbilitySystem")) {
            server.pluginManager.registerEvents(AbilityManager, this)
            val gachaEvents = GachaEvents(this)
            gacha = gachaEvents
            server.pluginManager.registerEvents(gachaEvents, this)
            server.pluginManager.registerEvents(AbilityShop(), this)
            server.pluginManager.registerEvents(AbilityEnemySelect(), this)
            server.pluginManager.registerEvents(AbilitySelectEvent(), this)
            server.pluginManager.registerEvents(AbilityDictionary(), this)
            AbilityManager.registerAbilities()
            FileManager.delivery.afterClaim = { player ->
                gachaEvents.recoverApplications(player)
                com.github.lupi13.limpi.quests.Lotto.check(player)
            }
            server.onlinePlayers.forEach { FileManager.delivery.claim(it) }
        }
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        // Command Setting
        time = System.currentTimeMillis()
        logger.info("Command setting...")
        getCommand("financial")!!.setExecutor(Financial(this, AccountCommands(FileManager.bank, FileManager.players, this),
            DeliveryCommands(FileManager.deliveries, FileManager.delivery, this), config.getBoolean("EnableFinancialSystem"), MarketCommands(this), CheckCommands(this, checks), CatalogCommands(this), RemovalCommands(this), TransitionCommands(this)))
        getCommand("financial")!!.tabCompleter = FinancialTab(this)
        getCommand("ability")!!.setExecutor(Ability(this, config.getBoolean("EnableAbilitySystem")))
        getCommand("ability")!!.tabCompleter = AbilityTab(this)
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        val blue = "\u001B[36m"
        val white = "\u001B[0m"
        println("$blue ___        ___     __   __    _______    ___ $white")
        println("$blue|   |      |   |   |  |_|  |  |       |  |   |$white")
        println("$blue|   |      |   |   |       |  |    _  |  |   |$white")
        println("$blue|   |      |   |   |       |  |   |_| |  |   |$white")
        println("$blue|   |___   |   |   |       |  |    ___|  |   |$white .       .     .-.. . --.")
        println("$blue|       |  |   |   | ||_|| |  |   |      |   |$white |-.. .  |  . .|-'.'| --|")
        println("$blue|_______|  |___|   |_|   |_|  |___|      |___|$white `-''-|  '-''-''  ''-'--'")
        println("                                                  `-'      ver ${this.pluginMeta.version}")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        backups?.close()
        backups = null
        StockEvents.stop()
        SellEvents.closeAll()
        AbilityRuntime.closeAll()
        if (config.getBoolean("EnableAbilitySystem")) AbilityManager.shutdown()
        server.onlinePlayers.forEach(AbilityRuntime::clearAttributes)
        gacha?.close()
        gacha = null
        FileManager.close()
        logger.info("LIMPI disabled.")
    }
}
