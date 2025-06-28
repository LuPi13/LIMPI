package com.github.lupi13.limpi

import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.SafetyFirst
import com.github.lupi13.limpi.commands.Financial
import com.github.lupi13.limpi.commands.FinancialTab
import com.github.lupi13.limpi.events.*
import com.github.lupi13.limpi.items.Check
import org.bukkit.plugin.java.JavaPlugin
import java.io.PrintStream

class LIMPI : JavaPlugin() {
    override fun onEnable() {
        // Plugin startup logic
        System.setOut(PrintStream(System.out, true, "UTF-8"))
        var time = System.currentTimeMillis()
        logger.info("File setting...")
        FileManager.setup()
        config.options().copyDefaults()
        saveDefaultConfig()
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        time = System.currentTimeMillis()
        logger.info("Events Registering...")
        server.pluginManager.registerEvents(JoinAndQuit(), this)
        if (config.getBoolean("EnableFinancialSystem")) {
            server.pluginManager.registerEvents(Check(), this)
            server.pluginManager.registerEvents(SellEvents(), this)
            server.pluginManager.registerEvents(ShopEvents(), this)
            server.pluginManager.registerEvents(StockEvents(), this)
            server.pluginManager.registerEvents(MarketEvents(), this)
            StockEvents.stockFlow()
        }
        if (config.getBoolean("EnableAbilitySystem")) {
            AbilityManager.registerAbilities()
        }
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        time = System.currentTimeMillis()
        logger.info("Command setting...")
        if (config.getBoolean("EnableFinancialSystem")) {
            getCommand("financial")!!.setExecutor(Financial())
            getCommand("financial")!!.tabCompleter = FinancialTab()
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
        println("                                                  `-'      ver 0.0.5")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("LIMPI disabled.")
    }
}
