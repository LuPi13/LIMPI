package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

/** Shared player-facing economy wording (kept identical to the original plugin presentation). */
object EconomyText {
    /** Canonical non-negative balance, or null when the account needs an admin repair. */
    fun balance(id: UUID): Long? =
        runCatching { Accounts.integralBalance(FileManager.players.snapshot(id)) }.getOrNull()?.takeIf { it >= 0 }

    fun balanceDisplay(id: UUID): Component =
        balance(id)?.let(::moneyDisplay) ?: Component.text("계좌 복구 필요", NamedTextColor.RED)

    /** "돈이 부족합니다! <player>님은 현재 계좌에 <money><tail>" */
    fun insufficient(player: Player, tail: String = "원이 있습니다."): Component =
        Component.text("돈이 부족합니다! ", NamedTextColor.RED)
            .append(playerDisplay(player))
            .append(Component.text("님은 현재 계좌에 ", NamedTextColor.RED))
            .append(balanceDisplay(player.uniqueId))
            .append(Component.text(tail, NamedTextColor.RED))

    fun invalidNumber(value: String, tail: String = "은(는) 올바르지 않은 숫자입니다!"): Component =
        Component.text(value, NamedTextColor.YELLOW).append(Component.text(tail, NamedTextColor.RED))

    fun error(sender: CommandSender, text: String) = sender.sendMessage(Component.text(text, NamedTextColor.RED))

    fun deny(player: Player) = player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)

    /** Original wording for bank failures; new failure kinds keep their short service message. */
    fun bankFailure(sender: CommandSender, failure: BankException, insufficientTail: String = "원이 있습니다.") {
        when (failure.reason) {
            BankFailure.INSUFFICIENT_FUNDS ->
                if (sender is Player) sender.sendMessage(insufficient(sender, insufficientTail)) else error(sender, "돈이 부족합니다!")
            BankFailure.SAME_ACCOUNT -> error(sender, "자기 자신에게 송금할 수 없습니다!")
            BankFailure.UNKNOWN_PLAYER -> error(sender, "해당 플레이어를 찾을 수 없습니다!")
            else -> error(sender, failure.reason.message)
        }
    }
}

class Financial(private val plugin: Plugin, private val accountCommands: AccountCommands,
    private val deliveryCommands: DeliveryCommands, private val enabled: Boolean, private val marketCommands: MarketCommands,
    private val checkCommands: CheckCommands, private val catalogCommands: CatalogCommands) : CommandExecutor {
    private val stocks = StockCommands()
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "financial", input, CommandDefinitions.financial, enabled) ?: return true
        try {
            when (route.id.substringBefore(' ')) {
                "help" -> CommandHelp.show(plugin, sender, "financial", CommandDefinitions.financial, enabled, admin = route.adminHelp)
                "send", "set", "add" -> accountCommands.execute(sender, args)
                "claim" -> deliveryCommands.execute(sender, args)
                "market" -> marketCommands.execute(sender, args)
                "check" -> checkCommands.execute(sender, args)
                "sell", "shop" -> catalogCommands.execute(sender, args)
                "stock" -> stocks.execute(sender, args)
                "me" -> {
                    val player = sender as Player
                    player.sendMessage(playerDisplay(player)
                        .append(Component.text("님의 계좌에는 ", NamedTextColor.WHITE))
                        .append(EconomyText.balanceDisplay(player.uniqueId))
                        .append(Component.text("원이 있습니다.", NamedTextColor.WHITE)))
                }
                "spy" -> {
                    val id = runCatching { UUID.fromString(args[1]) }.getOrNull() ?: FileManager.players.findByName(args[1])
                    if (id == null) { EconomyText.error(sender, "해당 플레이어를 찾을 수 없습니다!"); return true }
                    val data = runCatching { FileManager.players.snapshot(id) }.getOrNull()
                    // A UUID that never joined has an empty profile; it is unknown, not an account needing repair.
                    if (data == null || data.getKeys(false).isEmpty()) { EconomyText.error(sender, "해당 플레이어를 찾을 수 없습니다!"); return true }
                    val name = data.getString("name") ?: id.toString()
                    // A malformed value is reported as needing repair instead of being shown as 0.
                    sender.sendMessage(playerDisplay(name)
                        .append(Component.text("님의 계좌에는 ", NamedTextColor.WHITE))
                        .append(EconomyText.balanceDisplay(id))
                        .append(Component.text("원이 있습니다.", NamedTextColor.WHITE)))
                }
                else -> error("Unbound financial command ${route.id}")
            }
        } catch (failure: BankException) {
            EconomyText.bankFailure(sender, failure)
        } catch (failure: IllegalArgumentException) {
            EconomyText.error(sender, failure.message ?: "잘못된 형식입니다.")
        } catch (failure: ArithmeticException) {
            EconomyText.error(sender, "계산 결과가 허용 범위를 초과합니다.")
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Financial command ${route.id} from ${sender.name} failed", failure)
            throw failure // As before: Bukkit reports an internal command error to the sender.
        }
        return true
    }
}
