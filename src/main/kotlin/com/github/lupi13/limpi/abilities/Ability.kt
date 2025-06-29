package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.LIMPI
import com.google.common.collect.Lists
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

abstract class Ability(
    val grade: Grade,
    val displayName: Component,
    val codeName: String,
    val material: Material,
    val description: List<Component>,
    val restrictedSlot: Int?,
    val attribute: Attribute
) : Listener {
    protected open val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)
    open fun registerEvents() {
        Bukkit.getServer().pluginManager.registerEvents(this, plugin)
    }
    open fun startTask() {
    }
    fun getItem(): ItemStack {
        val item = ItemStack(material, 1)
        val meta = item.itemMeta
        meta.itemName(displayName
            .append(Component.text(" ")).append(attribute.displayAttribute)
            .append(Component.text(" ")).append(grade.displayGrade))

        meta.lore(description)
        item.itemMeta = meta
        return item
    }
}

enum class Grade(val displayGrade: Component) {
    TROLL(Component.text("(TROLL)", NamedTextColor.DARK_GRAY)),
    COMMON(Component.text("(COMMON)", NamedTextColor.WHITE)),
    RARE(Component.text("(RARE)", NamedTextColor.DARK_AQUA)),
    EPIC(Component.text("(EPIC)", NamedTextColor.DARK_PURPLE)),
    LEGENDARY(Component.text("(LEGENDARY)", NamedTextColor.GOLD)),
    MYTHIC(Component.text("(MYTHIC)", NamedTextColor.DARK_RED))
}

enum class Attribute(val displayAttribute: Component) {
    NONE(Component.text("[무]", NamedTextColor.WHITE)),
    FIRE(Component.text("[불]", NamedTextColor.RED)),
    WATER(Component.text("[물]", NamedTextColor.AQUA)),
    ELECTRIC(Component.text("[번개]", NamedTextColor.YELLOW)),
    MAGIC(Component.text("[마법]", NamedTextColor.LIGHT_PURPLE)),
    EXPLOSIVE(Component.text("[폭발]", NamedTextColor.DARK_RED)),
    DEFENSIVE(Component.text("[방어]", NamedTextColor.GRAY)),
    UNDEAD(Component.text("[언데드]", NamedTextColor.DARK_GREEN)),
    UTILITY(Component.text("[유틸리티]", NamedTextColor.DARK_AQUA))
}