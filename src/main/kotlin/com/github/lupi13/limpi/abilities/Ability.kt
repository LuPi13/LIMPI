package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.quests.Quest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale.getDefault

abstract class Ability(
    val grade: Grade,
    val element: Element,
    val displayName: Component,
    val codeName: String,
    val material: Material,
    open val description: List<Component>,
    open val details: List<Component> = description,
    open val restrictedSlot: Int? = null,
    open val activeItem: ItemStack? = null,
    open val howToGet: Component = Component.text("뽑기로 획득"),
    open val relatedQuest: List<Quest>? = null,
    val needFile: Boolean = false,
    open var file: File? = null,
    open var config: YamlConfiguration? = null
) : Listener {
    protected open val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

    open fun setUpFile() {
        if (needFile) {
            file = File(plugin.dataFolder.toString() + File.separator + "ability", "${codeName}.yml")
            if (!file!!.exists()) {
                try {
                    file!!.createNewFile()
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            config = YamlConfiguration.loadConfiguration(file!!)
        }
    }

    fun saveConfig() {
        try {
            config?.save(file!!)
        } catch (e: Exception) {
            println("\u001B[31m" + e.message + "\u001B[0m")
        }
    }

    fun registerEvents() {
        Bukkit.getServer().pluginManager.registerEvents(this, plugin)
    }

    fun getItem(): ItemStack {
        val item = ItemStack(Material.TURTLE_SCUTE, 1)
        val meta = item.itemMeta
        meta.itemModel = NamespacedKey("minecraft", material.name.lowercase(getDefault()))
        meta.itemName(displayName
            .append(Component.text(" ")).append(element.displayElement)
            .append(Component.text(" ")).append(grade.displayGrade))

        val lore = mutableListOf<Component>()
        for (line in description) {
            lore.add(line.decoration(TextDecoration.ITALIC, false))
        }
        meta.lore(lore)
        // ItemFlag 전부 추가
        ItemFlag.entries.forEach { flag ->
            meta.addItemFlags(flag)
        }
        item.itemMeta = meta
        return item
    }
}

enum class Grade(val displayGrade: Component) {
    TROLL(Component.text("(TROLL)", NamedTextColor.DARK_GRAY)),
    COMMON(Component.text("(COMMON)", NamedTextColor.DARK_GREEN)),
    RARE(Component.text("(RARE)", NamedTextColor.DARK_AQUA)),
    EPIC(Component.text("(EPIC)", NamedTextColor.DARK_PURPLE)),
    LEGENDARY(Component.text("(LEGENDARY)", NamedTextColor.GOLD)),
    MYTHIC(Component.text("(MYTHIC)", NamedTextColor.DARK_RED))
}

enum class Element(val displayElement: Component) {
    NONE(Component.text("[무]", NamedTextColor.WHITE)),
    FIRE(Component.text("[불]", NamedTextColor.RED)),
    WATER(Component.text("[물]", NamedTextColor.BLUE)),
    ELECTRIC(Component.text("[번개]", NamedTextColor.YELLOW)),
    MAGIC(Component.text("[마법]", NamedTextColor.LIGHT_PURPLE)),
    EXPLOSIVE(Component.text("[폭발]", NamedTextColor.DARK_RED)),
    DEFENSIVE(Component.text("[방어]", NamedTextColor.GRAY)),
    UNDEAD(Component.text("[언데드]", NamedTextColor.DARK_GREEN)),
    UTILITY(Component.text("[유틸리티]", NamedTextColor.AQUA)),
    SMITH(Component.text("[장인]", NamedTextColor.DARK_AQUA))
}