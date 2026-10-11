package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.TransactionIdentityConflict
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import kotlin.math.absoluteValue

class AccountCommands(
    private val bank: BankService,
    private val players: PlayerRepository,
    private val plugin: Plugin,
) {
    fun handles(args: Array<out String>) = args.firstOrNull()?.lowercase(Locale.ROOT) in setOf("send", "set", "add")

    fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val command = args[0].lowercase(Locale.ROOT)
        val permission = if (command == "send") "limpi.financial.send" else "limpi.financial.admin.$command"
        if (!sender.hasPermission(permission)) {
            error(sender, "권한이 부족합니다!"); return true
        }
        if (command == "send" && sender !is Player) {
            error(sender, "플레이어만 사용할 수 있습니다."); return true
        }
        if (args.size !in 3..4) {
            error(sender, "잘못된 형식입니다. /financial $command <플레이어> <금액>"); return true
        }
        val target = runCatching { UUID.fromString(args[1]) }.getOrNull() ?: players.findByName(args[1])
        if (target == null) { error(sender, "해당 플레이어를 찾을 수 없습니다!"); return true }
        val amount = args[2].toLongOrNull()
        if (amount == null) { sender.sendMessage(EconomyText.invalidNumber(args[2])); return true }
        // Optional hidden retry ID: the same ID replays the committed result instead of charging again.
        val id = if (args.size == 4) runCatching { UUID.fromString(args[3]) }.getOrNull() else UUID.randomUUID()
        if (id == null) { sender.sendMessage(EconomyText.invalidNumber(args[3], "은(는) 올바르지 않은 거래 ID입니다!")); return true }
        if (command == "send" && target == (sender as Player).uniqueId) {
            error(sender, "자기 자신에게 송금할 수 없습니다!"); return true
        }
        if ((command == "send" && amount <= 0) || (command == "set" && amount < 0) || (command == "add" && amount == 0L)) {
            val text = if (command == "send" && !sender.isOp) "그러면 못써요" else "나쁜 사람..."
            sender.sendMessage(Component.text(text, NamedTextColor.YELLOW)); return true
        }
        val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
        try {
            val receipt = when (command) {
                "send" -> bank.transfer(id, (sender as Player).uniqueId, target, amount)
                "set" -> bank.set(id, actor, target, amount)
                else -> bank.adjust(id, actor, target, amount)
            }
            if (receipt.replayed) {
                sender.sendMessage(Component.text("이미 처리된 거래입니다. 잔액을 다시 변경하지 않았습니다.", NamedTextColor.YELLOW))
                return true
            }
            val name = players.profile(target).name ?: args[1]
            val online = plugin.server.getPlayer(target)
            when (command) {
                "send" -> {
                    sender.sendMessage(playerDisplay(name)
                        .append(Component.text("님에게 ", NamedTextColor.WHITE))
                        .append(moneyDisplay(receipt.amount))
                        .append(Component.text("원을 송금했습니다.", NamedTextColor.WHITE)))
                    sender.sendMessage(Component.text("수수료 5%(", NamedTextColor.WHITE)
                        .append(moneyDisplay(receipt.fee))
                        .append(Component.text("원)가 추가로 차감되었습니다.", NamedTextColor.WHITE)))
                    (sender as Player).let { it.playSound(it, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F) }
                    online?.sendMessage(playerDisplay(sender as Player)
                        .append(Component.text("님이 ", NamedTextColor.WHITE))
                        .append(playerDisplay(name))
                        .append(Component.text("님에게 ", NamedTextColor.WHITE))
                        .append(moneyDisplay(receipt.amount))
                        .append(Component.text("원을 송금했습니다.", NamedTextColor.WHITE)))
                    online?.let { it.playSound(it, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F) }
                }
                "set" -> {
                    val value = receipt.after.getValue(target)
                    sender.sendMessage(playerDisplay(name)
                        .append(Component.text("님의 계좌를 ", NamedTextColor.WHITE))
                        .append(moneyDisplay(value))
                        .append(Component.text("원으로 설정했습니다.", NamedTextColor.WHITE)))
                    (sender as? Player)?.let { it.playSound(it, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F) }
                    online?.sendMessage(playerDisplay(name)
                        .append(Component.text("님의 계좌가 ", NamedTextColor.WHITE))
                        .append(moneyDisplay(value)
                        .append(Component.text("원으로 설정되었습니다.", NamedTextColor.WHITE))))
                    online?.let { it.playSound(it, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F) }
                }
                else -> {
                    val verb = if (receipt.amount > 0) "추가" else "차감"
                    sender.sendMessage(playerDisplay(name)
                        .append(Component.text("님의 계좌에 ", NamedTextColor.WHITE))
                        .append(moneyDisplay(receipt.amount.absoluteValue))
                        .append(Component.text("원을 ", NamedTextColor.WHITE))
                        .append(Component.text(verb, NamedTextColor.YELLOW))
                        .append(Component.text("했습니다.", NamedTextColor.WHITE)))
                    (sender as? Player)?.let { it.playSound(it, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F) }
                    online?.sendMessage(playerDisplay(name)
                        .append(Component.text("님의 계좌에 ", NamedTextColor.WHITE))
                        .append(moneyDisplay(receipt.amount.absoluteValue))
                        .append(Component.text("원이 ", NamedTextColor.WHITE))
                        .append(Component.text(verb, NamedTextColor.YELLOW))
                        .append(Component.text("되었습니다.", NamedTextColor.WHITE)))
                    online?.let { it.playSound(it, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F) }
                }
            }
        } catch (failure: BankException) {
            EconomyText.bankFailure(sender, failure)
            if (command == "send" && failure.reason == BankFailure.INSUFFICIENT_FUNDS) {
                error(sender, "송금 시에는 5%의 수수료가 송금자에게로부터 추가로 부과됩니다.")
            }
        } catch (failure: TransactionIdentityConflict) {
            error(sender, "이 거래 ID는 다른 요청에 사용되었습니다.")
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Account command $command failed for transaction $id", failure)
            error(sender, "거래 결과를 확인하지 못했습니다. 관리자에게 문의하세요.")
        }
        return true
    }

    private fun error(sender: CommandSender, text: String) = sender.sendMessage(Component.text(text, NamedTextColor.RED))
}
