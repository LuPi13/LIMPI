package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.meta.Damageable

object EmeraldFix : Ability(
    grade = Grade.RARE,
    element = Element.SMITH,
    displayName = Component.text("에메랄드 수리", NamedTextColor.GREEN),
    codeName = "emerald_fix",
    material = Material.EMERALD,
    description = listOf(
        Component.text("에메랄드를 사용하여 아이템을 수리합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){
    override val details: List<Component> get() = listOf(
            Component.text("인벤토리에서 커서에 에메랄드를 들고 ", NamedTextColor.WHITE),
            Component.text("수리하고 싶은 아이템을 우클릭하면", NamedTextColor.WHITE),
            Component.text("내구도를 ", NamedTextColor.WHITE)
                .append(Component.text("${RepairPolicy.amount(config?.get("repair"))}", NamedTextColor.GREEN))
                .append(Component.text(" 수리합니다.", NamedTextColor.WHITE))
        )


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (player.ability != this) return

        // A result slot is only a preview that is rebuilt from its inputs; repairing it would spend the emerald for nothing.
        if (event.slotType == org.bukkit.event.inventory.InventoryType.SlotType.RESULT) return
        val cursorItem = event.cursor
        if (cursorItem.type != Material.EMERALD) return

        val clickedItem = event.currentItem ?: return
        val data = clickedItem.itemMeta as? Damageable ?: return
        if (!data.hasDamage()) return

        if (event.isRightClick) {
            val repairAmount = RepairPolicy.amount(config?.get("repair"))
            if (data.damage == 0 || repairAmount == 0) return
            event.isCancelled = true
            data.damage = RepairPolicy.damageAfter(data.damage, repairAmount)
            clickedItem.itemMeta = data
            event.currentItem = clickedItem
            event.view.setCursor(cursorItem.clone().apply { amount-- })
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1f, 1.5f)
        }
    }
}
