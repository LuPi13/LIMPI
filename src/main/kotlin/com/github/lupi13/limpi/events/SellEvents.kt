package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.EconomyText
import com.github.lupi13.limpi.economy.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

/**
 * The original sale window: put items in freely, press the gold ingot, watch them break one by one.
 * Items placed here live only in this window (as before). On "판매" the credit is committed once with its own ledger
 * entry, and only then are the sold slots cleared, so a retry or close can never pay twice or return sold items.
 */
class SellEvents : Listener {
    class SellHolder(val owner: UUID) : InventoryHolder {
        private val view = Bukkit.createInventory(this, 54, SellGUIName)
        /** Slots already paid for; they are never returned on close. */
        var sold: Set<Int>? = null
        /** Credits the sale once; also run on plugin disable so a stopped animation still pays. */
        var settle: (() -> Boolean)? = null
        override fun getInventory(): Inventory = view
    }

    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        val SellGUIName = Component.text("LIMPI: 판매창", NamedTextColor.YELLOW, TextDecoration.BOLD)
        private const val SELL_SLOT = 53
        private val pending = mutableSetOf<SellHolder>()

        /** Configured price per item, or 1 per item when unlisted (as before). */
        private fun price(item: ItemStack): Money? {
            return CatalogPrices.sell(FileManager.getSellPriceConfig().get(item.type.name.lowercase(Locale.ROOT))).times(item.amount.toLong())
        }

        private fun total(inventory: Inventory): Money {
            var sum = Money(0)
            for (slot in 0 until SELL_SLOT) {
                val item = inventory.getItem(slot) ?: continue
                sum += try { price(item) ?: continue } catch (_: Exception) { continue }
            }
            return sum
        }

        private fun sellButton(total: Money, processing: String? = null): ItemStack {
            val close = ItemStack(Material.GOLD_INGOT, 1)
            val meta = close.itemMeta
            meta.customName(Component.text("판매", NamedTextColor.RED))
            val lore = mutableListOf<Component>(moneyDisplay(total.units).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("원", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)))
            if (processing != null) lore.add(Component.text(processing, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            meta.lore(lore)
            close.itemMeta = meta
            return close
        }

        fun openSellGUI(player: Player) {
            val holder = SellHolder(player.uniqueId)
            holder.inventory.setItem(SELL_SLOT, sellButton(Money(0)))
            player.openInventory(holder.inventory)
            refresh(player, holder)
        }

        /** Keep the total on the gold ingot current while the window is open (the original 1-tick updater). */
        private fun refresh(player: Player, holder: SellHolder) {
            object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline || player.openInventory.topInventory !== holder.inventory || holder.sold != null) { cancel(); return }
                    holder.inventory.setItem(SELL_SLOT, sellButton(total(holder.inventory)))
                }
            }.runTaskTimer(plugin, 1L, 1L)
        }

        private fun returnItems(player: Player, holder: SellHolder) {
            val sold = holder.sold ?: emptySet()
            for (slot in 0 until SELL_SLOT) {
                if (slot in sold) continue
                val item = holder.inventory.getItem(slot) ?: continue
                holder.inventory.setItem(slot, null)
                player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
            }
        }

        /** Return everything still on an open sale window, e.g. on plugin disable. */
        fun closeAll() {
            for (holder in pending.toList()) holder.settle?.invoke()
            for (player in Bukkit.getOnlinePlayers()) {
                val holder = player.openInventory.topInventory.holder as? SellHolder ?: continue
                returnItems(player, holder)
                player.closeInventory()
            }
        }

        /** As before: the items break one by one and the money is credited when the animation ends (even if closed). */
        private fun animate(player: Player, holder: SellHolder, total: Money) {
            val inventory = holder.inventory
            val request = UUID.randomUUID()
            val owner = player.uniqueId
            var credited = false
            holder.settle = settle@{
                if (credited) return@settle true
                try {
                    if (total.units > 0) FileManager.bank.adjust(request, "SELL_GUI", owner, total.units)
                    credited = true; pending.remove(holder); true
                } catch (failure: Exception) {
                    plugin.logger.log(Level.SEVERE, "Sale $request of ${total.units} for $owner failed", failure)
                    pending.remove(holder)
                    if (player.isOnline && failure is BankException) EconomyText.bankFailure(player, failure)
                    false
                }
            }
            pending.add(holder)
            object : BukkitRunnable() {
                var timer = 0
                override fun run() {
                    if (timer >= 52 || !player.isOnline) {
                        if (holder.settle?.invoke() != true) {
                            if (player.isOnline && player.openInventory.topInventory === inventory) player.closeInventory()
                            cancel(); return
                        }
                        if (!player.isOnline) { cancel(); return }
                    }
                    val open = player.openInventory.topInventory === inventory
                    if (open && timer < SELL_SLOT && inventory.getItem(timer) != null) {
                        inventory.setItem(timer, null)
                        player.playSound(player.location, Sound.BLOCK_CHAIN_BREAK, 0.8f, (Math.random() * 0.4 + 0.9).toFloat())
                    }
                    if (open) inventory.setItem(SELL_SLOT, sellButton(total, "처리중" + ".".repeat((timer / 2) % 3 + 1)))
                    if (timer >= 52) {
                        player.sendMessage(moneyDisplay(total.units)
                            .append(Component.text("원이 계좌에 입금되었습니다.", NamedTextColor.WHITE)))
                        if (player.openInventory.topInventory === inventory) player.closeInventory()
                        cancel()
                        return
                    }
                    timer++
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun click(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? SellHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (holder.sold != null) {
            player.sendMessage(Component.text("판매중입니다. 잠시만 기다려주세요.", NamedTextColor.RED))
            event.isCancelled = true
            return
        }
        if (FileManager.delivery.blocked(player.uniqueId)) { event.isCancelled = true; return }
        // Items move freely as before; only the button slot is fixed.
        if (event.rawSlot != SELL_SLOT) return
        event.isCancelled = true
        val prices = (0 until SELL_SLOT).mapNotNull { slot ->
            val item = top.getItem(slot) ?: return@mapNotNull null
            val price = try { price(item) } catch (failure: Exception) {
                plugin.logger.warning("Invalid sale price for ${item.type}: ${failure.message}"); null
            } ?: return@mapNotNull null
            slot to price
        }
        if (prices.isEmpty()) {
            player.sendMessage(Component.text("아이템을 올려놓고 눌러주세요.", NamedTextColor.RED))
            return
        }
        val total = try { prices.fold(Money(0)) { sum, (_, price) -> sum + price } } catch (_: ArithmeticException) {
            EconomyText.bankFailure(player, BankException(BankFailure.OVERFLOW)); return
        }
        // From here the window's items belong to the sale, exactly as the original timer>=0 state.
        holder.sold = (0 until SELL_SLOT).toSet()
        animate(player, holder, total)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun drag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? SellHolder ?: return
        if (holder.sold != null || SELL_SLOT in event.rawSlots) event.isCancelled = true
    }

    @EventHandler
    fun close(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? SellHolder ?: return
        val player = event.player as? Player ?: return
        returnItems(player, holder)
    }
}
