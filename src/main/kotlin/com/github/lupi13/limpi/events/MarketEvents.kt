package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.market.*
import com.github.lupi13.limpi.menu.*
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

class MarketEvents : Listener {
    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        val marketGUIName = Component.text("LIMPI: 장터", NamedTextColor.DARK_PURPLE)

        fun openMarketGUI(player: Player, requestedPage: Int = 0, expected: Inventory? = null) {
            val listings = FileManager.market.available().filter { it.state == ListingState.ACTIVE }
                .sortedBy { it.code }
            val page = Pagination(listings.size, requestedPage)
            val holder = MenuHolder(MenuKind.MARKET, page.page, marketGUIName)
            for ((slot, listing) in listings.subList(page.start, page.end).withIndex()) {
                val item = PaperItemCodec.item(listing.item.payload, minOf(listing.item.count, listing.item.limit.toLong()).toInt())
                val meta = item.itemMeta
                val lore = (meta.lore() ?: emptyList()).toMutableList()
                lore.add(Component.text("코드: ${listing.code}, 가격: ${listing.price.units}원", NamedTextColor.WHITE))
                lore.add(Component.text("판매자: ${listing.sellerName}", NamedTextColor.GRAY))
                lore.add(Component.text("좌클릭 구매 · 우클릭 흥정 요청 · 공간 부족 시 미수령 보관", NamedTextColor.YELLOW))
                meta.lore(lore); item.itemMeta = meta
                holder.inventory.setItem(slot, item)
                holder.actions[slot] = MenuAction.MarketQuote(listing.id, listing.price.units)
            }
            fun button(slot: Int, target: Int, label: String) {
                val item = ItemStack(Material.ARROW)
                val meta = item.itemMeta; meta.customName(Component.text(label)); item.itemMeta = meta
                holder.inventory.setItem(slot, item); holder.actions[slot] = MenuAction.Page(target)
            }
            if (page.hasPrevious) button(45, page.page - 1, "이전 페이지")
            if (page.hasNext) button(53, page.page + 1, "다음 페이지")
            openMenu(plugin, player, holder.inventory, expected)
        }

        fun reopenMarketGUI() {
            for (player in plugin.server.onlinePlayers) {
                val top = player.openInventory.topInventory
                val holder = top.holder as? MenuHolder ?: continue
                if (holder.kind == MenuKind.MARKET) openMarketGUI(player, holder.page, top)
            }
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.MARKET) return
        event.isCancelled = true
        if (event.rawSlot !in 0 until top.size || event.click !in setOf(ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.RIGHT, ClickType.SHIFT_RIGHT)) return
        val player = event.whoClicked as? Player ?: return
        if (FileManager.delivery.blocked(player.uniqueId) || !player.hasPermission("limpi.financial.market")) return
        val action = holder.actions[event.rawSlot] ?: return
        if (action is MenuAction.Page) { openMarketGUI(player, action.index, top); return }
        val quote = action as? MenuAction.MarketQuote ?: return
        val transaction = UUID.randomUUID()
        try {
            if (event.isLeftClick) {
                if (!FileManager.delivery.claim(player)) return
                val receipt = FileManager.marketTrades.buy(transaction, player.uniqueId, quote.id, quote.price)
                player.sendMessage(Component.text("구매를 확정했습니다. 거래 UUID: $transaction", NamedTextColor.GREEN))
                plugin.server.getPlayer(receipt.seller)?.sendMessage(Component.text(
                    "${player.name} 님이 매물을 구매했습니다. 수수료 ${receipt.fee}원을 제외하고 입금했습니다.", NamedTextColor.GREEN))
                if (!FileManager.delivery.claim(player)) return
                reopenMarketGUI()
            } else {
                val listing = FileManager.market.find(quote.id)
                if (listing == null || listing.state != ListingState.ACTIVE || listing.price.units != quote.price) throw MarketException(MarketFailure.STALE_QUOTE)
                val seller = plugin.server.getPlayer(listing.seller)
                if (seller == null) player.sendMessage(Component.text("판매자가 오프라인입니다.", NamedTextColor.YELLOW))
                else {
                    seller.sendMessage(Component.text("${player.name} 님이 ${listing.code} 매물의 흥정을 요청했습니다.", NamedTextColor.YELLOW))
                    player.sendMessage(Component.text("판매자에게 흥정을 요청했습니다.", NamedTextColor.WHITE))
                }
            }
        } catch (failure: BankException) {
            player.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: MarketException) {
            player.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
            openMarketGUI(player, holder.page, top)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Market transaction $transaction failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("거래 결과를 확인하지 못했습니다. 관리자에게 거래 UUID $transaction 을 알려주세요.", NamedTextColor.RED))
        }
    }
}
