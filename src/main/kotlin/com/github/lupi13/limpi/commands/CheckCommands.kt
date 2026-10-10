package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.checks.CheckException
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.items.Check
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
                    require(sender.hasPermission("limpi.financial.admin.check")) { "권한이 부족합니다." }
                    if (args.size == 3) {
                        val record = requireNotNull(FileManager.checks.find(UUID.fromString(args[2]))) { "발행 기록이 없습니다." }
                        sender.sendMessage(Component.text("${record.id}: ${record.amount.units}원 / ${record.state} / 발행인 ${record.issuer} / 사용인 ${record.redeemer}", NamedTextColor.WHITE))
                    } else {
                        require(sender is Player && args.size == 2) { if (sender is Player) "/financial check inspect [수표 UUID]" else "콘솔은 /financial check inspect <수표 UUID>로 조회하세요." }
                        val held = sender.inventory.itemInMainHand
                        val id = checks.id(held)
                        val legacy = checks.legacy(held)
                        sender.sendMessage(Component.text(if (id != null) "수표 UUID: $id" else if (legacy != null)
                            "기존 수표: ${legacy.amount}원 / 발행인 ${legacy.issuerName} / ${legacy.fingerprint}. 공개 해시는 발행 증거가 아닙니다."
                            else "확인할 수표 형식이 아닙니다.", NamedTextColor.YELLOW))
                    }
                    return true
                }
                "import" -> {
                    require(sender.hasPermission("limpi.financial.admin.check")) { "권한이 부족합니다." }
                    require(args.size >= 5) { "/financial check import <현재 소유자 UUID> <확인한 발행인 UUID> <확인 근거>" }
                    val owner = requireNotNull(plugin.server.getPlayer(UUID.fromString(args[2]))) { "현재 소유자가 접속하여 원본 수표를 주 손에 들고 있어야 합니다." }
                    require(!FileManager.delivery.blocked(owner.uniqueId)) { "소유자의 지급 기록을 먼저 복구하세요." }
                    val legacy = requireNotNull(checks.legacy(owner.inventory.itemInMainHand)) { "원본 수표 형식을 확인할 수 없습니다." }
                    val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
                    val result = FileManager.checks.importLegacy(request, actor, legacy.fingerprint, owner.uniqueId,
                        UUID.fromString(args[3]), legacy.amount, args.drop(4).joinToString(" ")) { id, money ->
                        PaperItemCodec.grant(checks.item(id, money, legacy.issuerName))
                    }
                    sender.sendMessage(Component.text("기존 수표를 전환했습니다. 원본은 보존하며 다시 입금되지 않습니다. 거래: ${result.transaction}", NamedTextColor.GREEN))
                    FileManager.delivery.claim(owner); return true
                }
            }
            require(sender is Player && sender.hasPermission("limpi.financial.check")) { "플레이어 권한이 필요합니다." }
            require(args.size in 2..3) { "/financial check <양수 금액> [거래 UUID]" }
            val amount = requireNotNull(args[1].toLongOrNull()) { "금액은 Long 범위 정수로 입력하세요." }
            require(amount > 0) { "수표 금액은 양수여야 합니다." }
            if (args.size == 3) request = UUID.fromString(args[2])
            if (!FileManager.delivery.claim(sender)) return true
            val result = FileManager.checks.issue(request, sender.uniqueId, amount) { id, money -> PaperItemCodec.grant(checks.item(id, money, sender.name)) }
            sender.sendMessage(Component.text(if (result.replayed) "기존 발행 결과를 확인했습니다. 다시 차감하거나 지급하지 않습니다. 거래: $request"
                else "${result.amount}원 수표 발행을 확정했습니다. 거래: $request", NamedTextColor.GREEN))
            FileManager.delivery.claim(sender)
        } catch (failure: CheckException) {
            sender.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: BankException) {
            sender.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "입력을 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Check command $request failed", failure)
            sender.sendMessage(Component.text("발행/전환 결과를 확인하지 못했습니다. 관리자에게 거래 UUID $request 을 알려주세요.", NamedTextColor.RED))
        }
        return true
    }
}
