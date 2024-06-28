package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.Functions
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.util.StringUtil

class FinancialTab: TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        var completions: MutableList<String> = mutableListOf()
        var list: MutableList<String> = mutableListOf()
        try {
            if (args.size == 1) {
                list.add("help")
                list.add("me")
                list.add("check")
                list.add("wire")
                if (sender.isOp) {
                    list.add("spy")
                    list.add("set")
                    list.add("add")
                }
                StringUtil.copyPartialMatches(args[0], list, completions)
            }

            val needsPlayer: List<String> = listOf("wire", "spy", "set", "add")

            if (args.size == 2 && needsPlayer.contains(args[0])) {
                Functions.getPlayers().forEach { p -> list.add(p.name) }
                StringUtil.copyPartialMatches(args[1], list, completions)
            }
        }
        catch (ignored: Exception) {
        }

        return completions
    }
}