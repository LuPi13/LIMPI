package com.github.lupi13.limpi.menu

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.ability.AbilitySelection
import com.github.lupi13.limpi.ability.SelectionTerms
import com.github.lupi13.limpi.events.AbilityDictionary
import com.github.lupi13.limpi.events.AbilitySelectEvent
import com.github.lupi13.limpi.events.SortOption
import com.github.lupi13.limpi.platform.AbilityTickets
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Level

object AbilityMenus {
    private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
    fun open(player: Player, kind: MenuKind, requestedPage: Int, sort: SortOption, expected: Inventory? = null) {
        require(kind == MenuKind.ABILITY_SELECT || kind == MenuKind.ABILITY_DICTIONARY)
        if (FileManager.delivery.blocked(player.uniqueId)) return
        val dictionary = kind == MenuKind.ABILITY_DICTIONARY
        val entries = if (dictionary) AbilityManager.abilities.toList() else
            FileManager.getPlayerData(player).getStringList("unlockedAbilities").distinct().mapNotNull(AbilityManager::getAbilityByCodeName)
        val sorted = when (sort) {
            SortOption.UNLOCKED -> entries
            SortOption.GANADA -> entries.sortedBy { PlainTextComponentSerializer.plainText().serialize(it.displayName) }
            SortOption.GRADE -> entries.sortedByDescending { it.grade }
            SortOption.ELEMENT -> entries.sortedBy { it.element }
        }
        val pagination = Pagination(sorted.size, if (requestedPage <= 1) 0 else requestedPage - 1)
        val holder = MenuHolder(kind, pagination.page, if (dictionary) AbilityDictionary.abilityDictionaryGUIName else AbilitySelectEvent.abilitySelectGUIName)
        for ((slot, ability) in sorted.subList(pagination.start, pagination.end).withIndex()) {
            holder.inventory.setItem(slot, ability.getItem())
            holder.actions[slot] = MenuAction.AbilityCode(ability.codeName)
        }
        if (pagination.hasPrevious) {
            holder.inventory.setItem(47, AbilitySelectEvent.getPreviousPageItem())
            holder.actions[47] = MenuAction.Page(pagination.page - 1)
        }
        if (pagination.hasNext) {
            holder.inventory.setItem(51, AbilitySelectEvent.getNextPageItem())
            holder.actions[51] = MenuAction.Page(pagination.page + 1)
        }
        holder.inventory.setItem(49, AbilitySelectEvent.getSortOptionItem(sort, dictionary))
        holder.actions[49] = MenuAction.AbilitySort(sort.ordinal)
        openMenu(plugin, player, holder.inventory, expected)
    }
    fun click(event: InventoryClickEvent, kind: MenuKind) {
        val top = event.view.topInventory
        val holder = top.holder as? MenuHolder ?: return
        if (holder.kind != kind) return
        event.isCancelled = true
        if (event.rawSlot !in 0 until top.size || event.click !in setOf(ClickType.LEFT, ClickType.RIGHT)) return
        val player = event.whoClicked as? Player ?: return
        if (FileManager.delivery.blocked(player.uniqueId)) return
        val sort = SortOption.entries[(holder.actions[49] as MenuAction.AbilitySort).ordinal]
        try {
            when (val action = holder.actions[event.rawSlot]) {
                is MenuAction.Page -> open(player, kind, action.index + 1, sort, top)
                is MenuAction.AbilitySort -> {
                    val delta = if (event.click == ClickType.LEFT) 1 else -1
                    val next = SortOption.entries[Math.floorMod(action.ordinal + delta, SortOption.entries.size)]
                    open(player, kind, holder.page + 1, next, top)
                }
                is MenuAction.AbilityCode -> {
                    val ability = requireNotNull(AbilityManager.getAbilityByCodeName(action.code))
                    if (event.click == ClickType.RIGHT) ability.sendAbilityInfo(player)
                    else if (kind == MenuKind.ABILITY_SELECT) select(player, ability)
                    else if (player.hasPermission("limpi.ability.admin.set") && FileManager.delivery.claim(player)) AbilityManager.applyAbility(player, ability)
                }
                else -> Unit
            }
        } catch (failure: IllegalArgumentException) {
            player.sendMessage(Component.text(failure.message ?: "선택 조건을 다시 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Ability menu request failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("능력 선택 기록을 확인해야 합니다. 관리자에게 문의하세요.", NamedTextColor.RED))
        }
    }
    private fun select(player: Player, ability: Ability) {
        require(player.hasPermission("limpi.ability.select")) { "능력 선택 권한이 없습니다." }
        if (!FileManager.delivery.claim(player)) return
        val previous = AbilityManager.getPlayerAbilityCodeName(player).orEmpty()
        require(previous != ability.codeName) { "이미 선택한 능력입니다." }
        require(ability.codeName in FileManager.getPlayerData(player).getStringList("unlockedAbilities")) { "해금하지 않은 능력입니다." }
        val before = PaperItemCodec.snapshot(player, includeOffHand = true)
        val slot = before.indexOfFirst { stack ->
            stack != null && AbilityTickets.identify(PaperItemCodec.item(stack.payload, stack.count)) == ability.grade.name
        }
        require(slot >= 0) { "해당 등급의 선택권이 필요합니다." }
        val ticket = requireNotNull(before[slot])
        val after = before.toMutableList().apply { this[slot] = if (ticket.count == 1) null else ticket.copy(count = ticket.count - 1) }.toList()
        try { AbilityManager.planInventory(player, ability, after) } catch (_: com.github.lupi13.limpi.delivery.InventoryFull) {
            throw IllegalArgumentException("제한 슬롯의 일반 아이템을 보관할 공간이 부족합니다. 인벤토리 공간을 확보하세요.")
        }
        val terms = SelectionTerms(ticket.payload, slot, ability.grade.name, previous, ability.codeName)
        val id = UUID.randomUUID()
        if (FileManager.delivery.remove(player, id, AbilitySelection.KIND, terms.encode(), before, after)) {
            player.sendMessage(Component.text("능력 선택을 저장했습니다. 거래: $id", NamedTextColor.GREEN))
            FileManager.delivery.claim(player)
        }
    }
}
