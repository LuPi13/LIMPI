package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.ability
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin


class AbilityDictionary : Listener {
    companion object {
        private val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        val abilityDictionaryGUIName = Component.text("LIMPI: Dictionary", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)

        /**
         * 플레이어가 dictionary GUI를 열도록 합니다.
         * @param player 능력 선택 GUI를 열 플레이어
         * @param page 페이지 번호 (1부터 시작)
         * @param sort 정렬 옵션
         */
        val sortOption = mutableMapOf<Player, SortOption>()
        fun openAbilityDictionaryGUI(player: Player, page: Int, sort: SortOption) {
            val dictionaryGUI: Inventory = Bukkit.createInventory(player, 54, abilityDictionaryGUIName)
            val abilities = AbilityManager().abilities

            sortOption[player] = sort

            val sortedAbilities = when (sort) {
                SortOption.UNLOCKED -> abilities
                SortOption.GANADA -> abilities.sortedBy { PlainTextComponentSerializer.plainText().serialize(it.displayName) }
                SortOption.GRADE -> abilities.sortedByDescending { it.grade }
                SortOption.ELEMENT -> abilities.sortedBy { it.element }
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
                dictionaryGUI.setItem(i, item)
            }

            // 페이지 네비게이션 아이템 추가
            if (page > 1) dictionaryGUI.setItem(47, AbilitySelectEvent.getPreviousPageItem())
            if (!isLastPage) dictionaryGUI.setItem(51, AbilitySelectEvent.getNextPageItem())
            dictionaryGUI.setItem(49, AbilitySelectEvent.getSortOptionItem(sort, true))


            player.openInventory(dictionaryGUI)
        }
    }


    val page = mutableMapOf<Player, Int>()
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() != abilityDictionaryGUIName) return
        event.isCancelled = true
        val abilityShopGUI = event.view.topInventory
        val player = event.whoClicked as Player
        if (event.currentItem == null || event.clickedInventory !== abilityShopGUI) return

        val item = event.currentItem!!
        event.isCancelled = true

        if (!page.containsKey(player)) page[player] = 1
        if (!sortOption.containsKey(player)) sortOption[player] = SortOption.UNLOCKED

        // 페이지 이동 및 정렬 옵션 변경 처리
        if (item == AbilitySelectEvent.getPreviousPageItem()) {
            openAbilityDictionaryGUI(player, page[player]!! - 1, sortOption[player]!!)
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f)
        }
        else if (item == AbilitySelectEvent.getNextPageItem()) {
            openAbilityDictionaryGUI(player, page[player]!! + 1, sortOption[player]!!)
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f)
        }
        else if (item == AbilitySelectEvent.getSortOptionItem(sortOption[player]!!, true)) {
            if (event.isLeftClick) {
                sortOption[player] = when (sortOption[player]) {
                    SortOption.UNLOCKED -> SortOption.GANADA
                    SortOption.GANADA -> SortOption.GRADE
                    SortOption.GRADE -> SortOption.ELEMENT
                    SortOption.ELEMENT -> SortOption.UNLOCKED
                    else -> SortOption.UNLOCKED
                }
                player.playSound(player.location, Sound.BLOCK_VAULT_ACTIVATE, 1f, 2f)
                openAbilityDictionaryGUI(player, page[player]!!, sortOption[player]!!)
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
                openAbilityDictionaryGUI(player, page[player]!!, sortOption[player]!!)
            }
        }

        // 능력 아이템 클릭 처리: op 권한이 있으면 능력 적용
        else {
            if (player.isOp && event.isLeftClick) {
                val clickedAbility = AbilityManager().getAbilityByItem(item)!!


                if (player.ability == clickedAbility) {
                    player.sendMessage(Component.text("이미 선택한 능력입니다.", NamedTextColor.RED))
                    player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f)
                } else {
                    AbilityManager().applyAbility(player, clickedAbility)
                }
            }

            // 우클릭 시 상세 정보 출력
            else if (event.isRightClick) {
                val ability = AbilityManager().getAbilityByItem(item)!!
                var page = Component.text("\n", NamedTextColor.WHITE)
                    .append(ability.displayName)
                    .append(Component.text(" ", NamedTextColor.WHITE))
                    .append(ability.grade.displayGrade)
                    .append(Component.text(" ", NamedTextColor.WHITE))
                    .append(ability.element.displayElement)
                    .append(Component.text("\n\n", NamedTextColor.WHITE))
                for (line in ability.details) {
                    page = page.append(line)
                        .append(Component.text("\n", NamedTextColor.WHITE))
                }
                page = page.append(Component.text("\n획득 방법: ", NamedTextColor.WHITE))
                    .append(ability.howToGet)

                val relatedQuests = ability.relatedQuest
                if (relatedQuests != null) {
                    page = page.append(Component.text("\n\n관련 퀘스트: ", NamedTextColor.WHITE))
                    for (quest in relatedQuests) {
                        page = page.append(Component.text("", NamedTextColor.WHITE)
                            .append(quest.displayName)
                            .append(Component.text(" ", NamedTextColor.WHITE)))
                    }
                }

                player.sendMessage(page)
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
            }
        }
    }
}