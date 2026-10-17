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
 * Items placed here live only in this window (as before). On "판매" the price is recomputed from the player's stored
 * sold counts and the credit, the updated counts and the receipt commit once (2.0.0); only then do the items belong to
 * the sale, so a retry or close can never pay twice or return sold items. The animation and message follow as before.
 */
class SellEvents : Listener {
    class SellHolder(val owner: UUID, val counters: Map<String, SaleCounter>) : InventoryHolder {
        private val view = Bukkit.createInventory(this, 54, SellGUIName)
        /** Slots already paid for; they are never returned on close. */
        var sold: Set<Int>? = null
        override fun getInventory(): Inventory = view
    }

    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        val SellGUIName = Component.text("LIMPI: 판매창", NamedTextColor.YELLOW, TextDecoration.BOLD)
        private const val SELL_SLOT = 53

        fun catalog(): SellCatalog = SellCatalog.of(FileManager.getSellPriceConfig())

        /** SellRecoveryHours: how long a player's sold count takes to halve. A positive integer. */
        fun recoveryMillis(): Long {
            val raw = plugin.config.get("SellRecoveryHours", 12)
            require((raw is Int || raw is Long) && (raw as Number).toLong() in 1..1_000_000) { "SellRecoveryHours는 1 이상의 정수여야 합니다." }
            return java.util.concurrent.TimeUnit.HOURS.toMillis(raw.toLong())
        }

        private fun lines(inventory: Inventory): List<SaleLine> = (0 until SELL_SLOT).mapNotNull { slot ->
            val item = inventory.getItem(slot) ?: return@mapNotNull null
            if (item.type.isAir || item.amount <= 0) null else SaleLine(slot, item.type.name.lowercase(Locale.ROOT), item.amount.toLong())
        }

        /** The preview on the gold ingot uses the counts read when the window opened; the sale itself rereads them. */
        private fun total(holder: SellHolder, lines: List<SaleLine> = lines(holder.inventory)): Money = try {
            catalog().quote(lines, holder.counters, System.currentTimeMillis(), recoveryMillis()).total
        } catch (_: Exception) { Money(0) }

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
            val holder = SellHolder(player.uniqueId, FileManager.sales.counters(player.uniqueId))
            holder.inventory.setItem(SELL_SLOT, sellButton(Money(0)))
            player.openInventory(holder.inventory)
            refresh(player, holder)
        }

        /**
         * Keep the total on the gold ingot current while the window is open (the original 1-tick updater). The quote is
         * recomputed when the items change, and once a second for the sold-count recovery over time; the button is
         * only replaced when the total changes.
         */
        private fun refresh(player: Player, holder: SellHolder) {
            object : BukkitRunnable() {
                var lastLines: List<SaleLine>? = null
                var lastTotal: Money? = null
                var age = 0
                override fun run() {
                    if (!player.isOnline || player.openInventory.topInventory !== holder.inventory || holder.sold != null) { cancel(); return }
                    val lines = lines(holder.inventory)
                    if (lines == lastLines && ++age < 20) return
                    lastLines = lines; age = 0
                    val total = total(holder, lines)
                    if (total == lastTotal && holder.inventory.getItem(SELL_SLOT)?.type == Material.GOLD_INGOT) return
                    lastTotal = total
                    holder.inventory.setItem(SELL_SLOT, sellButton(total))
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

        /** Return everything still on an open sale window, e.g. on plugin disable. A started sale is already paid. */
        fun closeAll() {
            for (player in Bukkit.getOnlinePlayers()) {
                val holder = player.openInventory.topInventory.holder as? SellHolder ?: continue
                returnItems(player, holder)
                player.closeInventory()
            }
        }

        /** As before: the items break one by one and the message follows (the money was committed on the click). */
        private fun animate(player: Player, holder: SellHolder, total: Money) {
            val inventory = holder.inventory
            object : BukkitRunnable() {
                var timer = 0
                override fun run() {
                    if (!player.isOnline) { cancel(); return }
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
        val lines = lines(top)
        if (lines.isEmpty()) {
            player.sendMessage(Component.text("아이템을 올려놓고 눌러주세요.", NamedTextColor.RED))
            return
        }
        val request = UUID.randomUUID()
        val receipt = try {
            FileManager.sales.sell(request, player.uniqueId, lines, catalog(), System.currentTimeMillis(), recoveryMillis()) { line, failure ->
                plugin.logger.warning("Invalid sale price for ${line.code}: ${failure.message}")
            }
        } catch (failure: BankException) {
            EconomyText.bankFailure(player, failure); return
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Sale $request for ${player.uniqueId} failed", failure)
            player.sendMessage(Component.text("판매하지 못했습니다. 관리자에게 문의하세요.", NamedTextColor.RED)); return
        }
        val total = Money(receipt.total)
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
