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
    private val screens: List<CasinoScreen> = listOf(MinesScreen(plugin), SlotScreen(plugin), BlackjackScreen(plugin), CrashScreen(plugin), RaceScreen(plugin))
    private val byCode = screens.associateBy { it.code }
    private var task: BukkitTask? = null

    private data class Entry(val slot: Int, val code: String, val material: Material, val name: String, val lore: List<String>)
    private val entries = listOf(
        Entry(11, "mines", Material.TNT, "마인즈", listOf("5x5 판에서 지뢰를 피해 칸을 엽니다.", "열수록 배율이 오르고, 언제든 수령할 수 있습니다.")),
        Entry(12, "slot", Material.LEVER, "슬롯머신", listOf("광물 3x3 릴을 돌립니다.", "가로 3줄과 대각선 2줄에서 같은 광물을 맞추세요.")),
        Entry(13, "blackjack", Material.BLACK_DYE, "블랙잭", listOf("최대 4명이 딜러와 승부합니다.", "21에 가까울수록 이기고, 블랙잭은 3:2로 받습니다.")),
        Entry(14, "crash", Material.FIREWORK_ROCKET, "크래시", listOf("모두 함께 오르는 배율을 지켜봅니다.", "터지기 전에 수령하면 그 배율만큼 받습니다.")),
        Entry(15, "race", Material.WHITE_WOOL, "양 경주", listOf("양 6마리의 경주에 베팅합니다.", "우승 확률이 낮은 양일수록 배당이 큽니다.")),
    )

    fun start() { task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { screens.forEach { it.tick() } }, 1L, 1L) }

    fun stop() {
        task?.cancel(); task = null
        plugin.server.onlinePlayers.filter { (it.openInventory.topInventory.holder as? MenuHolder)?.kind == MenuKind.CASINO }.forEach { it.closeInventory() }
    }

    fun openGame(player: Player, code: String) = requireNotNull(byCode[code]) { "Unknown casino game $code" }.open(player)

    fun openMenu(player: Player) {
        val holder = CasinoUi.holder("카지노", 27)
        for (entry in entries) {
            holder.inventory.setItem(entry.slot, CasinoUi.item(entry.material, CasinoUi.text(entry.name, NamedTextColor.GOLD), entry.lore.map { CasinoUi.text(it) } + CasinoUi.balance(player.uniqueId)))
            holder.actions[entry.slot] = MenuAction.Casino("menu", "game", screens.indexOfFirst { it.code == entry.code })
        }
        openMenu(plugin, player, holder.inventory)
    }

    @EventHandler
    fun click(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.CASINO) return
        if (event.click !in setOf(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT)) return
        val player = event.whoClicked as? Player ?: return
        // Raw slots of the player's own inventory are past the window, where no action is ever registered.
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
