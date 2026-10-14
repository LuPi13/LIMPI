package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.notice.Notice
import com.github.lupi13.limpi.notice.NoticeId
import com.github.lupi13.limpi.notice.NoticeInputError
import com.github.lupi13.limpi.notice.NoticeText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.LocalDate
import java.util.logging.Level

class Notice(private val plugin: Plugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "notice", input, CommandDefinitions.notice, true) ?: return true
        try {
            when (route.id) {
                "help" -> CommandHelp.show(plugin, sender, "notice", CommandDefinitions.notice, true, admin = false)
                "help admin" -> CommandHelp.show(plugin, sender, "notice", CommandDefinitions.notice, true, admin = true)
                "write" -> write(sender, args.drop(1).joinToString(" "))
                "show" -> {
                    val date = NoticeId.parseDate(args[1])
                    val notices = if (args.size == 3) listOfNotNull(FileManager.notices.find(NoticeId(date, NoticeId.parseNumber(args[2]))))
                        else FileManager.notices.onDate(date, ::damaged)
                    if (notices.isEmpty()) throw NoticeInputError(if (args.size == 3) "해당 공지를 찾을 수 없습니다." else "해당 날짜의 공지가 없습니다.")
                    show(sender, notices)
                }
                "list" -> list(sender)
                "unread" -> {
                    val player = sender as Player
                    val unread = FileManager.notices.unread(player.uniqueId)
                    if (unread.isEmpty()) player.sendMessage(Component.text("읽지 않은 공지가 없습니다.", NamedTextColor.WHITE))
                    else show(player, unread)
                }
                "delete" -> {
                    val id = NoticeId(NoticeId.parseDate(args[1]), NoticeId.parseNumber(args[2]))
                    if (!FileManager.notices.delete(id)) throw NoticeInputError("해당 공지를 찾을 수 없습니다.")
                    plugin.logger.info("Notice ${id.label} deleted by ${sender.name}")
                    sender.sendMessage(Component.text("공지 ${id.label}을(를) 삭제했습니다.", NamedTextColor.GREEN))
                }
                else -> error("Unbound notice command ${route.id}")
            }
        } catch (failure: NoticeInputError) {
            sender.sendMessage(Component.text(failure.message ?: "입력을 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Notice command ${route.id} from ${sender.name} failed", failure)
            sender.sendMessage(Component.text("공지를 처리하지 못했습니다. 관리자에게 문의하세요.", NamedTextColor.RED))
        }
        return true
    }

    private fun write(sender: CommandSender, input: String) {
        val json = NoticeText.serialize(NoticeText.parse(input))
        val today = LocalDate.now()
        val notice = FileManager.notices.write(json, (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE", today, System.currentTimeMillis())
        plugin.logger.info("Notice ${notice.id.label} written by ${sender.name}: $json")
        prune(plugin, today)
        val message = render(notice)
        for (player in plugin.server.onlinePlayers) {
            player.sendMessage(message)
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f)
            // Each player separately, so one damaged read list does not leave the others unread.
            try { FileManager.notices.markRead(listOf(player.uniqueId), listOf(notice.id)) }
            catch (failure: Exception) { plugin.logger.log(Level.WARNING, "Unable to mark notice ${notice.id.label} read for ${player.name}", failure) }
        }
        if (sender !is Player) sender.sendMessage(message)
        sender.sendMessage(Component.text("공지 ${notice.id.label}을(를) 등록했습니다.", NamedTextColor.GREEN))
    }

    private fun show(sender: CommandSender, notices: List<Notice>) {
        notices.forEach { sender.sendMessage(render(it)) }
        if (sender is Player) try { FileManager.notices.markRead(listOf(sender.uniqueId), notices.map { it.id }) }
            catch (failure: Exception) { plugin.logger.log(Level.WARNING, "Unable to mark notices read for ${sender.name}", failure) }
    }

    private fun list(sender: CommandSender) {
        val notices = FileManager.notices.all(::damaged)
        if (notices.isEmpty()) { sender.sendMessage(Component.text("보관 중인 공지가 없습니다.", NamedTextColor.WHITE)); return }
        val unread = (sender as? Player)?.let { player ->
            runCatching { FileManager.notices.unread(player.uniqueId).map { it.id }.toSet() }.getOrDefault(emptySet())
        }.orEmpty()
        val days = retentionDays(plugin)
        sender.sendMessage(Component.text("보관 중인 공지" + if (days > 0) " (${days}일 보관)" else "", NamedTextColor.GOLD))
        for ((date, group) in notices.groupBy { it.id.date }) {
            var line = Component.text(date.toString(), NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("클릭하여 이 날짜의 공지 모두 보기", NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.runCommand("/notice show $date"))
                .append(Component.text(":", NamedTextColor.WHITE))
            for (notice in group) {
                val number = "%03d".format(notice.id.number)
                line = line.append(Component.text(" ")).append(Component.text("[$number]", if (notice.id in unread) NamedTextColor.YELLOW else NamedTextColor.GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text("클릭하여 공지 보기", NamedTextColor.WHITE)))
                    .clickEvent(ClickEvent.runCommand("/notice show $date $number")))
            }
            sender.sendMessage(line)
        }
        if (sender is Player && unread.isNotEmpty()) sender.sendMessage(Component.text("노란색 번호는 읽지 않은 공지입니다.", NamedTextColor.GRAY))
    }

    private fun damaged(key: String, failure: Exception) = plugin.logger.log(Level.WARNING, "Skipping damaged notice $key", failure)

    companion object {
        /**
         * The broadcast format:
         *
         * 2026-10-14 (001)
         * 공지입니다.
         */
        fun render(notice: Notice): Component = Component.text()
            .append(Component.newline())
            .append(Component.text(notice.id.label, NamedTextColor.GOLD))
            .append(Component.newline())
            .append(NoticeText.deserialize(notice.json))
            .build()

        fun retentionDays(plugin: Plugin): Int = plugin.config.getInt("NoticeRetentionDays", 30)

        fun prune(plugin: Plugin, today: LocalDate = LocalDate.now()) {
            val removed = FileManager.notices.prune(today, retentionDays(plugin))
            if (removed > 0) plugin.logger.info("Pruned $removed notices older than ${retentionDays(plugin)} days")
        }

        /** Shown on join after the welcome message. */
        fun announceUnread(plugin: Plugin, player: Player) {
            if (!player.hasPermission("limpi.notice.show")) return
            val count = try { FileManager.notices.unread(player.uniqueId).size } catch (failure: Exception) {
                plugin.logger.log(Level.WARNING, "Unable to count unread notices for ${player.name}", failure); return
            }
            if (count == 0) return
            player.sendMessage(Component.text("읽지 않은 공지가 ${count}건 있습니다! ", NamedTextColor.YELLOW)
                .append(Component.text("여기를 눌러 공지를 확인하세요.", NamedTextColor.LIGHT_PURPLE))
                .hoverEvent(HoverEvent.showText(Component.text("클릭하여 명령 실행", NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.runCommand("/notice unread")))
        }
    }
}

class NoticeTab(private val plugin: Plugin) : TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = try {
        CommandDefinitions.notice.complete(args.toList(), true, sender is Player, sender::hasPermission) { kind ->
            when (kind) {
                "noticeDates" -> FileManager.notices.all().map { it.id.date.toString() }.distinct()
                "noticeNumbers" -> runCatching { LocalDate.parse(args[1]) }.getOrNull()
                    ?.let { date -> FileManager.notices.onDate(date).map { "%03d".format(it.id.number) } }.orEmpty()
                else -> emptyList()
            }
        }
    } catch (failure: Exception) {
        plugin.logger.log(Level.WARNING, "Unable to complete ${command.name} for ${sender.name}", failure)
        emptyList()
    }
}
