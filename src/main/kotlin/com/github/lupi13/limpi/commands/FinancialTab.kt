package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager.Companion.getStockConfig
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.StringUtil

class FinancialTab: TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)
        val completions: MutableList<String> = mutableListOf()
        val list: MutableList<String> = mutableListOf()
        try {
            if (args.size == 1) {
                list.add("help")
                list.add("me")
                list.add("check")
                list.add("send")
                list.add("sell")
                list.add("shop")
                list.add("stock")
                list.add("market")
                if (sender.isOp) {
                    list.add("spy")
                    list.add("set")
                    list.add("add")
                }
                StringUtil.copyPartialMatches(args[0], list, completions)
            }

            val needsPlayer: List<String> = listOf("spy", "set", "add", "send")
            val needsMaterial: List<String> = listOf("sell", "shop")

            if (args.size == 2 && needsPlayer.contains(args[0])) {
                val playerNames = plugin.server.onlinePlayers.map { it.name }
                StringUtil.copyPartialMatches(args[1], playerNames, completions)
            }

            if (args.size == 2 && args[0] == "stock") {
                list.add("add")
                list.add("show")
                if (sender.isOp) {
                    list.add("remove")
                    list.add("refix")
                }
                StringUtil.copyPartialMatches(args[1], list, completions)
            }

            if (args.size == 2 && args[0] == "market") {
                list.add("add")
                list.add("remove")
                StringUtil.copyPartialMatches(args[1], list, completions)
            }


            //only op
            if (sender.isOp) {
                if (args.size == 2 && needsMaterial.contains(args[0])) {
                    list.add("set")
                    list.add("remove")
                    StringUtil.copyPartialMatches(args[1], list, completions)
                }

                if (args.size == 3 && needsMaterial.contains(args[0]) && (args[1] == "set" || args[1] == "remove")) {
                    StringUtil.copyPartialMatches(args[2], Functions.allMaterials, completions)
                }

                if (args.size == 5 && args[0] == "stock" && args[1] == "add") {
                    StringUtil.copyPartialMatches(args[4], Functions.allMaterials, completions)
                }

                if (args.size == 3 && args[0] == "stock" && (args[1] == "remove" || args[1] == "refix")) {
                    for (name in getStockConfig().getKeys(false)) {
                        list.add(name)
                    }
                    list.add("all")
                    StringUtil.copyPartialMatches(args[2], list, completions)
                }
            }
        }
        catch (ignored: Exception) {
        }

        return completions
    }
}