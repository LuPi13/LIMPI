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
         * @param isShopDisplay 상점에서 보여지는지 여부(가격, 보유 에메랄드 등 표시)
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
                val money = emeraldCount(player!!)
                lore = lore + Component.text("가격: 에메랄드 ", NamedTextColor.WHITE)
                    .append(moneyDisplay(price))
                    .append(Component.text("개, 현재 보유 에메랄드: ", NamedTextColor.WHITE))
                    .append(moneyDisplay(money))
                    .append(Component.text("개", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
                lore = lore + Component.text("좌클릭하여 구매합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                lore = lore + Component.text("Shift를 누르고 클릭하면 10개/최대한 많이 구매합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
            }
            meta!!.lore(lore)
            item.itemMeta = meta
            return com.github.lupi13.limpi.platform.AbilityTickets.mark(item, grade?.name ?: "GACHA")
        }

        private fun price(code: String): Long {
            val path = if (code == "GACHA") "TicketPrice" else "SelectPrice.${Grade.valueOf(code).name}"
            return CatalogPrices.sell(requireNotNull(plugin.config.get(path)) { "Missing ability shop price $path" }).units
        }

        private val emeraldPayload: String get() = PaperItemCodec.payload(ItemStack(Material.EMERALD))
        private fun emeraldCount(player: Player): Long = InventoryPlan.count(PaperItemCodec.snapshot(player), emeraldPayload)

        fun openAbilityShopGUI(player: Player, expected: Inventory? = null) {
            if (!player.hasPermission("limpi.ability.shop") || !FileManager.delivery.claim(player)) return
            val holder = MenuHolder(MenuKind.ABILITY_SHOP, 0, abilityShopGUIName, 9)
            val grades = listOf<Grade?>(null) + Grade.entries
            for ((index, grade) in grades.withIndex()) {
                val code = grade?.name ?: "GACHA"
                try {
                    val unit = price(code)
                    val grant = PaperItemCodec.grant(getSelectTicketItem(grade, false, null))
                    holder.inventory.setItem(index + 1, getSelectTicketItem(grade, true, player))
                    holder.actions[index + 1] = MenuAction.ItemExchangeQuote(code, unit, grant)
                } catch (failure: Exception) {
                    plugin.logger.warning("Invalid ability shop product $code: ${failure.message}")
                }
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
        val quote = holder.actions[event.rawSlot] as? MenuAction.ItemExchangeQuote ?: return
        val request = UUID.randomUUID()
        try {
            require(price(quote.product) == quote.price) { "가격이 변경되었습니다. 화면을 다시 열어주세요." }
            if (!FileManager.delivery.claim(player)) return
            val before = PaperItemCodec.snapshot(player)
            val currency = emeraldPayload
            val available = InventoryPlan.count(before, currency)
            val maximum = if (event.click == ClickType.SHIFT_LEFT) 10L else 1L
            val count = if (quote.price == 0L) maximum else minOf(maximum, available / quote.price)
            if (count <= 0) {
                player.sendMessage(Component.text("에메랄드가 부족합니다! ", NamedTextColor.RED))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                return
            }
            val cost = Math.multiplyExact(quote.price, count)
            val remaining = if (cost == 0L) before else InventoryPlan.remove(before, currency, cost)
            try {
                InventoryPlan.grant(remaining, listOf(quote.reward.copy(count = count)))
            } catch (_: InventoryFull) {
                player.sendMessage(Component.text("인벤토리가 가득 찼습니다.", NamedTextColor.RED))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                return
            }
            val terms = ExchangeTerms(quote.product, currency, cost, quote.reward.copy(count = count))
            val completed = if (cost == 0L) {
                FileManager.exchanges.free(request, player.uniqueId, terms); true
            } else FileManager.delivery.remove(player, request, ItemExchange.KIND, terms.encode(), before, remaining)
            if (completed) {
                val name = (event.currentItem ?: PaperItemCodec.item(quote.reward.payload, 1)).displayName()
                player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                player.sendMessage(name
                    .append(Component.text(" $count", NamedTextColor.GREEN))
                    .append(Component.text("개 구매 완료.", NamedTextColor.WHITE)))
                if (FileManager.delivery.claim(player)) openAbilityShopGUI(player, top)
            }
        } catch (failure: IllegalArgumentException) {
            player.sendMessage(Component.text(failure.message ?: "구매하지 못했습니다.", NamedTextColor.RED))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Ability shop exchange $request failed for ${player.uniqueId}", failure)
        }
    }

}
