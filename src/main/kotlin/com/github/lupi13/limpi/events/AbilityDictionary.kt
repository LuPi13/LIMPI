package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.menu.AbilityMenus
import com.github.lupi13.limpi.menu.MenuKind
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class AbilityDictionary : Listener {
    companion object {
        val abilityDictionaryGUIName = Component.text("LIMPI: Dictionary", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
        fun openAbilityDictionaryGUI(player: Player, page: Int, sort: SortOption) =
            AbilityMenus.open(player, MenuKind.ABILITY_DICTIONARY, page, sort)
    }
    @EventHandler
    fun onClick(event: InventoryClickEvent) = AbilityMenus.click(event, MenuKind.ABILITY_DICTIONARY)
}
