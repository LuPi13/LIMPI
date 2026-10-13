package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.chest.*
import com.github.lupi13.limpi.commands.EconomyText
import com.github.lupi13.limpi.delivery.StoredStack
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.menu.MenuAction
import com.github.lupi13.limpi.menu.MenuHolder
import com.github.lupi13.limpi.menu.MenuKind
import com.github.lupi13.limpi.menu.openMenu
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Level

/**
 * /chest window, shop, auto store and death drops. The window moves items freely like a vanilla chest; the DB keeps
 * the chest as opened until the close is committed together with a player data checkpoint (see ChestService).
 */
class ChestEvents : Listener {
    class ChestHolder(val owner: UUID, var session: ChestSession, ownerName: String) : InventoryHolder {
        private val view = Bukkit.createInventory(this, session.chestBefore.size, chestGUIName(ownerName))
        /** Set once the close is being committed; later clicks in a lingering view are refused. */
        var finished = false
        override fun getInventory(): Inventory = view
    }

    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        fun chestGUIName(ownerName: String) = Component.text("LIMPI: ${ownerName}님의 상자", NamedTextColor.GOLD, TextDecoration.BOLD)
        val chestShopGUIName = Component.text("LIMPI: 상자 상점", NamedTextColor.GOLD, TextDecoration.BOLD)
        private val sessions = mutableMapOf<UUID, ChestHolder>()

        val enabled: Boolean get() = plugin.config.getBoolean("EnableChestSystem")

