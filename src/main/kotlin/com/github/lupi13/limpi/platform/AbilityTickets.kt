package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.abilities.Grade
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Stable PDC identity for tickets; display metadata never identifies a ticket. */
object AbilityTickets {
    private val kindKey = NamespacedKey("limpi", "ability_ticket")
    private val formatKey = NamespacedKey("limpi", "ability_ticket_format")
    private val kinds = setOf("GACHA") + Grade.entries.map { it.name }
    fun protected(item: ItemStack?): Boolean = item != null && item.hasItemMeta() &&
        (item.itemMeta.persistentDataContainer.has(kindKey) || item.itemMeta.persistentDataContainer.has(formatKey) || identify(item) != null)
    fun mark(item: ItemStack, kind: String): ItemStack {
        require(kind in kinds)
        val meta = item.itemMeta
        meta.persistentDataContainer.set(kindKey, PersistentDataType.STRING, kind)
        meta.persistentDataContainer.set(formatKey, PersistentDataType.INTEGER, 1)
        item.itemMeta = meta
        return item
    }
    fun identify(item: ItemStack?): String? {
        if (item == null || item.type.isAir || !item.hasItemMeta()) return null
        val data = item.itemMeta.persistentDataContainer
        if (data.has(kindKey) || data.has(formatKey)) {
            val kind = data.get(kindKey, PersistentDataType.STRING) ?: return null
            if (kind !in kinds || data.get(formatKey, PersistentDataType.INTEGER) != 1) return null
            return kind.takeIf { item.type == if (kind == "GACHA") Material.NETHER_STAR else Material.TURTLE_SCUTE }
        }
        return null
    }
}
