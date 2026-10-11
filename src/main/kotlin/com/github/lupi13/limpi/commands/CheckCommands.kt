package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.checks.CheckException
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.items.Check
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

class CheckCommands(private val plugin: Plugin, private val checks: Check) {
    fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        var request = UUID.randomUUID()
        try {
            when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
                "redeem" -> {
                    require(sender is Player && args.size == 2) { "/financial check redeem" }
                    checks.redeem(sender); return true
                }
                "inspect" -> {
                    require(sender.hasPermission("limpi.financial.admin.check")) { "권한이 부족합니다!" }
                    if (args.size == 3) {
                        val record = requireNotNull(FileManager.checks.find(UUID.fromString(args[2]))) { "발행 기록이 없습니다." }
                        sender.sendMessage(Component.text("${record.id}: ${record.amount.units}원 / ${record.state} / 발행인 ${record.issuer} / 사용인 ${record.redeemer}", NamedTextColor.WHITE))
                    } else {
                        require(sender is Player && args.size == 2) { if (sender is Player) "/financial check inspect [수표 UUID]" else "콘솔은 /financial check inspect <수표 UUID>로 조회하세요." }
                        val held = sender.inventory.itemInMainHand
                        val id = checks.id(held)
                        sender.sendMessage(Component.text(if (id != null) "수표 UUID: $id" else "확인할 수표 형식이 아닙니다.", NamedTextColor.YELLOW))
                    }
                    return true
                }
            }
            require(sender is Player && sender.hasPermission("limpi.financial.check")) { "권한이 부족합니다!" }
            require(args.size in 2..3) { "잘못된 형식입니다. /financial check <금액>" }
            val amount = args[1].toLongOrNull()
            if (amount == null) {
                sender.sendMessage(Component.text(args[1], NamedTextColor.YELLOW)
                    .append(Component.text("는 올바르지 않은 숫자입니다!", NamedTextColor.RED)))
                return true
            }
            if (amount <= 0) {
                sender.sendMessage(Component.text(if (sender.isOp) "어디에... 쓰시려구요...?" else "대출 안돼요", NamedTextColor.YELLOW))
                return true
            }
            // Optional hidden retry ID; never echoed back to the player.
            if (args.size == 3) request = UUID.fromString(args[2])
            if (!FileManager.delivery.claim(sender)) return true
            val result = FileManager.checks.issue(request, sender.uniqueId, amount) { id, money -> PaperItemCodec.grant(checks.item(id, money, sender.name)) }
            if (result.replayed) {
                sender.sendMessage(Component.text("이미 발행된 수표입니다. 다시 차감하거나 지급하지 않았습니다.", NamedTextColor.YELLOW))
            } else {
                sender.sendMessage(Component.text("수표가 발행되었습니다."))
                sender.playSound(sender, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1F, 1.3F)
            }
            FileManager.delivery.claim(sender, quiet = true)
        } catch (failure: CheckException) {
            sender.sendMessage(Component.text("수표가 유효하지 않습니다.", NamedTextColor.RED))
        } catch (failure: BankException) {
            EconomyText.bankFailure(sender, failure)
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "잘못된 형식입니다. /financial check <금액>", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Check command $request failed", failure)
            throw failure // As before: Bukkit reports an internal command error to the sender.
        }
        return true
    }
}
