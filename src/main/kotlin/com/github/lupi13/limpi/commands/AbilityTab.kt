package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.AbilityManager
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.StringUtil

class AbilityTab: TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String?>? {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)
        val completions: MutableList<String> = mutableListOf()
        val list: MutableList<String> = mutableListOf()
        try {
            if (args.size == 1) {
                list.add("help")
                list.add("enemy")
                list.add("me")
                list.add("select")
                list.add("dictionary")
                list.add("dict")
                list.add("shop")
                list.add("pickup")
                list.add("ceiling")
                list.add("pity")
                if (sender.isOp) {
                    list.add("set")
                    list.add("reload")
                }
                StringUtil.copyPartialMatches(args[0], list, completions)
            }



            //only op
            if (sender.isOp) {
                if (args[0] == "set") {
                    if (args.size == 2) {
                        val playerNames = plugin.server.onlinePlayers.map { it.name }
                        StringUtil.copyPartialMatches(args[1], playerNames, completions)
                    }
                    if (args.size == 3) {
                        val abilityCodeNames = AbilityManager().abilities.map { it.codeName }
                        StringUtil.copyPartialMatches(args[2], abilityCodeNames, completions)
                    }
                }
            }
        }
        catch (ignored: Exception) {
        }

        return completions
    }
}