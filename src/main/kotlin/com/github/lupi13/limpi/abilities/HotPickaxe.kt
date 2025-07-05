package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent

object HotPickaxe : Ability(
    grade = Grade.RARE,
    element = Element.SMITH,
    displayName = Component.text("뜨거운 곡괭이", NamedTextColor.DARK_RED),
    codeName = "hot_pickaxe",
    material = Material.NETHERITE_PICKAXE,
    description = listOf(
        Component.text("곡괭이로 광물을 캐면 익혀서 나옵니다.", NamedTextColor.WHITE),
        Component.text("적을 곡괭이로 공격하면 불태웁니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {

    override val details: List<Component> by lazy {
        listOf(
            Component.text("곡괭이로 광물을 캐면 익혀서 나옵니다.", NamedTextColor.WHITE),
            Component.text("적을 곡괭이로 공격하면 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("fireTicks") / 20.0}초", NamedTextColor.GREEN))
                .append(Component.text(" 간 불태웁니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("fireTicks", 60)
        }
        saveConfig()
    }

    val pickaxes = listOf(
        Material.NETHERITE_PICKAXE,
        Material.DIAMOND_PICKAXE,
        Material.GOLDEN_PICKAXE,
        Material.IRON_PICKAXE,
        Material.STONE_PICKAXE,
        Material.WOODEN_PICKAXE
    )

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        if (player.ability != this) return

        if (!pickaxes.contains(player.inventory.itemInMainHand.type)) return

        // 적에게 불태우기 효과 적용
        event.entity.fireTicks = config!!.getInt("fireTicks")
    }


    @EventHandler
    fun onBreakBlock(event: BlockDropItemEvent) {
        val player = event.player
        if (player.ability != this) return

        if (!pickaxes.contains(player.inventory.itemInMainHand.type)) return

        event.items.forEach { item ->
            val newType = when (item.itemStack.type) {
                Material.RAW_IRON -> Material.IRON_INGOT
                Material.RAW_GOLD -> Material.GOLD_INGOT
                Material.RAW_COPPER -> Material.COPPER_INGOT
                else -> item.itemStack.type // 다른 광물은 그대로 유지
            }
            if (newType != item.itemStack.type) {
                item.itemStack.type = newType
            }
        }
    }
}