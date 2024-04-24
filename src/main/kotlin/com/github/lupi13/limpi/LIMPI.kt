package com.github.lupi13.limpi

import org.bukkit.plugin.java.JavaPlugin

class LIMPI : JavaPlugin() {
    override fun onEnable() {
        // Plugin startup logic
        logger.info("LIMPI enabled.")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("LIMPI disabled.")
    }
}
