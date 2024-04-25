package com.github.lupi13.limpi

import com.github.lupi13.limpi.commands.Financial
import com.github.lupi13.limpi.events.JoinAndQuit
import com.github.lupi13.limpi.items.Check
import org.bukkit.plugin.java.JavaPlugin

class LIMPI : JavaPlugin() {
    override fun onEnable() {
        // Plugin startup logic
        var time = System.currentTimeMillis()
        logger.info("File setting...")
        FileManager.setup()
        config.options().copyDefaults()
        saveDefaultConfig()
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        time = System.currentTimeMillis()
        logger.info("Events Registering...")
        server.pluginManager.registerEvents(JoinAndQuit(), this)
        server.pluginManager.registerEvents(Check(0, null), this)
        logger.info("Done! (time elapsed: ${System.currentTimeMillis() - time} ms)")

        time = System.currentTimeMillis()
        logger.info("Command setting...")
        getCommand("test")!!.setExecutor(Tester())
        getCommand("financial")!!.setExecutor(Financial())
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
        println("                                                  `-'      ver 0.0.2")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("LIMPI disabled.")
    }
}
