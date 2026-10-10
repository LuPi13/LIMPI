package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.TransactionIdentityConflict
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

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
            error(sender, "권한이 부족합니다."); return true
        }
        if (command == "send" && sender !is Player) {
            error(sender, "송금은 플레이어만 사용할 수 있습니다."); return true
        }
        if (args.size !in 3..4) {
            error(sender, "/financial $command <플레이어 또는 UUID> <금액> [거래 ID]"); return true
        }
        val target = runCatching { UUID.fromString(args[1]) }.getOrNull() ?: players.findByName(args[1])
        if (target == null) { error(sender, "해당 플레이어의 계좌를 찾을 수 없습니다."); return true }
        val amount = args[2].toLongOrNull()
        if (amount == null) { error(sender, "금액은 Long 범위의 정수로 입력해 주세요."); return true }
        val id = if (args.size == 4) runCatching { UUID.fromString(args[3]) }.getOrNull() else UUID.randomUUID()
        if (id == null) { error(sender, "거래 ID가 유효하지 않습니다."); return true }
        val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
        try {
            val receipt = when (command) {
                "send" -> bank.transfer(id, (sender as Player).uniqueId, target, amount)
                "set" -> bank.set(id, actor, target, amount)
                else -> bank.adjust(id, actor, target, amount)
            }
            val name = players.profile(target).name ?: target.toString()
            val text = if (receipt.replayed) "이미 완료된 거래를 확인했습니다. 잔액을 다시 변경하지 않았습니다." else when (command) {
                "send" -> "$name 님에게 ${receipt.amount}원을 송금했습니다. 수수료: ${receipt.fee}원"
                "set" -> "$name 님의 잔액을 ${receipt.after.getValue(target)}원으로 설정했습니다."
                else -> "$name 님의 잔액을 ${receipt.amount}원 조정했습니다. 현재: ${receipt.after.getValue(target)}원"
            }
            sender.sendMessage(Component.text(text, NamedTextColor.WHITE))
            sender.sendMessage(Component.text("거래 ID: $id", NamedTextColor.GRAY))
            if (!receipt.replayed && command == "send") plugin.server.getPlayer(target)?.sendMessage(
                Component.text("${sender.name} 님이 ${receipt.amount}원을 송금했습니다.", NamedTextColor.GREEN))
        } catch (failure: BankException) {
            error(sender, failure.reason.message)
        } catch (failure: TransactionIdentityConflict) {
            error(sender, "이 거래 ID는 다른 요청에 사용되었습니다. 기존 요청과 같은 내용으로만 재시도할 수 있습니다.")
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Account command failed for transaction $id", failure)
            error(sender, "거래를 확인하지 못했습니다. 같은 거래 ID로 재시도하거나 관리자에게 확인을 요청하세요: $id")
        }
        return true
    }

    private fun error(sender: CommandSender, text: String) = sender.sendMessage(Component.text(text, NamedTextColor.RED))
}
