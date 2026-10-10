package com.github.lupi13.limpi.menu

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.plugin.Plugin

enum class MenuKind { STOCK, SHOP, SELL, MARKET, ABILITY_SELECT, ABILITY_DICTIONARY, ABILITY_SHOP, ABILITY_ENEMY }
sealed interface MenuAction {
    data class Page(val index: Int) : MenuAction
    data class AbilityCode(val code: String) : MenuAction
    data class AbilitySort(val ordinal: Int) : MenuAction
    data class StockQuote(val code: String, val price: Long) : MenuAction
    data class MarketQuote(val id: java.util.UUID, val price: Long) : MenuAction
    data class ShopQuote(val code: String, val price: Long) : MenuAction
    data class ItemExchangeQuote(val product: String, val price: Long, val reward: com.github.lupi13.limpi.delivery.ItemGrant) : MenuAction
    data class SaleSlot(val index: Int, val stack: com.github.lupi13.limpi.delivery.StoredStack, val price: Long, val selected: Boolean) : MenuAction
    data class SaleConfirm(val before: List<com.github.lupi13.limpi.delivery.StoredStack?>, val prices: Map<Int, Long>) : MenuAction
    data object SaleRefresh : MenuAction
    data object SaleClear : MenuAction
}

/** Server-owned identity and actions: titles and item names carry no authority. */
class MenuHolder(val kind: MenuKind, val page: Int, title: Component) : InventoryHolder {
    private val view = Bukkit.createInventory(this, 54, title)
    val actions = mutableMapOf<Int, MenuAction>()
    override fun getInventory(): Inventory = view
}

class ReadOnlyMenuListener : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun click(event: InventoryClickEvent) {
        if (event.view.topInventory.holder is MenuHolder) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun drag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is MenuHolder && event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }
}

fun openMenu(plugin: Plugin, player: Player, inventory: Inventory, expected: Inventory? = null) {
    plugin.server.scheduler.runTask(plugin, Runnable {
        if (player.isOnline && !player.isDead && (expected == null || player.openInventory.topInventory === expected)) {
            player.openInventory(inventory)
        }
    })
}
