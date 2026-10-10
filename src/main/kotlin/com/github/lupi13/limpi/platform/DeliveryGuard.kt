package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.delivery.DeliveryJournal
import com.github.lupi13.limpi.delivery.DeliveryState
import com.github.lupi13.limpi.delivery.RemovalJournal
import com.github.lupi13.limpi.delivery.RemovalState
import com.github.lupi13.limpi.market.MarketEscrow
import com.github.lupi13.limpi.market.EscrowState
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.*
import java.util.logging.Level
import java.util.logging.Logger

/** Block queued client inventory actions while a failed operation is being disconnected. */
class DeliveryGuard(private val controller: DeliveryController, private val journal: DeliveryJournal, private val escrow: MarketEscrow, private val removals: RemovalJournal, private val logger: Logger) : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun login(event: AsyncPlayerPreLoginEvent) {
        try {
            if (journal.pending(event.uniqueId).any { it.state == DeliveryState.REVIEW } || escrow.pending(event.uniqueId).any { it.state == EscrowState.REVIEW } || removals.pending(event.uniqueId).any { it.state == RemovalState.REVIEW }) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("지급 검토가 필요합니다. 관리자에게 UUID ${event.uniqueId}를 알려주세요."))
            }
        } catch (failure: Exception) {
            logger.log(Level.SEVERE, "Unable to inspect deliveries before login for ${event.uniqueId}", failure)
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("지급 저장소를 확인할 수 없습니다. 관리자에게 문의하세요."))
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    fun click(event: InventoryClickEvent) { if (controller.blocked(event.whoClicked.uniqueId)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun drag(event: InventoryDragEvent) { if (controller.blocked(event.whoClicked.uniqueId)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun drop(event: PlayerDropItemEvent) { if (controller.blocked(event.player.uniqueId)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun pickup(event: EntityPickupItemEvent) { if (event.entity is Player && controller.blocked(event.entity.uniqueId)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun swap(event: PlayerSwapHandItemsEvent) { if (controller.blocked(event.player.uniqueId)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun interact(event: PlayerInteractEvent) { if (controller.blocked(event.player.uniqueId)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun command(event: PlayerCommandPreprocessEvent) { if (controller.blocked(event.player.uniqueId)) event.isCancelled = true }
}
