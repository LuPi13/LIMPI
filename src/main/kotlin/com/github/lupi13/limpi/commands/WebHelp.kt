package com.github.lupi13.limpi.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin

/** Links from the in-game help to the published command pages (config HelpUrl; blank hides them). */
object WebHelp {
    data class Link(val label: String, val url: String)

    fun links(base: String?, admin: Boolean): List<Link> {
        val root = base?.trim().orEmpty().ifEmpty { return emptyList() }.let { if (it.endsWith('/')) it else "$it/" }
        return listOfNotNull(Link("웹 도움말", "${root}commands/"), if (admin) Link("관리자 명령어", "${root}admin/") else null)
    }

    fun links(plugin: Plugin, sender: CommandSender): List<Link> = links(plugin.config.getString("HelpUrl"), admin(sender))

    /** Whether the sender can use any administrative ability/financial/chest command. */
    fun admin(sender: CommandSender): Boolean = (CommandDefinitions.ability.routes + CommandDefinitions.financial.routes + CommandDefinitions.chest.routes)
        .any { route -> route.permission?.contains(".admin.") == true && sender.hasPermission(route.permission) }

    /** Clickable lines for a help book page; empty when no link is configured. */
    fun component(links: List<Link>): Component = links.fold(Component.empty()) { page, link ->
        page.append(Component.text("\n\n")).append(Component.text("[${link.label}]", NamedTextColor.BLUE)
            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.UNDERLINED, true)
            .hoverEvent(HoverEvent.showText(Component.text("클릭하면 브라우저에서 엽니다.", NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.openUrl(link.url)))
    }

    fun console(sender: CommandSender, links: List<Link>) = links.forEach { sender.sendMessage("${it.label}: ${it.url}") }
}
