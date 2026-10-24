package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.Grade
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.menu.*
import com.github.lupi13.limpi.platform.PaperItemCodec
import org.bukkit.event.inventory.ClickType
import java.util.UUID
import java.util.logging.Level

class AbilityShop : Listener {
    companion object {
        private val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        val abilityShopGUIName = Component.text("LIMPI: Ability 아이템", NamedTextColor.DARK_BLUE, TextDecoration.BOLD)

        /**
         * 능력 선택권 아이템 또는 뽑기권을 생성합니다.
         * isShopDisplay가 false일 경우, 플레이어 정보는 null로 설정할 수 있습니다.
         * @param grade 능력의 등급, null일 경우 뽑기권 아이템을 생성합니다.
         * @param isShopDisplay 상점에서 보여지는지 여부(가격, 보유 금액 등 표시)
         * @param player 플레이어 정보 (상점에서 구매 시 사용)
         * @return 생성된 아이템 스택
         */
        fun getSelectTicketItem(grade: Grade?, isShopDisplay: Boolean, player: Player?): ItemStack {
            val itemModel = when (grade) {
                Grade.TROLL -> "black_concrete"
                Grade.COMMON -> "green_concrete"
                Grade.RARE -> "cyan_concrete"
                Grade.EPIC -> "purple_concrete"
                Grade.LEGENDARY -> "yellow_concrete"
                Grade.MYTHIC -> "red_concrete"
                else -> "nether_star"  // 뽑기권
            }
            val item = if (itemModel == "nether_star") {
                GachaEvents.getGachaTicketItem()
            } else {
                ItemStack(Material.TURTLE_SCUTE, 1)
            }
            val meta = item.itemMeta
            if (itemModel != "nether_star") {
                meta.itemModel = NamespacedKey("minecraft", itemModel)
            }
            var lore: List<Component> = meta.lore() ?: mutableListOf()

            if (grade != null) {
                lore = lore + grade.displayGrade.decoration(TextDecoration.ITALIC, false).append(Component.text(" 능력을 선택할 때 소모됩니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                meta.displayName(grade.displayGrade.append(Component.text(" 선택권", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false))
            }

            if (isShopDisplay) {
                val price = price(grade?.name ?: "GACHA")
                // 2.0.0: paid from the account like the server shop (user decision 2026-10-16), same line format.
                lore = lore + Component.text("가격: ", NamedTextColor.WHITE)
                    .append(moneyDisplay(price))
                    .append(Component.text("원, 현재 보유 금액: ", NamedTextColor.WHITE))
                    .append(com.github.lupi13.limpi.commands.EconomyText.balanceDisplay(player!!.uniqueId))
                    .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
                lore = lore + Component.text("좌클릭하여 구매합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                lore = lore + Component.text("Shift를 누르고 클릭하면 10개/최대한 많이 구매합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
            }
            meta!!.lore(lore)
            item.itemMeta = meta
            return if (isShopDisplay) com.github.lupi13.limpi.platform.AbilityTickets.unmark(item)
                else com.github.lupi13.limpi.platform.AbilityTickets.mark(item, grade?.name ?: "GACHA")
        }

        private const val BUNDLE = 10L

        private fun price(code: String): Long {
            val path = if (code == "GACHA") "TicketMoneyPrice" else "SelectMoneyPrice.${Grade.valueOf(code).name}"
            return CatalogPrices.price(requireNotNull(plugin.config.get(path)) { "Missing ability shop price $path" }).units
        }

        /** TROLL 선택권은 팔지 않는다(2.6.0). 이 등급으로 돌아가는 일을 일부러 어렵게 한다. */
        private val unsold = setOf(Grade.TROLL)

        /** The current product for a code (GACHA or a grade), or null when it is unknown, not sold or misconfigured. */
        fun offer(code: String): ShopOffer? = try {
            val grade = if (code == "GACHA") null else Grade.valueOf(code)
            if (grade in unsold) return null
            ShopOffer(code, PaperItemCodec.grant(getSelectTicketItem(grade, false, null)), Money(price(code)), BUNDLE)
        } catch (failure: Exception) {
            plugin.logger.warning("Invalid ability shop product $code: ${failure.message}"); null
        }

        fun openAbilityShopGUI(player: Player, expected: Inventory? = null) {
            if (!player.hasPermission("limpi.ability.shop") || !FileManager.delivery.claim(player)) return
            val holder = MenuHolder(MenuKind.ABILITY_SHOP, 0, abilityShopGUIName, 9)
            val grades = listOf<Grade?>(null) + Grade.entries
            for ((index, grade) in grades.withIndex()) {
                if (grade in unsold) continue // 다른 상품의 칸은 그대로 둔다.
                val code = grade?.name ?: "GACHA"
                val offer = offer(code) ?: continue
                holder.inventory.setItem(index + 1, getSelectTicketItem(grade, true, player))
                holder.actions[index + 1] = MenuAction.ShopQuote(code, offer.price.units)
            }
            openMenu(plugin, player, holder.inventory, expected)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.ABILITY_SHOP) return
        event.isCancelled = true
        if (event.rawSlot !in 0 until top.size || event.click !in setOf(ClickType.LEFT, ClickType.SHIFT_LEFT)) return
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission("limpi.ability.shop") || FileManager.delivery.blocked(player.uniqueId)) return
        val quote = holder.actions[event.rawSlot] as? MenuAction.ShopQuote ?: return
        val request = UUID.randomUUID()
        val bundle = event.click == ClickType.SHIFT_LEFT
        try {
            if (!FileManager.delivery.claim(player)) return
            val offer = offer(quote.code)
            if (offer != null && offer.price.units == quote.price) {
                // Refuse up front, like the server shop, when the tickets would not fit.
                val balance = com.github.lupi13.limpi.commands.EconomyText.balance(player.uniqueId) ?: 0L
                val count = if (!bundle) 1L else if (quote.price == 0L) BUNDLE else minOf(BUNDLE, balance / quote.price)
                if (count > 0) try {
                    InventoryPlan.grant(PaperItemCodec.snapshot(player), listOf(offer.item.copy(count = count)), player.inventory.maxStackSize)
                } catch (_: InventoryFull) {
                    player.sendMessage(Component.text("인벤토리가 가득 찼습니다.", NamedTextColor.RED))
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                    return
                }
            }
            val receipt = FileManager.abilityShop.buy(request, player.uniqueId, quote.code, quote.price, bundle)
            val name = (event.currentItem ?: PaperItemCodec.item(requireNotNull(offer).item.payload, 1)).displayName()
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
            player.sendMessage(name
                .append(Component.text(" ${receipt.count}", NamedTextColor.GREEN))
                .append(Component.text("개 구매 완료.", NamedTextColor.WHITE)))
            if (FileManager.delivery.claim(player, quiet = true)) openAbilityShopGUI(player, top)
        } catch (failure: BankException) {
            if (failure.reason == BankFailure.INSUFFICIENT_FUNDS) player.sendMessage(com.github.lupi13.limpi.commands.EconomyText.insufficient(player, "원 있습니다."))
            else com.github.lupi13.limpi.commands.EconomyText.bankFailure(player, failure)
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
        } catch (failure: ShopUnavailable) {
            player.sendMessage(Component.text(failure.message ?: "상품을 확인하세요.", NamedTextColor.RED))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
            openAbilityShopGUI(player, top)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Ability shop purchase $request failed for ${player.uniqueId}", failure)
        }
    }

}
