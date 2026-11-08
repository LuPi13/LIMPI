package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.EconomyText
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.delivery.InventoryFull
import com.github.lupi13.limpi.delivery.InventoryPlan
import com.github.lupi13.limpi.menu.*
import com.github.lupi13.limpi.platform.PaperShopCatalog
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

class ShopEvents : Listener {
    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        val shopGUIName = Component.text("LIMPI: 구매창", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)

        /** The bottom row: page buttons on the ends, the tabs between them around an empty middle. */
        private const val PREVIOUS_SLOT = 45
        private const val NEXT_SLOT = 53
        private val tabSlots = listOf(46, 47, 48, 50, 51, 52)

        private fun tabIcon(tab: ShopTab) = when (tab) {
            ShopTab.ALL -> Material.CHEST
            ShopTab.BUILDING -> Material.BRICKS
            ShopTab.RESOURCE -> Material.IRON_INGOT
            ShopTab.FARMING -> Material.WHEAT
            ShopTab.DECORATION -> Material.POPPY
            ShopTab.OTHER -> Material.TORCH
        }

        private fun tabItem(tab: ShopTab, count: Int, selected: Boolean) = ItemStack(tabIcon(tab)).apply {
            val meta = itemMeta
            meta.displayName(Component.text(tab.label, if (selected) NamedTextColor.GREEN else NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text("상품 ${count}개", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            meta.setEnchantmentGlintOverride(selected)
            itemMeta = meta
        }

        private fun abilityShopLink(player: Player) =
            ShopLinks.shown(plugin.config.getBoolean("EnableAbilitySystem"), player.hasPermission("limpi.ability.shop"))

        /** The server shop's button in the ability shop, and the other way round. */
        fun linkItem(material: Material, name: String, color: NamedTextColor) = ItemStack(material).apply {
            val meta = itemMeta
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text("클릭하여 ${name}으로 이동합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)))
            itemMeta = meta
        }

        private fun tabOf(holder: MenuHolder) = ShopTab.entries.firstOrNull { it.code == holder.section } ?: ShopTab.ALL

        fun openShopGUI(player: Player, requestedPage: Int = 0, expected: Inventory? = null, tab: ShopTab = ShopTab.ALL) {
            val available = PaperShopCatalog(plugin).available()
            val categories = ShopCategories { FileManager.getShopCategoryConfig().get(it) }
            val offers = categories.filter(available, tab) { it.code }
            val page = Pagination(offers.size, requestedPage)
            val holder = MenuHolder(MenuKind.SHOP, page.page, shopGUIName, section = tab.code)
            val balance = EconomyText.balanceDisplay(player.uniqueId)
            for ((slot, offer) in offers.subList(page.start, page.end).withIndex()) {
                val icon = PaperShopCatalog.icon(offer)
                val meta = icon.itemMeta
                meta.lore(listOf(Component.text("가격: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                    .append(moneyDisplay(offer.price.units).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text("원, 현재 보유 금액: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                    .append(balance.decoration(TextDecoration.ITALIC, false))
                    .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
                    Component.text("좌클릭하여 구매합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift를 누르고 클릭하면 1세트/최대한 많이 구매합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)))
                icon.itemMeta = meta
                holder.inventory.setItem(slot, icon); holder.actions[slot] = MenuAction.ShopQuote(offer.code, offer.price.units)
            }
            // Same page buttons (dye, lore) as the ability dictionary.
            if (page.hasPrevious) { holder.inventory.setItem(PREVIOUS_SLOT, AbilitySelectEvent.getPreviousPageItem()); holder.actions[PREVIOUS_SLOT] = MenuAction.Page(page.page - 1) }
            if (page.hasNext) { holder.inventory.setItem(NEXT_SLOT, AbilitySelectEvent.getNextPageItem()); holder.actions[NEXT_SLOT] = MenuAction.Page(page.page + 1) }
            ShopTab.entries.forEachIndexed { index, shown ->
                val slot = tabSlots[index]
                holder.inventory.setItem(slot, tabItem(shown, categories.filter(available, shown) { it.code }.size, shown == tab))
                holder.actions[slot] = MenuAction.ShopSection(shown.code)
            }
            if (abilityShopLink(player)) {
                holder.inventory.setItem(ShopLinks.SERVER_SHOP_SLOT, linkItem(Material.BEACON, "능력 상점", NamedTextColor.AQUA))
                holder.actions[ShopLinks.SERVER_SHOP_SLOT] = MenuAction.OpenMenu(MenuKind.ABILITY_SHOP)
            }
            openMenu(plugin, player, holder.inventory, expected)
        }
    }

    /** Refuse up front, as before, when the purchase would not fit; queued delivery stays only as a fallback. */
    private fun fits(player: Player, quote: MenuAction.ShopQuote, bundle: Boolean): Boolean {
        val offer = PaperShopCatalog(plugin).offer(quote.code) ?: return true
        val balance = EconomyText.balance(player.uniqueId) ?: return true
        val count = if (!bundle) 1L else if (quote.price == 0L) offer.bundle
            else minOf(offer.bundle, balance / quote.price)
        if (count <= 0L) return true
        return try {
            InventoryPlan.grant(PaperItemCodec.snapshot(player), listOf(offer.item.copy(count = count)), player.inventory.maxStackSize); true
        } catch (_: InventoryFull) { false }
    }

    @EventHandler
    fun click(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.SHOP) return
        event.isCancelled = true
        if (event.rawSlot !in 0 until top.size || event.click !in setOf(ClickType.LEFT, ClickType.SHIFT_LEFT)) return
        val player = event.whoClicked as? Player ?: return
        if (FileManager.delivery.blocked(player.uniqueId) || !player.hasPermission("limpi.financial.shop")) return
        val action = holder.actions[event.rawSlot] ?: return
        val tab = tabOf(holder)
        if (action is MenuAction.Page) {
            openShopGUI(player, action.index, top, tab)
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f)
            return
        }
        if (action is MenuAction.ShopSection) {
            if (action.tab == tab.code) return
            openShopGUI(player, 0, top, ShopTab.entries.firstOrNull { it.code == action.tab } ?: ShopTab.ALL)
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f)
            return
        }
        if (action is MenuAction.OpenMenu) {
            if (action.kind != MenuKind.ABILITY_SHOP || !abilityShopLink(player)) return
            AbilityShop.openAbilityShopGUI(player, top)
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f)
            return
        }
        val quote = action as? MenuAction.ShopQuote ?: return
        val request = UUID.randomUUID()
        try {
            if (!FileManager.delivery.claim(player)) return
            if (!fits(player, quote, event.click == ClickType.SHIFT_LEFT)) {
                player.sendMessage(Component.text("인벤토리가 가득 찼습니다.", NamedTextColor.RED))
                EconomyText.deny(player)
                return
            }
            val receipt = FileManager.shop.buy(request, player.uniqueId, quote.code, quote.price, event.click == ClickType.SHIFT_LEFT)
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
            val product = PaperShopCatalog.name(quote.code) ?: Component.text(quote.code.lowercase(Locale.getDefault()))
            player.sendMessage(Component.text("", NamedTextColor.AQUA).append(product)
                .append(Component.text(" ${receipt.count}", NamedTextColor.GREEN))
                .append(Component.text("개 구매 완료.", NamedTextColor.WHITE)))
            if (FileManager.delivery.claim(player, quiet = true)) openShopGUI(player, holder.page, top, tab)
        } catch (failure: BankException) {
            if (failure.reason == BankFailure.INSUFFICIENT_FUNDS) player.sendMessage(EconomyText.insufficient(player, "원 있습니다."))
            else EconomyText.bankFailure(player, failure)
            EconomyText.deny(player)
        } catch (failure: ShopUnavailable) {
            player.sendMessage(Component.text(failure.message ?: "상품을 확인하세요.", NamedTextColor.RED))
            EconomyText.deny(player)
            openShopGUI(player, holder.page, top, tab)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Shop transaction $request failed", failure)
        }
    }
}
