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
    private val checkCommands: CheckCommands, private val catalogCommands: CatalogCommands,
    private val removalCommands: RemovalCommands, private val transitionCommands: TransitionCommands) : CommandExecutor {
    private val stocks = StockCommands()
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "financial", input, CommandDefinitions.financial, enabled) ?: return true
        try {
            when (route.id.substringBefore(' ')) {
                "help" -> CommandHelp.show(plugin, sender, "financial", CommandDefinitions.financial, enabled, admin = false)
                "help admin" -> CommandHelp.show(plugin, sender, "financial", CommandDefinitions.financial, enabled, admin = true)
                "send", "set", "add" -> accountCommands.execute(sender, args)
                "claim", "delivery" -> deliveryCommands.execute(sender, args)
                "market" -> marketCommands.execute(sender, args)
                "check" -> checkCommands.execute(sender, args)
                "sell", "shop" -> catalogCommands.execute(sender, args)
                "removal" -> removalCommands.execute(sender, args)
                "transition" -> transitionCommands.execute(sender, args)
                "stock" -> stocks.execute(sender, args)
                "sugar" -> sugar(sender, args)
                "review" -> {
                    val records = com.github.lupi13.limpi.recovery.PendingReviews.list(FileManager.transactions)
                    if (records.isEmpty()) sender.sendMessage(Component.text("확인 대기 기록이 없습니다.", NamedTextColor.GRAY))
                    records.forEach { record ->
                        sender.sendMessage(Component.text("${record.name} / ${record.owner} / ${record.kind} / ${record.id} / ${record.state}", NamedTextColor.WHITE))
                        sender.sendMessage(Component.text(record.reason, NamedTextColor.YELLOW))
                        sender.sendMessage(Component.text(record.command, NamedTextColor.GRAY))
                    }
                }
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
                    if (data == null) { EconomyText.error(sender, "해당 플레이어를 찾을 수 없습니다!"); return true }
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

    /** The original /financial help book; commands added later are appended in the same style. */
    private fun sugar(sender: CommandSender, args: Array<out String>) {
        val owner = UUID.fromString(if (args[1].equals("void", true)) args[2] else args[1])
        if (!args[1].equals("void", true)) {
            val ids = FileManager.sugar.pendingIds(owner)
            sender.sendMessage(if (ids.isEmpty()) "적용 대기 중인 설탕 결과가 없습니다." else "적용 대기: ${ids.joinToString()}")
            return
        }
        require(plugin.server.getPlayer(owner) == null) { "소유자가 오프라인일 때 결과를 종료하세요." }
        val request = UUID.randomUUID()
        val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
        FileManager.sugar.void(request, actor, owner, UUID.fromString(args[3]), args.drop(4).joinToString(" "))
        sender.sendMessage("설탕 결과 ${args[3]}을(를) 종료했습니다. 감사 거래: $request")
    }
}
