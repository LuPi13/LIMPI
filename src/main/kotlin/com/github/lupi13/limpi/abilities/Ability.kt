package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.storage.AtomicFiles
import com.github.lupi13.limpi.storage.YamlFiles
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.util.Locale.getDefault
import java.util.logging.Level

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
    val acquisition: AcquisitionType = AcquisitionType.GACHA,
    open val howToGet: Component = Component.text(acquisition.label),
    open val relatedQuest: List<Quest>? = null,
    open val needFile: Boolean = false,
    open var file: File? = null,
    open var config: YamlConfiguration? = null,
) : Listener {
    protected open val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

    open fun setUpFile() {
        if (!needFile) return

        file = File(plugin.dataFolder.toString() + File.separator + "ability", "${codeName}.yml")
        if (!Files.exists(file!!.toPath())) AtomicFiles.write(file!!.toPath(), "")
        config = YamlFiles.read(file!!.toPath())
    }

    fun saveConfig() {
        val data = config ?: return
        val path = requireNotNull(file) { "Ability $codeName has no configuration file" }.toPath()
        try {
            YamlFiles.write(path, data)
        } catch (e: Exception) {
            config = YamlFiles.read(path)
            plugin.logger.log(Level.SEVERE, "Unable to save ability $codeName; previous file preserved", e)
            throw e
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

    fun sendAbilityInfo(player: Player) {
        var text: Component = Component.text("\n", NamedTextColor.WHITE)
            .append(this.displayName)
            .append(Component.text(" ", NamedTextColor.WHITE))
            .append(this.grade.displayGrade)
            .append(Component.text(" ", NamedTextColor.WHITE))
            .append(this.element.displayElement)
            .append(Component.text("\n\n", NamedTextColor.WHITE))

        for (line in this.details) {
            text = text.append(line)
                .append(Component.text("\n", NamedTextColor.WHITE))
        }
        text = text.append(Component.text("\n획득 방법: ", NamedTextColor.WHITE))
            .append(this.howToGet)

        val relatedQuests = this.relatedQuest
        if (relatedQuests != null) {
            text = text.append(Component.text("\n\n관련 퀘스트: ", NamedTextColor.WHITE))
            for (quest in relatedQuests) {
                text = text.append(Component.text("", NamedTextColor.WHITE)
                    .append(quest.displayName)
                    .append(Component.text(" ", NamedTextColor.WHITE)))
            }
        }

        player.sendMessage(text)
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
