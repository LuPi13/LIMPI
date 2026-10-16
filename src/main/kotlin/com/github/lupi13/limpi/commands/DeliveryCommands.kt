package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.platform.DeliveryController
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

class DeliveryCommands(private val journal: DeliveryJournal, private val controller: DeliveryController, private val plugin: Plugin) {
    fun handles(args: Array<out String>) = args.firstOrNull()?.lowercase(Locale.ROOT) in setOf("claim", "delivery")

    fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (args[0].equals("claim", true)) {
            if (sender !is Player || !sender.hasPermission("limpi.financial.claim") || args.size != 1) {
                error(sender, "플레이어만 사용할 수 있습니다."); return true
            }
            val nothing = journal.pending(sender.uniqueId).isEmpty()
            if (controller.claim(sender) && nothing) error(sender, "받을 아이템이 없습니다.")
            return true
        }
        if (!sender.hasPermission("limpi.admin.recovery.delivery")) { error(sender, "권한이 부족합니다!"); return true }
        try {
            if (args.getOrNull(1)?.equals("resolve", true) == true) {
                if (args.size < 6) { error(sender, "/limpi delivery resolve <소유자 UUID> <지급 UUID> <delivered|retry> <확인 근거>"); return true }
                val owner = UUID.fromString(args[2]); val id = UUID.fromString(args[3])
                if (plugin.server.getPlayer(owner) != null) { error(sender, "소유자가 오프라인일 때 저장된 playerdata를 확인한 뒤 해결하세요."); return true }
                val decision = DeliveryResolution.valueOf(args[4].uppercase(Locale.ROOT))
                val note = args.drop(5).joinToString(" ")
                val request = UUID.randomUUID()
                journal.resolve(request, owner, id, (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE", decision, note)
                sender.sendMessage(Component.text("지급 $id 검토를 ${decision.name}로 기록했습니다. 감사 거래: $request", NamedTextColor.GREEN))
                return true
            }
            if (args.size !in 2..3) { error(sender, "/limpi delivery <소유자 UUID> [지급 UUID]"); return true }
            val owner = UUID.fromString(args[1])
            val records = if (args.size == 3) listOfNotNull(journal.find(owner, UUID.fromString(args[2]))) else journal.pending(owner)
            if (records.isEmpty()) sender.sendMessage(Component.text("조회할 지급 기록이 없습니다.", NamedTextColor.GRAY))
            records.forEach { record ->
                sender.sendMessage(Component.text("${record.id} / ${record.state} / ${record.reason}", NamedTextColor.WHITE))
                sender.sendMessage(Component.text("수량: ${record.items.joinToString { it.count.toString() }}, 전/후 슬롯: ${record.before?.size}/${record.after?.size}", NamedTextColor.GRAY))
                record.reviewReason?.let { sender.sendMessage(Component.text(it, NamedTextColor.YELLOW)) }
            }
        } catch (failure: IllegalArgumentException) {
            error(sender, "UUID·결정·조회 조건을 확인하세요: ${failure.message}")
        } catch (failure: IllegalStateException) {
            error(sender, "처리할 수 없는 지급 상태입니다: ${failure.message}")
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Delivery command failed", failure)
            error(sender, "저장소 오류가 발생했습니다. 로그와 지급 기록을 확인하세요.")
        }
        return true
    }

    private fun error(sender: CommandSender, text: String) = sender.sendMessage(Component.text(text, NamedTextColor.RED))
}
