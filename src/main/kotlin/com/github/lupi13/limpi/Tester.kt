package com.github.lupi13.limpi

import com.github.lupi13.limpi.items.Check
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Tester: CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            val player: Player = sender
            player.inventory.addItem(Check(args[0].toLong(), player))
        }
        return true
    }
}