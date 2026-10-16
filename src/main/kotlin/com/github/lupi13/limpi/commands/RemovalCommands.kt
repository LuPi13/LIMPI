package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

class RemovalCommands(private val plugin: Plugin) {
    fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("limpi.admin.recovery.removal")) {
            sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED)); return true
        }
        val request = UUID.randomUUID()
        try {
            if (args.getOrNull(1)?.equals("resolve", true) == true) {
                require(args.size >= 6) { "/limpi removal resolve <owner UUID> <회수 UUID> removed|retained <확인 근거>" }
                val owner = UUID.fromString(args[2]); val id = UUID.fromString(args[3])
                require(plugin.server.getPlayer(owner) == null) { "소유자가 오프라인일 때 원본/회수 후 슬롯과 저장 기록을 확인하세요." }
                val removed = when (args[4].lowercase(java.util.Locale.ROOT)) { "removed" -> true; "retained" -> false; else -> error("removed 또는 retained를 지정하세요.") }
                FileManager.removals.resolve(request, (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE", owner, id, removed, args.drop(5).joinToString(" "))
                sender.sendMessage(Component.text("회수 $id 결정을 기록했습니다. 감사 거래: $request", NamedTextColor.GREEN))
            } else {
                require(args.size in 2..3) { "/limpi removal <owner UUID> [회수 UUID]" }
                val owner = UUID.fromString(args[1])
                val records = if (args.size == 3) listOfNotNull(FileManager.removals.find(owner, UUID.fromString(args[2]))) else FileManager.removals.pending(owner)
                if (records.isEmpty()) sender.sendMessage(Component.text("조회할 회수 기록이 없습니다.", NamedTextColor.GRAY))
                for (record in records) {
                    sender.sendMessage(Component.text("${record.evidence.id} / ${record.kind} / ${record.state} / checkpoint ${record.evidence.checkpoint}", NamedTextColor.WHITE))
                    sender.sendMessage(Component.text("원본/이후 슬롯: ${record.evidence.before.size}/${record.evidence.after.size}, ${record.reason ?: ""}", NamedTextColor.GRAY))
                }
            }
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "조회 조건을 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Removal command $request failed", failure)
            sender.sendMessage(Component.text("회수 상태/저장소를 확인해야 합니다. 거래 UUID: $request", NamedTextColor.RED))
        }
        return true
    }
}
