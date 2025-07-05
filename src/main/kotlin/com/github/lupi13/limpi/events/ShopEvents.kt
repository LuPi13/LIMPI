package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.Financial
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.collections.ArrayList


class ShopEvents : Listener {
    companion object {
        private val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        fun getShopPrice(item: ItemStack): Int {
            val sellPrice = SellEvents.getSellPrice(item)
            val ratio = if (FileManager.getShopRatioConfig().getKeys(false).contains(item.type.toString().lowercase(Locale.getDefault()))) {
                FileManager.getShopRatioConfig().getDouble(item.type.toString().lowercase(Locale.getDefault()))
            } else {
                plugin.config.getDouble("DefaultShopRatio")
            }
            return (ratio * sellPrice).toInt()
        }

        val shopGUIName = Component.text("LIMPI: 구매창", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)

        fun openShopGUI(player: Player) {
            val shopGUI: Inventory = Bukkit.createInventory(player, 54, shopGUIName)
            var i = 0
            for (names in FileManager.getShopRatioConfig().getKeys(false)) {
                val item = ItemStack(Functions.toMaterial(names), 1)
                val meta = item.itemMeta
                val lore: MutableList<Component> = ArrayList()
                lore.add(Component.text("가격: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                    .append(moneyDisplay(getShopPrice(item)).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text("원, 현재 보유 금액: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                    .append(moneyDisplay(FileManager.getPlayerData(player)["money"] as Int).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("좌클릭하여 구매합니다.", NamedTextColor.WHITE))
                lore.add(Component.text("Shift를 누르고 클릭하면 1세트/최대한 많이 구매합니다.", NamedTextColor.WHITE))
                meta!!.lore(lore)
                item.itemMeta = meta
                shopGUI.setItem(i, item)
                i += 1
            }
            player.openInventory(shopGUI)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() == shopGUIName) {
            val shopGUI = event.view.topInventory
            val player = event.whoClicked as Player

            if (event.currentItem != null && event.clickedInventory === shopGUI) {
                val config = FileManager.getPlayerData(player)
                val money = config["money"] as Int
                val item = event.currentItem!!
                val price = getShopPrice(item)
                var count = 1
                if (event.click.isShiftClick) {
                    count = item.maxStackSize.coerceAtMost(money / price)
                }
                if (event.click.isLeftClick) {
                    if (money >= price) {
                        if (Functions.validInventoryIndex(player, ItemStack(item.type, count)) != null) {
                            config["money"] = money - (price * count)
                            FileManager.savePlayerData(player, config)
                            player.inventory.addItem(ItemStack(item.type, count))
                            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                            player.sendMessage(Component.text(item.type.toString().lowercase(Locale.getDefault()), NamedTextColor.AQUA)
                                .append(Component.text(" $count", NamedTextColor.GREEN))
                                .append(Component.text("개 구매 완료.", NamedTextColor.WHITE)))
                            openShopGUI(player)
                        }
                        else {
                            player.sendMessage(Component.text("인벤토리가 가득 찼습니다.", NamedTextColor.RED))
                            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                        }
                    } else {
                        player.sendMessage(Component.text("돈이 부족합니다! ", NamedTextColor.RED)
                            .append(playerDisplay(player))
                            .append(Component.text("님은 현재 계좌에 ", NamedTextColor.RED))
                            .append(moneyDisplay(money))
                            .append(Component.text("원 있습니다.", NamedTextColor.RED)))
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                    }
                }
                event.isCancelled = true
            }
            else {
                event.isCancelled = true
            }
        }
    }
}
