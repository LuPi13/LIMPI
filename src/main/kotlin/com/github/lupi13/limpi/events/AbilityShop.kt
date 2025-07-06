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
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import kotlin.math.min

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
                val price = if (grade == null) {
                    plugin.config.getInt("TicketPrice")
                } else {
                    plugin.config.getInt("SelectPrice.${grade.name}")
                }
                val money = Functions.getInventoryItemCount(player!!, ItemStack(Material.EMERALD))
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
            return item
        }

        fun openAbilityShopGUI(player: Player) {
            val shopGUI: Inventory = Bukkit.createInventory(player, 9, abilityShopGUIName)

            shopGUI.setItem(1, getSelectTicketItem(null, true, player))  // 뽑기권
            shopGUI.setItem(2, getSelectTicketItem(Grade.TROLL, true, player))
            shopGUI.setItem(3, getSelectTicketItem(Grade.COMMON, true, player))
            shopGUI.setItem(4, getSelectTicketItem(Grade.RARE, true, player))
            shopGUI.setItem(5, getSelectTicketItem(Grade.EPIC, true, player))
            shopGUI.setItem(6, getSelectTicketItem(Grade.LEGENDARY, true, player))
            shopGUI.setItem(7, getSelectTicketItem(Grade.MYTHIC, true, player))

            player.openInventory(shopGUI)
        }
    }


    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() != abilityShopGUIName) return
        event.isCancelled = true
        val abilityShopGUI = event.view.topInventory
        val player = event.whoClicked as Player
        if (event.currentItem == null || event.clickedInventory !== abilityShopGUI) return

        val item = event.currentItem!!
        event.isCancelled = true

        val grade = when (item) {
            getSelectTicketItem(Grade.TROLL, true, player) -> Grade.TROLL
            getSelectTicketItem(Grade.COMMON, true, player) -> Grade.COMMON
            getSelectTicketItem(Grade.RARE, true, player) -> Grade.RARE
            getSelectTicketItem(Grade.EPIC, true, player) -> Grade.EPIC
            getSelectTicketItem(Grade.LEGENDARY, true, player) -> Grade.LEGENDARY
            getSelectTicketItem(Grade.MYTHIC, true, player) -> Grade.MYTHIC
            else -> null  // 뽑기권
        }

        val price = when (grade) {
            Grade.TROLL -> plugin.config.getInt("SelectPrice.TROLL")
            Grade.COMMON -> plugin.config.getInt("SelectPrice.COMMON")
            Grade.RARE -> plugin.config.getInt("SelectPrice.RARE")
            Grade.EPIC -> plugin.config.getInt("SelectPrice.EPIC")
            Grade.LEGENDARY -> plugin.config.getInt("SelectPrice.LEGENDARY")
            Grade.MYTHIC -> plugin.config.getInt("SelectPrice.MYTHIC")
            else -> plugin.config.getInt("TicketPrice")
        }

        val money = Functions.getInventoryItemCount(player, ItemStack(Material.EMERALD))

        var count = if (money < price) 0 else 1
        if (event.click.isShiftClick) {
            count = min(money / price, 10)
        }

        if (event.click.isLeftClick) {
            if (count == 0) {
                player.sendMessage(Component.text("에메랄드가 부족합니다! ", NamedTextColor.RED))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                return
            }

            // 뽑기권
            if (item.isSimilar(getSelectTicketItem(null, true, player))) {
                if (Functions.validInventoryIndex(player, GachaEvents.getGachaTicketItem()) != null) {
                    player.inventory.addItem(GachaEvents.getGachaTicketItem().apply { amount = count })
                    Functions.removeInventoryItem(player, ItemStack(Material.EMERALD), price * count)
                    player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                    player.sendMessage(item.displayName()
                        .append(Component.text(" $count", NamedTextColor.GREEN))
                        .append(Component.text("개 구매 완료.", NamedTextColor.WHITE)))
                    openAbilityShopGUI(player)
                } else {
                    player.sendMessage(Component.text("인벤토리가 가득 찼습니다.", NamedTextColor.RED))
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                }
                return
            }

            // 능력 선택권
            else {
                if (Functions.validInventoryIndex(player, getSelectTicketItem(grade!!, false, null)) != null) {
                    player.inventory.addItem(getSelectTicketItem(grade, false, null).apply { amount = count })
                    Functions.removeInventoryItem(player, ItemStack(Material.EMERALD), price * count)
                    player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                    player.sendMessage(
                        item.displayName()
                            .append(Component.text(" $count", NamedTextColor.GREEN))
                            .append(Component.text("개 구매 완료.", NamedTextColor.WHITE))
                    )
                    openAbilityShopGUI(player)
                } else {
                    player.sendMessage(Component.text("인벤토리가 가득 찼습니다.", NamedTextColor.RED))
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                }
            }
        }
    }



    // 소모성 인벤토리(조합대 등)에 넣기 금지
    val allowedInventoryType = setOf(
        InventoryType.BARREL,
        InventoryType.CREATIVE,
        InventoryType.CHEST,
        InventoryType.DECORATED_POT,
        InventoryType.DISPENSER,
        InventoryType.DROPPER,
        InventoryType.ENDER_CHEST,
        InventoryType.HOPPER,
        InventoryType.PLAYER,
        InventoryType.SHULKER_BOX
    )
    val restrict =
        Grade.entries.map { getSelectTicketItem(it, false, null) } + getSelectTicketItem(null, false, null)
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        for (item in restrict) {
            if (event.currentItem != null && event.currentItem!!.isSimilar(item)) {
                if (event.clickedInventory != null && event.clickedInventory!!.type !in allowedInventoryType) {
                    event.isCancelled = true
                }
            }

            if (event.cursor.isSimilar(item)) {
                if (event.clickedInventory != null && event.clickedInventory!!.type !in allowedInventoryType) {
                    event.isCancelled = true
                }
            }

            if (event.action == InventoryAction.HOTBAR_SWAP) {
                val player = event.whoClicked as Player
                val slot = event.hotbarButton
                if (player.inventory.getItem(slot) != null && player.inventory.getItem(slot)!!.isSimilar(item)) {
                    if (event.inventory.type !in allowedInventoryType) {
                        event.isCancelled = true
                    }
                }
            }

            if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (event.currentItem != null && event.currentItem!!.isSimilar(item)) {
                    if (event.inventory.type !in allowedInventoryType) {
                        event.isCancelled = true
                    }
                }
            }
        }
    }
}