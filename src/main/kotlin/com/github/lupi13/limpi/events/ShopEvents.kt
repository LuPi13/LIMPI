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
        fun openShopGUI(player: Player, requestedPage: Int = 0, expected: Inventory? = null) {
            val offers = PaperShopCatalog(plugin).available()
            val page = Pagination(offers.size, requestedPage)
            val holder = MenuHolder(MenuKind.SHOP, page.page, shopGUIName)
            val balance = EconomyText.balanceDisplay(player.uniqueId)
            for ((slot, offer) in offers.subList(page.start, page.end).withIndex()) {
                val icon = PaperItemCodec.item(offer.item.payload, 1)
                val meta = icon.itemMeta
                meta.lore(listOf(Component.text("가격: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                    .append(moneyDisplay(offer.price.units).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text("원, 현재 보유 금액: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                    .append(balance.decoration(TextDecoration.ITALIC, false))
                    .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
                    Component.text("좌클릭하여 구매합니다.", NamedTextColor.WHITE),
                    Component.text("Shift를 누르고 클릭하면 1세트/최대한 많이 구매합니다.", NamedTextColor.WHITE)))
                icon.itemMeta = meta
                holder.inventory.setItem(slot, icon); holder.actions[slot] = MenuAction.ShopQuote(offer.code, offer.price.units)
            }
            fun button(slot: Int, target: Int, label: String) {
                val icon = ItemStack(Material.ARROW); val meta = icon.itemMeta
                meta.customName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)); icon.itemMeta = meta
                holder.inventory.setItem(slot, icon); holder.actions[slot] = MenuAction.Page(target)
            }
            if (page.hasPrevious) button(45, page.page - 1, "이전 페이지")
            if (page.hasNext) button(53, page.page + 1, "다음 페이지")
            openMenu(plugin, player, holder.inventory, expected)
        }
    }

    /** Refuse up front, as before, when the purchase would not fit; queued delivery stays only as a fallback. */
    private fun fits(player: Player, quote: MenuAction.ShopQuote, bundle: Boolean): Boolean {
        val offer = PaperShopCatalog(plugin).offer(quote.code) ?: return true
        val balance = EconomyText.balance(player.uniqueId) ?: return true
        val count = if (!bundle) 1L else if (quote.price == 0L) offer.item.limit.toLong()
            else minOf(offer.item.limit.toLong(), balance / quote.price)
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
        if (action is MenuAction.Page) { openShopGUI(player, action.index, top); return }
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
            player.sendMessage(Component.text(quote.code.lowercase(Locale.getDefault()), NamedTextColor.AQUA)
                .append(Component.text(" ${receipt.count}", NamedTextColor.GREEN))
                .append(Component.text("개 구매 완료.", NamedTextColor.WHITE)))
            if (FileManager.delivery.claim(player, quiet = true)) openShopGUI(player, holder.page, top)
        } catch (failure: BankException) {
            if (failure.reason == BankFailure.INSUFFICIENT_FUNDS) player.sendMessage(EconomyText.insufficient(player, "원 있습니다."))
            else EconomyText.bankFailure(player, failure)
            EconomyText.deny(player)
        } catch (failure: ShopUnavailable) {
            player.sendMessage(Component.text(failure.message ?: "상품을 확인하세요.", NamedTextColor.RED))
            EconomyText.deny(player)
            openShopGUI(player, holder.page, top)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Shop transaction $request failed", failure)
        }
    }
}
