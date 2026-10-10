package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.events.*
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
                "help" -> CommandHelp.show(sender, "ability", CommandDefinitions.ability, enabled, if (enabled) information.pages() else emptyList())
                "me" -> {
                    val player = sender as Player
                    val code = AbilityManager.getPlayerAbilityCodeName(player)
                    val ability = AbilityManager.getAbilityByCodeName(code.orEmpty())
                    if (ability != null) ability.sendAbilityInfo(player)
                    else player.sendMessage(if (code.isNullOrBlank()) "현재 능력이 없습니다." else "등록되지 않은 능력 ID: $code. 기존 기록은 유지됩니다. 관리자에게 확인하세요.")
                }
                "shop" -> AbilityShop.openAbilityShopGUI(sender as Player)
                "set" -> {
                    val targetId = runCatching { UUID.fromString(args[1]) }.getOrNull()
                    val target = if (targetId != null) plugin.server.getPlayer(targetId)
                        else plugin.server.onlinePlayers.firstOrNull { it.name.equals(args[1], true) }
                    require(target != null) { "접속한 플레이어를 찾을 수 없습니다." }
                    val ability = if (args[2].equals("none", true)) null else
                        requireNotNull(AbilityManager.getAbilityByCodeName(args[2])) { "능력 ID가 잘못되었습니다." }
                    if (!FileManager.delivery.claim(target)) {
                        sender.sendMessage("${target.name}의 지급/회수/전환 기록을 먼저 복구해야 합니다."); return true
                    }
                    AbilityManager.applyAbility(target, ability)
                    sender.sendMessage("${target.name}: ${ability?.codeName ?: "능력 제거"}")
                }
                "enemy" -> AbilityEnemySelect.openEnemySelectGUI(sender as Player)
                "select" -> AbilitySelectEvent.openAbilitySelectGUI(sender as Player, 1, SortOption.UNLOCKED)
                "dictionary" -> AbilityDictionary.openAbilityDictionaryGUI(sender as Player, 1, SortOption.UNLOCKED)
                "ceiling" -> information.pity(sender as Player)
                "pickup" -> information.pickup(sender)
                "result" -> information.result(sender as Player, UUID.fromString(args[1]))
                else -> error("Unbound ability command ${route.id}")
            }
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(failure.message ?: "입력을 확인하세요.")
        } catch (failure: ArithmeticException) {
            sender.sendMessage("설정 또는 계산 결과가 허용 범위를 초과합니다.")
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Ability command ${route.id} from ${sender.name} failed", failure)
            sender.sendMessage("처리 결과를 확인하지 못했습니다. 관리자 로그를 확인하세요.")
        }
        return true
    }
}
