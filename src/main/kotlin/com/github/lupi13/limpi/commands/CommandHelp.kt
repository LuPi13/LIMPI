package com.github.lupi13.limpi.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * /<root> help prints how to use each command in chat: one line per command, the full description on hover, a click
 * runs a command without arguments or fills the chat box otherwise. Explanations live on the web pages
 * (config HelpUrl) and in dedicated commands such as /ability rates. Administrative commands are under /<root> help admin.
 */
object CommandHelp {
    val ALIASES = mapOf("ability" to listOf("ab"), "financial" to listOf("fi", "fc"), "chest" to listOf("ch"), "notice" to listOf("no", "nt"))

    fun show(plugin: Plugin, sender: CommandSender, root: String, index: CommandIndex, enabled: Boolean, admin: Boolean) {
        val web = WebHelp.links(plugin, sender)
        lines(root, index, enabled, admin, sender is Player, sender::hasPermission, web).forEach(sender::sendMessage)
        // The console cannot click; print the addresses instead.
        if (sender !is Player) web.filter { it.admin == admin }.forEach { sender.sendMessage("${it.label}: ${it.url}") }
    }

    fun lines(root: String, index: CommandIndex, enabled: Boolean, admin: Boolean, player: Boolean,
              hasPermission: (String) -> Boolean, web: List<WebHelp.Link>): List<Component> {
        val visible = index.visible(enabled, player, hasPermission)
        val routes = visible.filter { it.path.first() != "help" && it.admin == admin }
        val title = if (admin) "/$root 관리자 명령어" else "/$root 도움말"
        val lines = mutableListOf(header(title, web.filter { it.admin == admin }))
        if (!admin) ALIASES[root]?.let { aliases ->
            lines += Component.text(aliases.joinToString(", ") { "/$it" } + " 로도 입력할 수 있습니다.", NamedTextColor.GRAY)
        }
        if (!enabled) lines += Component.text("이 시스템은 꺼져 있어 일부 명령만 쓸 수 있습니다.", NamedTextColor.RED)
        routes.mapTo(lines) { line(root, it) }
        if (routes.isEmpty()) lines += Component.text("사용할 수 있는 명령어가 없습니다.", NamedTextColor.GRAY)
        val other = visible.firstOrNull { it.path.first() == "help" && it.adminHelp != admin }
        if (other != null) lines += link(if (admin) "[일반 명령어 보기]" else "[관리자 명령어 보기]", "/$root ${other.path.joinToString(" ")}",
            if (admin) NamedTextColor.AQUA else NamedTextColor.RED)
        return lines
    }

    fun header(title: String, web: List<WebHelp.Link>): Component = web.fold(
        Component.text("━━━━━ $title ━━━━━", NamedTextColor.GOLD)) { line, link -> line.append(Component.text(" ")).append(WebHelp.button(link)) }

    fun line(root: String, route: CommandRoute): Component {
        val command = "/$root ${route.path.joinToString(" ")}"
        return Component.text("/$root ${route.usage}", NamedTextColor.GREEN)
            .append(Component.text(" - ${route.short}", NamedTextColor.GRAY))
            .hoverEvent(HoverEvent.showText(Component.text(route.description, NamedTextColor.WHITE)
                .append(Component.text(if (route.bare) "\n클릭하여 실행" else "\n클릭하여 입력", NamedTextColor.GRAY))))
            .clickEvent(if (route.bare) ClickEvent.runCommand(command) else ClickEvent.suggestCommand("$command "))
    }

    fun link(label: String, command: String, color: NamedTextColor): Component = Component.text(label, color)
        .decoration(TextDecoration.UNDERLINED, true)
        .hoverEvent(HoverEvent.showText(Component.text("클릭하여 명령 실행", NamedTextColor.WHITE)))
        .clickEvent(ClickEvent.runCommand(command))
}
