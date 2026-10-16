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
    data class Link(val label: String, val url: String, val admin: Boolean = false)

    private fun root(base: String?): String? = base?.trim()?.ifEmpty { null }?.let { if (it.endsWith('/')) it else "$it/" }

    /**
     * [system] (ability, financial, chest, notice) links to that system's page and its section of the admin page;
     * null (/limpi help) links to the front page and the top of the admin page.
     */
    fun links(base: String?, admin: Boolean, system: String?): List<Link> {
        val root = root(base) ?: return emptyList()
        return listOfNotNull(Link("웹 도움말", if (system == null) root else "$root$system/"),
            if (admin) Link("관리자 명령어", if (system == null) "${root}admin/" else "${root}admin/#$system", true) else null)
    }

    fun links(plugin: Plugin, sender: CommandSender, system: String?): List<Link> = links(plugin.config.getString("HelpUrl"), admin(sender), system)

    /** The site's front page, or null when no address is configured. */
    fun home(base: String?): String? = root(base)

    /** Whether the sender can use any administrative command. */
    fun admin(sender: CommandSender): Boolean = (CommandDefinitions.all.values + CommandDefinitions.limpi).flatMap { it.routes }
        .any { route -> route.admin && sender.hasPermission(route.permission!!) }

    fun button(link: Link): Component = Component.text("[${link.label}]", NamedTextColor.BLUE)
        .decoration(TextDecoration.UNDERLINED, true)
        .hoverEvent(HoverEvent.showText(Component.text("클릭하면 브라우저에서 엽니다.", NamedTextColor.GRAY)))
        .clickEvent(ClickEvent.openUrl(link.url))
}
