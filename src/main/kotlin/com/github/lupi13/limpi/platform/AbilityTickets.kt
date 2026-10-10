package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.abilities.Grade
import com.github.lupi13.limpi.events.AbilityShop
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Stable identity for newly issued tickets; exact old metadata remains usable without rewriting it. */
object AbilityTickets {
    private val kindKey = NamespacedKey("limpi", "ability_ticket")
    private val formatKey = NamespacedKey("limpi", "ability_ticket_format")
    private val kinds = setOf("GACHA") + Grade.entries.map { it.name }
    private val legacyTemplates by lazy {
        kinds.associateWith { kind ->
            AbilityShop.getSelectTicketItem(if (kind == "GACHA") null else Grade.valueOf(kind), false, null).apply {
                val meta = itemMeta
                meta.persistentDataContainer.remove(kindKey); meta.persistentDataContainer.remove(formatKey)
                itemMeta = meta
            }
        }
    }
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
        if (item.type != Material.NETHER_STAR && item.type != Material.TURTLE_SCUTE) return null
        // No inference from a name or model alone. Old tickets must match the full legacy template.
        for ((kind, template) in legacyTemplates) {
            if (item.isSimilar(template)) return kind
        }
        return null
    }
}
