package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.menu.MenuAction
import com.github.lupi13.limpi.menu.MenuHolder
import com.github.lupi13.limpi.menu.MenuKind
import com.github.lupi13.limpi.menu.openMenu
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/** One game's screens. Screens read the truth from the services; closing a screen never ends a round. */
interface CasinoScreen {
    val code: String
    fun open(player: Player)
    fun click(player: Player, holder: MenuHolder, action: MenuAction.Casino, click: ClickType)
    fun closed(player: Player, holder: MenuHolder) {}
    fun quit(player: Player) {}
    fun tick() {}
    fun release(id: UUID) {}
}

class CasinoEvents(private val plugin: Plugin) : Listener {
    private val screens: List<CasinoScreen> = listOf(MinesScreen(plugin))
    private val byCode = screens.associateBy { it.code }
    private var task: BukkitTask? = null

    private data class Entry(val slot: Int, val code: String, val material: Material, val name: String, val lore: List<String>)
    private val entries = listOf(
        Entry(13, "mines", Material.TNT, "마인즈", listOf("5x5 판에서 지뢰를 피해 칸을 엽니다.", "열수록 배율이 오르고, 언제든 수령할 수 있습니다.")),
    )

    fun start() { task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { screens.forEach { it.tick() } }, 1L, 1L) }

    fun stop() {
        task?.cancel(); task = null
        plugin.server.onlinePlayers.filter { (it.openInventory.topInventory.holder as? MenuHolder)?.kind == MenuKind.CASINO }.forEach { it.closeInventory() }
    }

    fun openGame(player: Player, code: String) = requireNotNull(byCode[code]) { "Unknown casino game $code" }.open(player)

    fun openMenu(player: Player) {
        val holder = CasinoUi.holder("카지노", 27)
        for (index in 0 until 27) holder.inventory.setItem(index, CasinoUi.filler())
        for (entry in entries) {
            holder.inventory.setItem(entry.slot, CasinoUi.item(entry.material, CasinoUi.text(entry.name, NamedTextColor.GOLD), entry.lore.map { CasinoUi.text(it) } + CasinoUi.balance(player.uniqueId)))
            holder.actions[entry.slot] = MenuAction.Casino("menu", "game", screens.indexOfFirst { it.code == entry.code })
        }
        openMenu(plugin, player, holder.inventory)
    }

    @EventHandler
    fun click(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.CASINO || event.clickedInventory !== event.view.topInventory) return
        if (event.click !in setOf(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT)) return
        val player = event.whoClicked as? Player ?: return
        val action = holder.actions[event.rawSlot] as? MenuAction.Casino ?: return
        if (action.screen == "menu") {
            if (action.action == "game") screens[action.argument].open(player) else openMenu(player)
            return
        }
        byCode[action.screen]?.click(player, holder, action, event.click)
    }

    @EventHandler
    fun close(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.CASINO) return
        val player = event.player as? Player ?: return
        screens.forEach { it.closed(player, holder) }
    }

    @EventHandler
    fun quit(event: PlayerQuitEvent) {
        screens.forEach { it.quit(event.player); it.release(event.player.uniqueId) }
        CasinoUi.release(event.player.uniqueId)
    }
}
