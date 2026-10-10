package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.menu.AbilityMenus
import com.github.lupi13.limpi.menu.MenuKind
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
        fun openAbilitySelectGUI(player: Player, page: Int, sort: SortOption) =
            AbilityMenus.open(player, MenuKind.ABILITY_SELECT, page, sort)
    }
    @EventHandler
    fun onClick(event: InventoryClickEvent) = AbilityMenus.click(event, MenuKind.ABILITY_SELECT)
}
