package com.github.lupi13.limpi.platform

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*

/** One movement policy covers PDC tickets, including automatic inventory transfers. */
class AbilityTicketGuard : Listener {
    private fun storage(type: InventoryType) = type in setOf(
        InventoryType.BARREL, InventoryType.CREATIVE, InventoryType.CHEST, InventoryType.DECORATED_POT,
        InventoryType.DISPENSER, InventoryType.DROPPER, InventoryType.ENDER_CHEST, InventoryType.HOPPER,
        InventoryType.PLAYER, InventoryType.SHULKER_BOX)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun click(event: InventoryClickEvent) {
        val clicked = event.clickedInventory ?: return
        val player = event.whoClicked as? Player ?: return
        val swapped = when {
            event.click == ClickType.SWAP_OFFHAND -> player.inventory.itemInOffHand
            event.hotbarButton in 0..8 -> player.inventory.getItem(event.hotbarButton)
            else -> null
        }
        val protected = AbilityTickets.protected(event.currentItem) || AbilityTickets.protected(event.cursor) || AbilityTickets.protected(swapped)
        if (!protected) return
        if (!storage(clicked.type)) event.isCancelled = true
        if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY && clicked === event.view.bottomInventory &&
            !storage(event.view.topInventory.type)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun drag(event: InventoryDragEvent) {
        if (AbilityTickets.protected(event.oldCursor) && !storage(event.view.topInventory.type) &&
            event.rawSlots.any { it < event.view.topInventory.size }) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun transfer(event: InventoryMoveItemEvent) {
        if (AbilityTickets.protected(event.item) && !storage(event.destination.type)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun pickup(event: InventoryPickupItemEvent) {
        if (AbilityTickets.protected(event.item.itemStack) && !storage(event.inventory.type)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun prepareCraft(event: PrepareItemCraftEvent) {
        if (event.inventory.matrix.any(AbilityTickets::protected)) event.inventory.result = null
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun craft(event: CraftItemEvent) {
        if (event.inventory.matrix.any(AbilityTickets::protected)) event.isCancelled = true
    }
}
