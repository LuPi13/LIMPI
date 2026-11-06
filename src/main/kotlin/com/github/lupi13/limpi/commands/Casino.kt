package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.events.casino.CasinoEvents
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

/** [events] is null while the casino is off: only help and inspect still answer. */
class Casino(private val plugin: Plugin, private val events: CasinoEvents?) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        // A bare /casino opens the menu; every other path goes through the shared definitions.
        val (route, args) = CommandAccess.route(sender, "casino", if (input.isEmpty()) arrayOf("open") else input, CommandDefinitions.casino, events != null) ?: return true
        when (route.id) {
            "help", "help admin" -> CommandHelp.show(plugin, sender, "casino", CommandDefinitions.casino, events != null, admin = route.adminHelp)
            "inspect" -> inspect(sender, args[1])
            "open" -> requireNotNull(events).openMenu(sender as Player)
            else -> requireNotNull(events).openGame(sender as Player, route.id)
        }
        return true
    }

    private fun inspect(sender: CommandSender, target: String) {
        val owner = runCatching { UUID.fromString(target) }.getOrNull() ?: FileManager.players.findByName(target)
        if (owner == null) { EconomyText.error(sender, "해당 플레이어를 찾을 수 없습니다!"); return }
        val lines = FileManager.casino.describe(owner)
        if (lines.isEmpty()) sender.sendMessage("$target($owner): 진행 중인 카지노 판이 없습니다.")
        lines.forEach { sender.sendMessage("$target($owner): $it") }
    }
}

class CasinoTab(plugin: Plugin) : CommandCompletion(plugin, CommandDefinitions.casino, "EnableCasino")
