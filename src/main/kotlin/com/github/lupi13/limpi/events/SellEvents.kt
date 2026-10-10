package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.delivery.StoredStack
import com.github.lupi13.limpi.economy.*
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
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

/** A read-only view of storage slots. Selected items never leave playerdata before confirmation. */
class SellEvents : Listener {
    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        fun openSellGUI(player: Player, selected: Map<Int, StoredStack> = emptyMap(), expected: Inventory? = null) {
            val before = PaperItemCodec.snapshot(player)
            val holder = MenuHolder(MenuKind.SELL, 0, Component.text("LIMPI: 판매 선택", NamedTextColor.YELLOW))
            val prices = mutableMapOf<Int, Long>()
            var total = Money(0)
            for ((slot, stack) in before.withIndex()) {
                if (stack == null) continue
                val icon = PaperItemCodec.item(stack.payload, stack.count)
                try {
                    val price = CatalogPrices.sell(FileManager.getSellPriceConfig().get(icon.type.name.lowercase(Locale.ROOT)))
                    val chosen = selected[slot] == stack
                    val value = price.times(stack.count.toLong())
                    if (chosen) { val updated = total + value; prices[slot] = price.units; total = updated }
                    val meta = icon.itemMeta
                    meta.lore((meta.lore() ?: emptyList()) + listOf(
                        Component.text("원본 슬롯 $slot / ${value.units}원", NamedTextColor.WHITE),
                        Component.text(if (chosen) "선택됨 · 클릭하여 해제" else "클릭하여 이 스택 전체 선택", NamedTextColor.YELLOW)))
                    icon.itemMeta = meta
                    holder.actions[slot] = MenuAction.SaleSlot(slot, stack, price.units, chosen)
                } catch (failure: Exception) {
                    val meta = icon.itemMeta
                    meta.lore((meta.lore() ?: emptyList()) + Component.text("가격 확인 필요 · 판매 불가", NamedTextColor.RED)); icon.itemMeta = meta
                    plugin.logger.warning("Invalid sale quote for slot $slot: ${failure.message}")
                }
                holder.inventory.setItem(slot, icon)
            }
            fun button(slot: Int, material: Material, label: String, action: MenuAction) {
                val icon = ItemStack(material); val meta = icon.itemMeta
                meta.customName(Component.text(label)); icon.itemMeta = meta
                holder.inventory.setItem(slot, icon); holder.actions[slot] = action
            }
            button(45, Material.GOLD_INGOT, "선택한 스택 판매: ${total.units}원", MenuAction.SaleConfirm(before, prices.toMap()))
            button(49, Material.CLOCK, "현재 인벤토리 새로고침", MenuAction.SaleRefresh)
            button(53, Material.BARRIER, "선택 초기화", MenuAction.SaleClear)
            openMenu(plugin, player, holder.inventory, expected)
        }
    }

    @EventHandler
    fun click(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.SELL) return
        event.isCancelled = true
        if (event.rawSlot !in 0 until top.size || event.click != ClickType.LEFT) return
        val player = event.whoClicked as? Player ?: return
        if (FileManager.delivery.blocked(player.uniqueId) || !player.hasPermission("limpi.financial.sell")) return
        val action = holder.actions[event.rawSlot] ?: return
        val selected = holder.actions.values.filterIsInstance<MenuAction.SaleSlot>().filter { it.selected }
            .associate { it.index to it.stack }.toMutableMap()
        try {
            when (action) {
                is MenuAction.SaleSlot -> {
                    if (action.selected) selected.remove(action.index) else selected[action.index] = action.stack
                    openSellGUI(player, selected, top)
                }
                MenuAction.SaleRefresh -> openSellGUI(player, selected, top)
                MenuAction.SaleClear -> openSellGUI(player, emptyMap(), top)
                is MenuAction.SaleConfirm -> {
                    require(action.prices.isNotEmpty()) { "판매할 스택을 선택하세요." }
                    for ((slot, price) in action.prices) {
                        val item = PaperItemCodec.item(requireNotNull(action.before[slot]).payload, 1)
                        require(CatalogPrices.sell(FileManager.getSellPriceConfig().get(item.type.name.lowercase(Locale.ROOT))).units == price) {
                            "가격이 변경되었습니다. 새로고침 후 다시 확인하세요."
                        }
                    }
                    val request = UUID.randomUUID()
                    if (FileManager.delivery.sell(player, request, action.before, action.prices.mapValues { Money(it.value) })) {
                        openSellGUI(player, emptyMap(), top)
                    }
                }
                else -> Unit
            }
        } catch (failure: BankException) {
            player.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: IllegalArgumentException) {
            player.sendMessage(Component.text(failure.message ?: "판매 대상을 다시 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Sale menu failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("판매 기록을 확인해야 합니다. 관리자에게 문의하세요.", NamedTextColor.RED))
        }
    }
}
