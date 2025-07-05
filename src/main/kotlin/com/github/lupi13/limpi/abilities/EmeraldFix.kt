package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.meta.Damageable

object EmeraldFix : Ability(
    grade = Grade.EPIC,
    element = Element.SMITH,
    displayName = Component.text("에메랄드 수리", NamedTextColor.GREEN),
    codeName = "emerald_fix",
    material = Material.EMERALD,
    description = listOf(
        Component.text("에메랄드를 사용하여 아이템을 수리합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){
    override val details: List<Component> by lazy {
        listOf(
            Component.text("인벤토리에서 커서에 에메랄드를 들고 ", NamedTextColor.WHITE),
            Component.text("수리하고 싶은 아이템을 우클릭하면", NamedTextColor.WHITE),
            Component.text("내구도를 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("repair")}", NamedTextColor.GREEN))
                .append(Component.text(" 수리합니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("repair", 20)
        }
        saveConfig()
    }


    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as Player
        if (player.ability != this) return

        val cursorItem = event.cursor
        if (cursorItem.type != Material.EMERALD) return

        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR || clickedItem.itemMeta == null || !((clickedItem.itemMeta as Damageable).hasDamage())) return

        if (event.isRightClick) {
            val data = clickedItem.itemMeta as Damageable
            val repairAmount = config!!.getInt("repair")
            if (data.damage == 0) return
            data.damage = (data.damage - repairAmount).coerceAtLeast(0)
            clickedItem.itemMeta = data
            event.currentItem = clickedItem
            event.cursor.amount -= 1
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1f, 1.5f)
            event.isCancelled = true
        }
    }
}