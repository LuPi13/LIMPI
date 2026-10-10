package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.inventory.PrepareAnvilEvent

object HeavyEnchant : Ability(
    grade = Grade.LEGENDARY,
    element = Element.SMITH,
    displayName = Component.text("진보된 지식", NamedTextColor.AQUA),
    codeName = "heavy_enchant",
    material = org.bukkit.Material.ENCHANTED_BOOK,
    description = listOf(
        Component.text("마법부여대에서 부여받은 모든 마법부여의 등급이", NamedTextColor.WHITE),
        Component.text("강제로 1 높게 부여됩니다. 또, 붙일 수 없는", NamedTextColor.WHITE),
        Component.text("일부 마법부여를 모루에서 강제로 붙입니다.", NamedTextColor.WHITE)
    )
){
    override val details: List<Component> by lazy {
        description + listOf(Component.text("주의! 모루 합성이 완벽히 작동하지 않을 수 있습니다.", NamedTextColor.YELLOW))
    }


    @EventHandler(ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) {
        val player = event.enchanter
        if (player.ability != this) return

        val enchants = event.enchantsToAdd
        for (enchant in enchants.keys) {
            enchants[enchant] = enchants[enchant]!! + 1
        }
    }


    @EventHandler(ignoreCancelled = true)
    fun onAnvil(event: PrepareAnvilEvent) {
        val player = event.viewers.firstOrNull() as? Player ?: return
        if (player.ability != this) return

        val result = event.result ?: return
        // Books keep enchantments in stored enchants. Applying inputs one after another used to overwrite a higher
        // vanilla/result level with the last input's lower level.
        fun levels(item: org.bukkit.inventory.ItemStack?): Map<org.bukkit.enchantments.Enchantment, Int> =
            when (val meta = item?.itemMeta) {
                null -> emptyMap()
                is org.bukkit.inventory.meta.EnchantmentStorageMeta -> meta.storedEnchants + meta.enchants
                else -> meta.enchants
            }
        val merged = com.github.lupi13.limpi.ability.EnchantMerge.highest(levels(result), levels(event.inventory.firstItem), levels(event.inventory.secondItem))
        val meta = result.itemMeta ?: return
        if (meta is org.bukkit.inventory.meta.EnchantmentStorageMeta) merged.forEach { (enchant, level) -> meta.addStoredEnchant(enchant, level, true) }
        else merged.forEach { (enchant, level) -> meta.addEnchant(enchant, level, true) }
        result.itemMeta = meta
        event.result = result
    }
}
