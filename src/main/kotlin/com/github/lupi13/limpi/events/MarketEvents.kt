package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.EconomyText
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.delivery.InventoryFull
import com.github.lupi13.limpi.delivery.InventoryPlan
import com.github.lupi13.limpi.market.*
import com.github.lupi13.limpi.menu.*
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
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
import java.util.UUID
import java.util.logging.Level

class MarketEvents : Listener {
    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        val marketGUIName = Component.text("LIMPI: 장터", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)

        /** Display name without the surrounding brackets that displayName() adds. */
        fun itemName(item: ItemStack, fallback: String = item.type.name.lowercase().replace("_", " ")): String = try {
            val name = PlainTextComponentSerializer.plainText().serialize(item.displayName())
            name.substring(1, name.length - 1).ifBlank { fallback }
        } catch (_: Exception) { fallback }

        fun openMarketGUI(player: Player, requestedPage: Int = 0, expected: Inventory? = null) {
            val listings = FileManager.market.available().filter { it.state == ListingState.ACTIVE }
                .sortedBy { it.code }
            val page = Pagination(listings.size, requestedPage)
            val holder = MenuHolder(MenuKind.MARKET, page.page, marketGUIName)
            for ((slot, listing) in listings.subList(page.start, page.end).withIndex()) {
                val item = PaperItemCodec.item(listing.item.payload, minOf(listing.item.count, listing.item.limit.toLong()).toInt())
                val meta = item.itemMeta
                val lore = (meta.lore() ?: emptyList()).toMutableList()
                lore.add(Component.text("가격: ", NamedTextColor.WHITE)
                    .append(moneyDisplay(listing.price.units))
                    .append(Component.text("원, 판매자: ", NamedTextColor.WHITE))
                    .append(playerDisplay(listing.sellerName).decoration(TextDecoration.ITALIC, false)))
                lore.add(Component.text("좌클릭하여 구매, 우클릭하여 흥정을 요청합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore); item.itemMeta = meta
                holder.inventory.setItem(slot, item)
                holder.actions[slot] = MenuAction.MarketQuote(listing.id, listing.price.units)
            }
            fun button(slot: Int, target: Int, label: String) {
                val item = ItemStack(Material.ARROW)
                val meta = item.itemMeta
                meta.customName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)); item.itemMeta = meta
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

        fun getRandomPrefix(): String { val prefixList = listOf(
            "돈없는 ", "거지왕 ", "주식 떡락한 ", "패가망신한 ", "부채에 시달리는 ", "롯데리아 케찹도둑 ",
            "산와머니 백댄서 ", "도박쟁이 ", "먹을게 없어서 흙 퍼먹는 ", "롯데마트 시식코너 마스터 ", "길고양이 밥 뺏어먹는 ", "자판기 잔돈 슬롯 점검 전문가 ",
            "공짜 와이파이 추적자 ", "하수구에 500원 동전 빠뜨린 ", "버스비 없어서 버스기사와 눈싸움 하다가 쫒겨난 ", "편의점 폐기 헌터 ", "사기 수표에 당한 "
        )
        return if (Math.random() < 0.9999) {
            prefixList.random()
        } else {
            ("이 접두어는 0.01% 확률로 등장합니다. 행운아에게 자비를 베풀어 보시는 것은 어떠신가요? 0.01%를 뚫은 ")
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
            val listing = FileManager.market.find(quote.id)
            if (listing == null || listing.state != ListingState.ACTIVE || listing.price.units != quote.price) throw MarketException(MarketFailure.STALE_QUOTE)
            val label = "${itemName(PaperItemCodec.item(listing.item.payload, 1))}(${listing.code})"
            if (event.isLeftClick) {
                if (!FileManager.delivery.claim(player)) return
                val balance = EconomyText.balance(player.uniqueId)
                val fits = try { InventoryPlan.grant(PaperItemCodec.snapshot(player), listOf(listing.item), player.inventory.maxStackSize); true }
                    catch (_: InventoryFull) { false }
                if (balance != null && balance >= quote.price && !fits) {
                    player.sendMessage(Component.text("인벤토리가 가득 찼습니다", NamedTextColor.RED))
                    EconomyText.deny(player)
                    return
                }
                val receipt = FileManager.marketTrades.buy(transaction, player.uniqueId, quote.id, quote.price)
                player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                player.sendMessage(Component.text(label, NamedTextColor.AQUA)
                    .append(Component.text("을(를) 구매했습니다.", NamedTextColor.WHITE)))
                plugin.server.getPlayer(receipt.seller)?.let { seller ->
                    seller.playSound(seller, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                    seller.sendMessage(playerDisplay(player)
                        .append(Component.text("님이 ", NamedTextColor.WHITE))
                        .append(Component.text(label, NamedTextColor.AQUA))
                        .append(Component.text("을(를) 구매했습니다.", NamedTextColor.WHITE)))
                    seller.sendMessage(Component.text("판매 수수료 5%(", NamedTextColor.WHITE)
                        .append(moneyDisplay(receipt.fee))
                        .append(Component.text("원)를 제외하고 계좌에 입금되었습니다.", NamedTextColor.WHITE)))
                }
                if (!FileManager.delivery.claim(player, quiet = true)) return
                reopenMarketGUI()
            } else {
                // Haggling is only a notification; it never changes the listing.
                plugin.server.getPlayer(listing.seller)?.sendMessage(Component.text(getRandomPrefix(), NamedTextColor.WHITE)
                    .append(playerDisplay(player))
                    .append(Component.text("님이 ", NamedTextColor.WHITE))
                    .append(Component.text(label, NamedTextColor.AQUA))
                    .append(Component.text("을(를) 깎아달라고 조릅니다.", NamedTextColor.WHITE)))
                player.sendMessage(playerDisplay(listing.sellerName)
                    .append(Component.text("님에게 ", NamedTextColor.WHITE))
                    .append(Component.text(label, NamedTextColor.AQUA))
                    .append(Component.text("을(를) 깎아달라고 졸랐습니다.", NamedTextColor.WHITE)))
            }
        } catch (failure: BankException) {
            if (failure.reason == BankFailure.INSUFFICIENT_FUNDS) player.sendMessage(EconomyText.insufficient(player, "원 있습니다."))
            else EconomyText.bankFailure(player, failure)
            EconomyText.deny(player)
        } catch (failure: MarketException) {
            if (failure.reason == MarketFailure.OWN_LISTING) {
                player.sendMessage(Component.text("자신이 올린 물품은 구매할 수 없습니다.", NamedTextColor.RED)
                    .append(Component.text("\n장터에 올린 아이템을 환수하고 싶다면 ", NamedTextColor.RED))
                    .append(Component.text("/market remove <code>", NamedTextColor.RED))
                    .append(Component.text(" 명령어를 사용하세요.", NamedTextColor.RED)))
            } else {
                player.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
                openMarketGUI(player, holder.page, top)
            }
            EconomyText.deny(player)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Market transaction $transaction failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("거래 결과를 확인하지 못했습니다. 관리자에게 문의하세요.", NamedTextColor.RED))
        }
    }
}
