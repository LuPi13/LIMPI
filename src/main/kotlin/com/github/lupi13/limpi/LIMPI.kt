package com.github.lupi13.limpi

import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.commands.Ability
import com.github.lupi13.limpi.commands.AbilityTab
import com.github.lupi13.limpi.commands.AccountCommands
import com.github.lupi13.limpi.commands.Financial
import com.github.lupi13.limpi.commands.FinancialTab
import com.github.lupi13.limpi.events.*
import com.github.lupi13.limpi.items.Check
import com.github.lupi13.limpi.menu.ReadOnlyMenuListener
import org.bukkit.plugin.java.JavaPlugin

class LIMPI : JavaPlugin() {
    override fun onEnable() {
        // Plugin startup logic

        // File Setting
        var time = System.currentTimeMillis()
        logger.info("File setting...")
        config.options().copyDefaults()
        saveDefaultConfig()
        FileManager.initialize(this)
        server.onlinePlayers.forEach(FileManager::makePlayerData)
        if (config.getBoolean("EnableFinancialSystem")) {
            FileManager.setup()
        }
        if (config.getBoolean("EnableAbilitySystem")) {
            AbilityManager.setupAbilityFiles()
        }
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        // Event Registering
        time = System.currentTimeMillis()
        logger.info("Events Registering...")
        server.pluginManager.registerEvents(JoinAndQuit(), this)
        server.pluginManager.registerEvents(ReadOnlyMenuListener(), this)
        if (config.getBoolean("EnableFinancialSystem")) {
            server.pluginManager.registerEvents(Check(), this)
            server.pluginManager.registerEvents(SellEvents(), this)
            server.pluginManager.registerEvents(ShopEvents(), this)
            server.pluginManager.registerEvents(StockEvents(), this)
            server.pluginManager.registerEvents(MarketEvents(), this)
            StockEvents.stockFlow()
        }
        if (config.getBoolean("EnableAbilitySystem")) {
            server.pluginManager.registerEvents(AbilityManager, this)
            server.pluginManager.registerEvents(GachaEvents(), this)
            server.pluginManager.registerEvents(AbilityShop(), this)
            server.pluginManager.registerEvents(AbilityEnemySelect(), this)
            server.pluginManager.registerEvents(AbilitySelectEvent(), this)
            server.pluginManager.registerEvents(AbilityDictionary(), this)
            AbilityManager.registerAbilities()
        }
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        // Command Setting
        time = System.currentTimeMillis()
        logger.info("Command setting...")
        if (config.getBoolean("EnableFinancialSystem")) {
            getCommand("financial")!!.setExecutor(Financial(AccountCommands(FileManager.bank, FileManager.players, this)))
            getCommand("financial")!!.tabCompleter = FinancialTab()
        }
        if (config.getBoolean("EnableAbilitySystem")) {
            getCommand("ability")!!.setExecutor(Ability())
            getCommand("ability")!!.tabCompleter = AbilityTab()
        }
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
        StockEvents.stop()
        FileManager.close()
        logger.info("LIMPI disabled.")
    }
}
