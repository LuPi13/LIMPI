package com.github.lupi13.limpi.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta

object CommandHelp {
    fun show(sender: CommandSender, root: String, index: CommandIndex, enabled: Boolean, extra: List<Component> = emptyList(),
             web: List<WebHelp.Link> = emptyList()) {
        val routes = index.visible(enabled, sender is Player, sender::hasPermission)
        if (sender !is Player) {
            routes.forEach { sender.sendMessage("/$root ${it.usage}: ${it.description}") }
            extra.forEach(sender::sendMessage)
            WebHelp.console(sender, web)
            return
        }
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta
        meta.title = "/$root 도움말"; meta.author = "LIMPI"
        for (group in routes.chunked(2)) {
            var page = Component.empty()
            for (route in group) page = page.append(Component.text("/$root ${route.usage}\n", NamedTextColor.DARK_GREEN))
                .append(Component.text("${route.description}\n\n", NamedTextColor.BLACK))
            meta.addPages(page)
        }
        extra.forEach(meta::addPages)
        if (web.isNotEmpty()) meta.addPages(Component.text("[/$root 도움말]", NamedTextColor.DARK_AQUA).append(WebHelp.component(web)))
        book.itemMeta = meta
        sender.openBook(book)
    }
}
