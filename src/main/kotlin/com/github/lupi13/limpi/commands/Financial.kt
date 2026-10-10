package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.economy.Accounts
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

class Financial(private val plugin: Plugin, private val accountCommands: AccountCommands,
    private val deliveryCommands: DeliveryCommands, private val enabled: Boolean, private val marketCommands: MarketCommands,
    private val checkCommands: CheckCommands, private val catalogCommands: CatalogCommands,
    private val removalCommands: RemovalCommands, private val transitionCommands: TransitionCommands) : CommandExecutor {
    private val stocks = StockCommands()
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "financial", input, CommandDefinitions.financial, enabled) ?: return true
        try {
            when (route.id.substringBefore(' ')) {
                "help" -> CommandHelp.show(sender, "financial", CommandDefinitions.financial, enabled)
                "send", "set", "add" -> accountCommands.execute(sender, args)
                "claim", "delivery" -> deliveryCommands.execute(sender, args)
                "market" -> marketCommands.execute(sender, args)
                "check" -> checkCommands.execute(sender, args)
                "sell", "shop" -> catalogCommands.execute(sender, args)
                "removal" -> removalCommands.execute(sender, args)
                "transition" -> transitionCommands.execute(sender, args)
                "stock" -> stocks.execute(sender, args)
                "me" -> {
                    val balance = Accounts.balance(FileManager.players.snapshot((sender as Player).uniqueId))
                    sender.sendMessage("현재 잔액: ${balance.units}원")
                }
                "spy" -> {
                    val id = runCatching { UUID.fromString(args[1]) }.getOrNull() ?: FileManager.players.findByName(args[1])
                    require(id != null) { "해당 계좌를 찾을 수 없습니다." }
                    val data = FileManager.players.snapshot(id)
                    val name = data.getString("name") ?: id.toString()
                    // Report malformed legacy values explicitly instead of throwing away the spy request.
                    val balance = runCatching { Accounts.integralBalance(data) }.getOrNull()
                    sender.sendMessage(when {
                        balance == null -> "$name: 잔액 형식 오류 (계좌 복구 필요)"
                        balance < 0 -> "$name: ${balance}원 (계좌 복구 필요)"
                        else -> "$name: ${balance}원"
                    })
                }
                else -> error("Unbound financial command ${route.id}")
            }
        } catch (failure: com.github.lupi13.limpi.economy.BankException) {
            sender.sendMessage(failure.reason.message)
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(failure.message ?: "입력을 확인하세요.")
        } catch (failure: ArithmeticException) {
            sender.sendMessage("계산 결과가 허용 범위를 초과합니다.")
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Financial command ${route.id} from ${sender.name} failed", failure)
            sender.sendMessage("처리 결과를 확인하지 못했습니다. 관리자 로그를 확인하세요.")
        }
        return true
    }
}
