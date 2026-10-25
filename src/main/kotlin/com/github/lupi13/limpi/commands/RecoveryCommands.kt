package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.events.MarketEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID

/** /limpi recovery commands that have no dedicated handler class: review, escrow and chest windows. */
class RecoveryCommands(private val plugin: Plugin) {
    private fun actor(sender: CommandSender) = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
    private fun offline(owner: UUID) = require(plugin.server.getPlayer(owner) == null) { "소유자가 오프라인일 때 저장 상태를 확인하세요." }

    fun review(sender: CommandSender) {
        val records = com.github.lupi13.limpi.recovery.PendingReviews.list(FileManager.transactions)
        if (records.isEmpty()) sender.sendMessage(Component.text("확인 대기 기록이 없습니다.", NamedTextColor.GRAY))
        records.forEach { record ->
            sender.sendMessage(Component.text("${record.name} / ${record.owner} / ${record.kind} / ${record.id} / ${record.state}", NamedTextColor.WHITE))
            sender.sendMessage(Component.text(record.reason, NamedTextColor.YELLOW))
            sender.sendMessage(Component.text(record.command, NamedTextColor.GRAY))
        }
    }

    /** escrow <owner> | escrow resolve <owner> <listing> removed|retained <note> */
    fun escrow(sender: CommandSender, args: Array<String>) {
        val request = UUID.randomUUID()
        if (args[1].equals("resolve", true)) {
            val owner = UUID.fromString(args[2]); offline(owner)
            val decision = args[4].lowercase(Locale.ROOT)
            require(decision in setOf("removed", "retained")) { "removed 또는 retained를 입력하세요." }
            FileManager.escrow.resolve(request, actor(sender), owner, UUID.fromString(args[3]), decision == "removed", args.drop(5).joinToString(" "))
            sender.sendMessage(Component.text("처리했습니다. 감사 거래 UUID: $request", NamedTextColor.GREEN))
            MarketEvents.reopenMarketGUI()
            return
        }
        val pending = FileManager.escrow.pending(UUID.fromString(args[1]))
        if (pending.isEmpty()) sender.sendMessage(Component.text("조회할 장터 회수 기록이 없습니다.", NamedTextColor.GRAY))
        pending.forEach { sender.sendMessage(Component.text("${it.listing}: ${it.state} / ${it.reason ?: "등록 중"}", NamedTextColor.WHITE)) }
    }

    /** estate discard <estate id> <note>: an estate document that cannot be read; the payload stays in the ledger. */
    fun estate(sender: CommandSender, args: Array<String>) {
        val id = UUID.fromString(args[2])
        val request = UUID.randomUUID()
        FileManager.estates.discard(request, actor(sender), id, args.drop(3).joinToString(" "))
        sender.sendMessage("사유지 기록 ${id}을(를) 정리했습니다. 원본은 감사 거래에 보관했습니다: $request")
    }

    /** chest resolve <owner> <window> before|after <note> */
    fun chest(sender: CommandSender, args: Array<String>) {
        val owner = UUID.fromString(args[2]); val id = UUID.fromString(args[3])
        val applyAfter = when (args[4].lowercase(Locale.ROOT)) {
            "after" -> true; "before" -> false
            else -> throw IllegalArgumentException("before 또는 after를 입력하세요.")
        }
        offline(owner)
        val request = UUID.randomUUID()
        FileManager.chests.resolve(request, actor(sender), owner, id, applyAfter, args.drop(5).joinToString(" "))
        sender.sendMessage("상자 창 기록 ${id}을(를) ${if (applyAfter) "닫은 뒤" else "열기 전"} 상태로 정리했습니다. 감사 거래: $request")
    }
}
