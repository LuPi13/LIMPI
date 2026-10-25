package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.chest.ChestUnavailable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.logging.Level

/**
 * /limpi help: one entry point that leads to each system's help and the web pages.
 * /limpi help admin and the rest: the recovery commands for interrupted records, available whatever system is off.
 */
class Limpi(private val plugin: Plugin, private val deliveryCommands: DeliveryCommands, private val removalCommands: RemovalCommands,
            private val transitionCommands: TransitionCommands, private val recovery: RecoveryCommands) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "limpi", input, CommandDefinitions.limpi, true) ?: return true
        try {
            when (route.id.substringBefore(' ')) {
                "help" -> if (route.adminHelp) CommandHelp.show(plugin, sender, "limpi", CommandDefinitions.limpi, true, admin = true) else help(sender)
                "review" -> recovery.review(sender)
                "delivery" -> deliveryCommands.execute(sender, args)
                "removal" -> removalCommands.execute(sender, args)
                "transition" -> transitionCommands.execute(sender, args)
                "escrow" -> recovery.escrow(sender, args)
                "chest" -> recovery.chest(sender, args)
                "estate" -> recovery.estate(sender, args)
                else -> error("Unbound limpi command ${route.id}")
            }
        } catch (failure: com.github.lupi13.limpi.estate.EstateUnavailable) {
            sender.sendMessage(Component.text(failure.message ?: "사유지 기록을 처리할 수 없습니다.", NamedTextColor.RED))
        } catch (failure: ChestUnavailable) {
            sender.sendMessage(Component.text(failure.message ?: "상자를 사용할 수 없습니다.", NamedTextColor.RED))
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "입력을 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "LIMPI command ${route.id} from ${sender.name} failed", failure)
            throw failure // As elsewhere: Bukkit reports an internal command error to the sender.
        }
        return true
    }

    private fun help(sender: CommandSender) {
        val web = WebHelp.links(plugin, sender, null)
        lines(web).forEach(sender::sendMessage)
        if (CommandDefinitions.limpi.visible(true, sender is Player, sender::hasPermission).any { it.adminHelp })
            sender.sendMessage(CommandHelp.link("[관리자 명령어 보기]", "/limpi help admin", NamedTextColor.RED))
        if (sender !is Player) web.forEach { sender.sendMessage("${it.label}: ${it.url}") }
    }

    companion object {
        val SYSTEMS = listOf("ability" to "능력·뽑기·퀘스트", "financial" to "계좌·수표·상점·장터·주식", "chest" to "개인 상자", "estate" to "사유지", "notice" to "공지")

        fun lines(web: List<WebHelp.Link>): List<Component> = listOf(CommandHelp.header("LIMPI 도움말", web)) + SYSTEMS.map { (root, text) ->
            Component.text("/$root help", NamedTextColor.GREEN).append(Component.text(" - $text", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("/$root 명령어 사용법을 봅니다.", NamedTextColor.WHITE)
                    .append(Component.text("\n클릭하여 실행", NamedTextColor.GRAY))))
                .clickEvent(ClickEvent.runCommand("/$root help"))
        }
    }
}

/** Always enabled: recovery must stay reachable when a system is turned off. */
class LimpiTab(plugin: Plugin) : CommandCompletion(plugin, CommandDefinitions.limpi, null)
