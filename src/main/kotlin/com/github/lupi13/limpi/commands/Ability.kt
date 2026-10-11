package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.events.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

class Ability(private val plugin: Plugin, private val enabled: Boolean) : CommandExecutor {
    private val information = GachaInformation(plugin)
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "ability", input, CommandDefinitions.ability, enabled) ?: return true
        try {
            when (route.id) {
                "help" -> {
                    if (sender is Player && enabled) information.openHelpBook(sender, sender.isOp || sender.hasPermission("limpi.ability.admin.set"))
                    else CommandHelp.show(sender, "ability", CommandDefinitions.ability, enabled)
                }
                "me" -> {
                    val player = sender as Player
                    val code = AbilityManager.getPlayerAbilityCodeName(player)
                    if (code.isNullOrEmpty()) {
                        player.sendMessage(Component.text("현재 능력이 없습니다.", NamedTextColor.RED))
                        return true
                    }
                    val ability = AbilityManager.getAbilityByCodeName(code)
                    if (ability == null) {
                        plugin.logger.warning("Player ${player.name} (${player.uniqueId}) has unregistered ability id $code")
                        throw IllegalStateException("Unregistered ability id $code")
                    }
                    player.sendMessage(Component.text("\n", NamedTextColor.WHITE)
                        .append(playerDisplay(player))
                        .append(Component.text("님의 능력", NamedTextColor.WHITE)))
                    ability.sendAbilityInfo(player)
                }
                "shop" -> AbilityShop.openAbilityShopGUI(sender as Player)
                "set" -> {
                    val targetId = runCatching { UUID.fromString(args[1]) }.getOrNull()
                    val target = if (targetId != null) plugin.server.getPlayer(targetId)
                        else plugin.server.onlinePlayers.firstOrNull { it.name.equals(args[1], true) }
                    val ability = if (args[2].equals("none", true)) null else AbilityManager.getAbilityByCodeName(args[2])
                    if (target == null || (ability == null && !args[2].equals("none", true))) {
                        sender.sendMessage(Component.text("플레이어를 찾을 수 없거나 능력 코드가 잘못되었습니다.", NamedTextColor.RED))
                        return true
                    }
                    if (!FileManager.delivery.claim(target)) {
                        sender.sendMessage(Component.text("${target.name}의 지급/회수/전환 기록을 먼저 복구해야 합니다.", NamedTextColor.RED))
                        return true
                    }
                    AbilityManager.applyAbility(target, ability)
                }
                "enemy" -> AbilityEnemySelect.openEnemySelectGUI(sender as Player)
                "select" -> AbilitySelectEvent.openAbilitySelectGUI(sender as Player, 1, SortOption.UNLOCKED)
                "dictionary" -> AbilityDictionary.openAbilityDictionaryGUI(sender as Player, 1, SortOption.UNLOCKED)
                "ceiling" -> information.pity(sender as Player)
                "pickup" -> information.pickup(sender)
                "result" -> {
                    val id = runCatching { UUID.fromString(args[1]) }.getOrNull()
                    if (id == null) sender.sendMessage(Component.text("확인된 결과가 없습니다.", NamedTextColor.RED))
                    else information.result(sender as Player, id)
                }
                else -> error("Unbound ability command ${route.id}")
            }
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "입력을 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Ability command ${route.id} from ${sender.name} failed", failure)
            throw failure // As before: Bukkit reports an internal command error to the sender.
        }
        return true
    }
}
