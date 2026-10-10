package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.menu.*
import com.github.lupi13.limpi.platform.PaperShopCatalog
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Level

class ShopEvents : Listener {
    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        fun openShopGUI(player: Player, requestedPage: Int = 0, expected: Inventory? = null) {
            val offers = PaperShopCatalog(plugin).available()
            val page = Pagination(offers.size, requestedPage)
            val holder = MenuHolder(MenuKind.SHOP, page.page, Component.text("LIMPI: 구매창", NamedTextColor.DARK_GREEN))
            for ((slot, offer) in offers.subList(page.start, page.end).withIndex()) {
                val icon = PaperItemCodec.item(offer.item.payload, 1)
                val meta = icon.itemMeta
                meta.lore(listOf(Component.text("가격: ${offer.price.units}원", NamedTextColor.WHITE),
                    Component.text("좌클릭 1개 · Shift+좌클릭 1세트/구매 가능한 수량", NamedTextColor.YELLOW),
                    Component.text("공간 부족 시 /financial claim으로 수령", NamedTextColor.GRAY)))
                icon.itemMeta = meta
                holder.inventory.setItem(slot, icon); holder.actions[slot] = MenuAction.ShopQuote(offer.code, offer.price.units)
            }
            fun button(slot: Int, target: Int, label: String) {
                val icon = ItemStack(Material.ARROW); val meta = icon.itemMeta
                meta.customName(Component.text(label)); icon.itemMeta = meta
                holder.inventory.setItem(slot, icon); holder.actions[slot] = MenuAction.Page(target)
            }
            if (page.hasPrevious) button(45, page.page - 1, "이전 페이지")
            if (page.hasNext) button(53, page.page + 1, "다음 페이지")
            openMenu(plugin, player, holder.inventory, expected)
        }
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
            val receipt = FileManager.shop.buy(request, player.uniqueId, quote.code, quote.price, event.click == ClickType.SHIFT_LEFT)
            player.sendMessage(Component.text("${quote.code} ${receipt.count}개 구매를 확정했습니다. ${receipt.total}원 / 거래: $request", NamedTextColor.GREEN))
            if (FileManager.delivery.claim(player)) openShopGUI(player, holder.page, top)
        } catch (failure: BankException) {
            player.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: ShopUnavailable) {
            player.sendMessage(Component.text(failure.message ?: "상품을 확인하세요.", NamedTextColor.RED))
            openShopGUI(player, holder.page, top)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Shop transaction $request failed", failure)
            player.sendMessage(Component.text("구매 기록을 확인해야 합니다. 거래 UUID: $request", NamedTextColor.RED))
        }
    }
}
