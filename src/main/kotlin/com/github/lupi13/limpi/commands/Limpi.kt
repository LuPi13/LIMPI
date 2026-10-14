package com.github.lupi13.limpi.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/** /limpi help: one entry point that leads to each system's help and the web pages. */
class Limpi(private val plugin: Plugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        CommandAccess.route(sender, "limpi", input, CommandDefinitions.limpi, true) ?: return true
        val web = WebHelp.links(plugin, sender, null)
        lines(web).forEach(sender::sendMessage)
        if (sender !is Player) web.forEach { sender.sendMessage("${it.label}: ${it.url}") }
        return true
    }

    companion object {
        val SYSTEMS = listOf("ability" to "능력·뽑기·퀘스트", "financial" to "계좌·수표·상점·장터·주식", "chest" to "개인 상자", "notice" to "공지")

        fun lines(web: List<WebHelp.Link>): List<Component> = listOf(CommandHelp.header("LIMPI 도움말", web)) + SYSTEMS.map { (root, text) ->
            Component.text("/$root help", NamedTextColor.GREEN).append(Component.text(" - $text", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("/$root 명령어 사용법을 봅니다.", NamedTextColor.WHITE)
                    .append(Component.text("\n클릭하여 실행", NamedTextColor.GRAY))))
                .clickEvent(ClickEvent.runCommand("/$root help"))
        }
    }
}

class LimpiTab : org.bukkit.command.TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        CommandDefinitions.limpi.complete(args.toList(), true, sender is Player, sender::hasPermission) { emptyList() }
}
