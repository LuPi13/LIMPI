package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.Grade
import com.github.lupi13.limpi.abilities.ability
import com.github.lupi13.limpi.events.AbilityShop.Companion.abilityShopGUIName
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

enum class SortOption {
    UNLOCKED,
    GANADA,
    GRADE,
    ELEMENT
}

class AbilitySelectEvent : Listener {
    companion object {
        private val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        val abilitySelectGUIName = Component.text("LIMPI: 능력 선택", NamedTextColor.GOLD, TextDecoration.BOLD)

        /**
         * 능력 선택 페이지에 넣을 '다음 페이지' 아이템을 생성합니다.
         */
        fun getNextPageItem(): ItemStack {
            val item = ItemStack(Material.LIME_DYE, 1)
            val meta = item.itemMeta
            meta.displayName(Component.text("다음 페이지", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    Component.text("클릭하여 다음 페이지로 이동합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                )
            )
            item.itemMeta = meta
            return item
        }

        /**
         * 능력 선택 페이지에 넣을 '이전 페이지' 아이템을 생성합니다.
         */
        fun getPreviousPageItem(): ItemStack {
            val item = ItemStack(Material.PINK_DYE, 1)
            val meta = item.itemMeta
            meta.displayName(Component.text("이전 페이지", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    Component.text("클릭하여 이전 페이지로 이동합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                )
            )
            item.itemMeta = meta
            return item
        }

        /**
         * 능력 선택 페이지에 넣을 '정렬 옵션' 아이템을 생성합니다.
         */
        fun getSortOptionItem(sort: SortOption, isInDictionary: Boolean): ItemStack {
            val item = ItemStack(Material.BOOK, 1)
            val meta = item.itemMeta
            meta.displayName(
                Component.text("정렬 옵션", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
            )
            val lore = mutableListOf(
                Component.text("클릭하여 정렬 옵션을 변경합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
            )
            if (isInDictionary) {
                lore.add(Component.text("업데이트순", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            } else {
                lore.add(Component.text("획득순", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            }
            lore += listOf(
            Component.text("가나다순", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
            Component.text("등급순", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
            Component.text("속성순", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
            )


            for (i in 1..4) {
                if (i-1 == sort.ordinal) {
                    lore[i] = lore[i].color(NamedTextColor.GREEN).decoration(TextDecoration.STRIKETHROUGH, false)
                } else {
                    lore[i] = lore[i].color(NamedTextColor.GRAY).decoration(TextDecoration.STRIKETHROUGH, true)
                }
            }
            meta.lore(lore)
            item.itemMeta = meta
            return item
        }

        /**
         * 플레이어가 능력 선택 GUI를 열도록 합니다.
         * @param player 능력 선택 GUI를 열 플레이어
         * @param page 페이지 번호 (1부터 시작)
         * @param sort 정렬 옵션
         */
        val sortOption = mutableMapOf<Player, SortOption>()
        fun openAbilitySelectGUI(player: Player, page: Int, sort: SortOption) {
            val selectGUI: Inventory = Bukkit.createInventory(player, 54, abilitySelectGUIName)
            val playerConfig = FileManager.getPlayerData(player)
            val unlockedCodeName = playerConfig.getStringList("unlockedAbilities")
            val unlockedAbilities = unlockedCodeName.map { AbilityManager().getAbilityByCodeName(it) }

            sortOption[player] = sort

            val sortedAbilities = when (sort) {
                SortOption.UNLOCKED -> unlockedAbilities.filterNotNull()
                SortOption.GANADA -> unlockedAbilities.filterNotNull().sortedBy { PlainTextComponentSerializer.plainText().serialize(it.displayName) }
                SortOption.GRADE -> unlockedAbilities.filterNotNull().sortedByDescending { it.grade }
                SortOption.ELEMENT -> unlockedAbilities.filterNotNull().sortedBy { it.element }
            }

            var isLastPage = false
            for (i in 0..44) {
                val abilityIndex = (page - 1) * 45 + i
                if (abilityIndex >= sortedAbilities.size) {
                    isLastPage = true
                    break
                }

                val ability = sortedAbilities[abilityIndex]

                val item = ability.getItem()
                selectGUI.setItem(i, item)
            }

            // 페이지 네비게이션 아이템 추가
            if (page > 1) selectGUI.setItem(47, getPreviousPageItem())
            if (!isLastPage) selectGUI.setItem(51, getNextPageItem())
            selectGUI.setItem(49, getSortOptionItem(sort, false))


            player.openInventory(selectGUI)
        }
    }


    val page = mutableMapOf<Player, Int>()
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() != abilitySelectGUIName) return
        event.isCancelled = true
        val abilityShopGUI = event.view.topInventory
        val player = event.whoClicked as Player
        if (event.currentItem == null || event.clickedInventory !== abilityShopGUI) return

        val item = event.currentItem!!
        event.isCancelled = true

        if (!page.containsKey(player)) page[player] = 1
        if (!sortOption.containsKey(player)) sortOption[player] = SortOption.UNLOCKED

        // 페이지 이동 및 정렬 옵션 변경 처리
        if (item == getPreviousPageItem()) {
            openAbilitySelectGUI(player, page[player]!! - 1, sortOption[player]!!)
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f)
        }
        else if (item == getNextPageItem()) {
            openAbilitySelectGUI(player, page[player]!! + 1, sortOption[player]!!)
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f)
        }
        else if (item == getSortOptionItem(sortOption[player]!!, false)) {
            if (event.isLeftClick) {
                sortOption[player] = when (sortOption[player]) {
                    SortOption.UNLOCKED -> SortOption.GANADA
                    SortOption.GANADA -> SortOption.GRADE
                    SortOption.GRADE -> SortOption.ELEMENT
                    SortOption.ELEMENT -> SortOption.UNLOCKED
                    else -> SortOption.UNLOCKED
                }
                player.playSound(player.location, Sound.BLOCK_VAULT_ACTIVATE, 1f, 2f)
                openAbilitySelectGUI(player, page[player]!!, sortOption[player]!!)
            }
            if (event.isRightClick) {
                sortOption[player] = when (sortOption[player]) {
                    SortOption.UNLOCKED -> SortOption.ELEMENT
                    SortOption.GANADA -> SortOption.UNLOCKED
                    SortOption.GRADE -> SortOption.GANADA
                    SortOption.ELEMENT -> SortOption.GRADE
                    else -> SortOption.UNLOCKED
                }
                player.playSound(player.location, Sound.BLOCK_VAULT_ACTIVATE, 1f, 2f)
                openAbilitySelectGUI(player, page[player]!!, sortOption[player]!!)
            }
        }

        // 능력 아이템 클릭 처리
        else {
            val clickedAbility = AbilityManager().getAbilityByItem(item)!!

            val grade = clickedAbility.grade

            if (Functions.getInventoryItemCount(player, AbilityShop.getSelectTicketItem(grade, false, null)) >= 1) {
                if (player.ability == clickedAbility) {
                    player.sendMessage(Component.text("이미 선택한 능력입니다.", NamedTextColor.RED))
                    player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f)
                }
                else {
                    Functions.removeInventoryItem(player, AbilityShop.getSelectTicketItem(grade, false, null), 1)
                    AbilityManager().applyAbility(player, clickedAbility)
                }
            }
            else {
                player.sendMessage(Component.text("능력을 선택하려면 해당 등급의 선택권이 필요합니다.", NamedTextColor.RED))
                player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f)
            }
        }
    }
}