        /** Configured prices, or null (after telling the player) when the configuration is malformed. */
        fun prices(player: Player? = null): ChestPrices? = try { ChestPrices.read(plugin.config) } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Invalid chest prices in config.yml; chest purchases are refused", failure)
            player?.sendMessage(Component.text("상자 가격 설정에 오류가 있습니다. 관리자에게 문의하세요.", NamedTextColor.RED))
            null
        }

        fun open(player: Player) {
            val owner = player.uniqueId
            if (FileManager.delivery.blocked(owner)) return
            sessions[owner]?.let { player.openInventory(it.inventory); return }
            if (!FileManager.delivery.claim(player)) return
            val inventory = FileManager.delivery.fullInventory(player)
            val before = inventory.snapshot()
            val session = FileManager.chests.open(owner, before)
            try {
                inventory.checkpoint(before, session.openCheckpoint)
            } catch (failure: Exception) {
                FileManager.delivery.fail(player, failure, "Chest window ${session.id} could not be opened for $owner", "상자 기록을 확인해야 합니다.")
                return
            }
            val holder = ChestHolder(owner, session, player.name)
            session.chestBefore.forEachIndexed { slot, stack -> stack?.let { holder.inventory.setItem(slot, PaperItemCodec.item(it.payload, it.count)) } }
            sessions[owner] = holder
            player.openInventory(holder.inventory)
            player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f)
            if (player.openInventory.topInventory !== holder.inventory) finish(player, closeView = false)
        }

        /** Commit the open window (if any). [closeView] also closes it when called outside the close event. */
        fun finish(player: Player, closeView: Boolean) {
            val holder = sessions.remove(player.uniqueId) ?: return
            holder.finished = true
            val view = player.openInventory
            val showing = view.topInventory === holder.inventory
            if (showing) {
                // Put a carried item back so it is part of the committed images instead of falling on the ground.
                val cursor = view.cursor
                if (!cursor.isEmpty) {
                    val left = holder.inventory.addItem(cursor).values.firstOrNull()?.let { player.inventory.addItem(it).values.firstOrNull() }
                    view.setCursor(left)
                }
            }
            try {
                val inventory = FileManager.delivery.fullInventory(player)
                val playerAfter = inventory.snapshot()
                val closing = FileManager.chests.closing(holder.session, playerAfter, image(holder.inventory))
                inventory.checkpoint(playerAfter, requireNotNull(closing.closeCheckpoint))
                FileManager.chests.complete(closing)
            } catch (failure: Exception) {
                FileManager.delivery.fail(player, failure, "Chest window ${holder.session.id} could not be saved for ${player.uniqueId}", "상자 기록을 확인해야 합니다.")
            }
            if (showing && closeView) player.closeInventory()
        }

        /**
         * Commit the window as it is now and keep it open under a fresh record. Used right after something leaves the
         * window onto the ground, so a crash cannot bring that item back into the chest while it also lies in the world.
         * Items on the cursor are in neither image until the window closes.
         */
        private fun checkpoint(player: Player) {
            val holder = sessions[player.uniqueId] ?: return
            if (holder.finished) return
            try {
                val inventory = FileManager.delivery.fullInventory(player)
                val current = inventory.snapshot()
                val closing = FileManager.chests.closing(holder.session, current, image(holder.inventory))
                inventory.checkpoint(current, requireNotNull(closing.closeCheckpoint))
                FileManager.chests.complete(closing)
                val next = FileManager.chests.open(player.uniqueId, current)
                inventory.checkpoint(current, next.openCheckpoint)
                holder.session = next
            } catch (failure: Exception) {
                sessions.remove(player.uniqueId); holder.finished = true
                FileManager.delivery.fail(player, failure, "Chest window ${holder.session.id} could not be checkpointed for ${player.uniqueId}", "상자 기록을 확인해야 합니다.")
            }
        }

        fun closeAll() = Bukkit.getOnlinePlayers().filter { it.uniqueId in sessions }.forEach { finish(it, closeView = true) }

        private fun image(inventory: Inventory): List<StoredStack?> = (0 until inventory.size).map { slot ->
            val item = inventory.getItem(slot)
            if (item == null || item.isEmpty) null else StoredStack(PaperItemCodec.payload(item), item.amount, maxOf(item.maxStackSize, item.amount))
        }

        fun openShop(player: Player, expected: Inventory? = null) {
            val state = FileManager.chests.state(player.uniqueId)
            val prices = prices(player) ?: return
            val holder = MenuHolder(MenuKind.CHEST_SHOP, 0, chestShopGUIName, 27)
            val balance = EconomyText.balanceDisplay(player.uniqueId)
            fun line(text: String, color: NamedTextColor = NamedTextColor.WHITE) = Component.text(text, color).decoration(TextDecoration.ITALIC, false)
            fun price(product: ChestProduct): List<Component> {
                val quote = prices.quote(state, product) ?: return emptyList()
                return listOf(line("가격: ").append(moneyDisplay(quote.units).decoration(TextDecoration.ITALIC, false))
                    .append(line("원, 현재 보유 금액: ")).append(balance.decoration(TextDecoration.ITALIC, false)).append(line("원")),
                    line("좌클릭하여 구매합니다."))
            }
            fun icon(slot: Int, material: Material, name: Component, lore: List<Component>, product: ChestProduct) {
                val item = ItemStack(material); val meta = item.itemMeta
                meta.customName(name.decoration(TextDecoration.ITALIC, false)); meta.lore(lore); item.itemMeta = meta
                holder.inventory.setItem(slot, item)
                prices.quote(state, product)?.let { holder.actions[slot] = MenuAction.ChestQuote(product.name, it.units) }
            }
            val locked = listOf(line("먼저 상자를 해금하세요.", NamedTextColor.GRAY))
            if (!state.unlocked) icon(11, Material.CHEST, Component.text("상자 해금", NamedTextColor.GOLD, TextDecoration.BOLD),
                listOf(line("9칸(1줄)짜리 개인 상자를 엽니다."), line("/chest로 언제든 열 수 있습니다.")) + price(ChestProduct.UNLOCK), ChestProduct.UNLOCK)
            else icon(11, Material.CHEST, Component.text("상자 업그레이드", NamedTextColor.GOLD, TextDecoration.BOLD),
                listOf(line("현재 ${state.rows}줄(${state.rows * 9}칸)입니다.")) +
                    (if (state.rows < ChestState.MAX_ROWS) listOf(line("구매하면 1줄(9칸) 늘어납니다.")) + price(ChestProduct.UPGRADE)
                    else listOf(line("최대 크기입니다.", NamedTextColor.GREEN))), ChestProduct.UPGRADE)
            icon(13, Material.HOPPER, Component.text("자동 저장", NamedTextColor.AQUA, TextDecoration.BOLD),
                listOf(line("인벤토리가 가득 찬 상태에서 주운 아이템을"), line("상자에 자동으로 넣습니다.")) + when {
                    !state.unlocked -> locked
                    state.autoOwned -> listOf(line("구매 완료 (현재 ${if (state.autoEnabled) "켜짐" else "꺼짐"})", NamedTextColor.GREEN),
                        line("/chest auto로 켜고 끌 수 있습니다."))
                    else -> price(ChestProduct.AUTO_STORE)
                }, ChestProduct.AUTO_STORE)
            icon(15, Material.ENDER_CHEST, Component.text("엔더화", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                listOf(line("죽어도 상자 안의 아이템을 떨어뜨리지 않습니다.")) + when {
                    !state.unlocked -> locked
                    state.ender -> listOf(line("구매 완료", NamedTextColor.GREEN))
                    else -> price(ChestProduct.ENDER)
                }, ChestProduct.ENDER)
            openMenu(plugin, player, holder.inventory, expected)
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun click(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ChestHolder ?: return
        if (holder.finished || FileManager.delivery.blocked(event.whoClicked.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW)
    fun drag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? ChestHolder ?: return
        if (holder.finished) event.isCancelled = true
    }

    @EventHandler
    fun close(event: InventoryCloseEvent) {
        if (event.inventory.holder !is ChestHolder) return
        val player = event.player as? Player ?: return
        finish(player, closeView = false)
        player.playSound(player.location, Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f)
    }

    /** Dropping (Q, or outside the window) works as in a vanilla chest; the window is committed at once. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun drop(event: PlayerDropItemEvent) { if (event.player.uniqueId in sessions) checkpoint(event.player) }

    @EventHandler(priority = EventPriority.LOWEST)
    fun pickup(event: EntityPickupItemEvent) { if (event.entity.uniqueId in sessions) event.isCancelled = true }

    @EventHandler(priority = EventPriority.LOWEST)
    fun quit(event: PlayerQuitEvent) {
        finish(event.player, closeView = false)
        FileManager.chests.release(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun death(event: PlayerDeathEvent) {
        val player = event.entity
        finish(player, closeView = false)
        if (!enabled || event.keepInventory || FileManager.delivery.blocked(player.uniqueId)) return
        val request = UUID.randomUUID()
        try {
            FileManager.chests.deathDrop(request, player.uniqueId).forEach { event.drops.add(PaperItemCodec.item(it.payload, it.count)) }
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Chest death drop $request failed for ${player.uniqueId}; the chest was kept", failure)
        }
    }

    /** Only when none of the ground item fits the inventory; a partial pickup leaves the rest for the next attempt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun autoStore(event: PlayerAttemptPickupItemEvent) {
        val player = event.player; val item = event.item; val stack = item.itemStack
        if (!enabled || stack.isEmpty || event.remaining != stack.amount || !item.isValid || !item.canPlayerPickup()) return
        if (player.isDead || player.uniqueId in sessions || FileManager.delivery.blocked(player.uniqueId)) return
        if (item.owner != null && item.owner != player.uniqueId) return
        if (runCatching { AbilityManager.active(stack) }.getOrDefault(true)) return
        val request = UUID.randomUUID()
        try {
            if (!FileManager.chests.state(player.uniqueId).autoEnabled) return
            val accepted = FileManager.chests.absorb(request, player.uniqueId, item.uniqueId, PaperItemCodec.payload(stack),
                stack.amount, stack.maxStackSize)
            if (accepted == 0) return
            event.isCancelled = true
            if (accepted == stack.amount) item.remove() else item.itemStack = stack.clone().apply { amount -= accepted }
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.2f, 0.8f)
            player.sendActionBar(stack.effectiveName().append(Component.text("이(가) 자동 저장되었습니다", NamedTextColor.WHITE)))
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Chest auto store $request failed for ${player.uniqueId}", failure)
        }
    }

    /** An older world save can bring back an item that was already stored; trim it to what was left on the ground. */
    @EventHandler
    fun entitiesLoad(event: EntitiesLoadEvent) {
        for (entity in event.entities) {
            val item = entity as? Item ?: continue
            val remaining = FileManager.chests.absorbedRemaining(item.uniqueId) ?: continue
            val stack = item.itemStack
            if (stack.amount <= remaining) continue
            if (remaining == 0) item.remove() else item.itemStack = stack.clone().apply { amount = remaining }
            plugin.logger.warning("Trimmed reloaded item entity ${item.uniqueId} that was already stored in a chest")
        }
    }

    @EventHandler
    fun shopClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.CHEST_SHOP) return
        event.isCancelled = true
        if (event.rawSlot !in 0 until top.size || event.click != ClickType.LEFT) return
        val player = event.whoClicked as? Player ?: return
        val quote = holder.actions[event.rawSlot] as? MenuAction.ChestQuote ?: return
        if (FileManager.delivery.blocked(player.uniqueId) || !player.hasPermission("limpi.chest.shop")) return
        if (!plugin.config.getBoolean("EnableFinancialSystem")) {
            player.sendMessage(Component.text("금융 시스템이 비활성화되어 있어 구매할 수 없습니다.", NamedTextColor.RED)); EconomyText.deny(player); return
        }
        val prices = prices(player) ?: return
        val product = ChestProduct.valueOf(quote.product)
        val request = UUID.randomUUID()
        try {
            val state = FileManager.chests.buy(request, player.uniqueId, product, quote.price, prices)
            player.sendMessage(when (product) {
                ChestProduct.UNLOCK -> Component.text("상자를 해금했습니다! ", NamedTextColor.GREEN)
                    .append(Component.text("/chest", NamedTextColor.LIGHT_PURPLE)).append(Component.text("로 열 수 있습니다.", NamedTextColor.WHITE))
                ChestProduct.UPGRADE -> Component.text("상자가 ${state.rows}줄(${state.rows * 9}칸)로 커졌습니다!", NamedTextColor.GREEN)
                ChestProduct.AUTO_STORE -> Component.text("자동 저장을 구매했습니다! ", NamedTextColor.GREEN)
                    .append(Component.text("/chest auto", NamedTextColor.LIGHT_PURPLE)).append(Component.text("로 켜고 끌 수 있습니다.", NamedTextColor.WHITE))
                ChestProduct.ENDER -> Component.text("상자가 엔더화되었습니다! 이제 죽어도 상자 안의 아이템을 떨어뜨리지 않습니다.", NamedTextColor.LIGHT_PURPLE)
            })
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            openShop(player, top)
        } catch (failure: BankException) {
            if (failure.reason == BankFailure.INSUFFICIENT_FUNDS) player.sendMessage(EconomyText.insufficient(player, "원 있습니다."))
            else EconomyText.bankFailure(player, failure)
            EconomyText.deny(player)
        } catch (failure: ChestUnavailable) {
            player.sendMessage(Component.text(failure.message ?: "구매할 수 없습니다.", NamedTextColor.RED))
            EconomyText.deny(player)
            openShop(player, top)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Chest purchase $request failed for ${player.uniqueId}", failure)
        }
    }
}
