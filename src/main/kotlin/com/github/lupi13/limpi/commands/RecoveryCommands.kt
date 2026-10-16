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

/** /limpi recovery commands that have no dedicated handler class: review, escrow, chest windows and sugar results. */
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

    /** sugar <owner> | sugar void <owner> <result> <note> */
    fun sugar(sender: CommandSender, args: Array<String>) {
        if (!args[1].equals("void", true)) {
            val ids = FileManager.sugar.pendingIds(UUID.fromString(args[1]))
            sender.sendMessage(if (ids.isEmpty()) "적용 대기 중인 설탕 결과가 없습니다." else "적용 대기: ${ids.joinToString()}")
            return
        }
        val owner = UUID.fromString(args[2])
        require(plugin.server.getPlayer(owner) == null) { "소유자가 오프라인일 때 결과를 종료하세요." }
        val request = UUID.randomUUID()
        FileManager.sugar.void(request, actor(sender), owner, UUID.fromString(args[3]), args.drop(4).joinToString(" "))
        sender.sendMessage("설탕 결과 ${args[3]}을(를) 종료했습니다. 감사 거래: $request")
    }
}
