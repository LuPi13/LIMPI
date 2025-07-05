package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object Alchemy : Ability(
    grade = Grade.EPIC,
    element = Element.SMITH,
    displayName = Component.text("연금술", NamedTextColor.GOLD),
    codeName = "alchemy",
    material = Material.RAW_GOLD,
    description = listOf(
        Component.text("구리 주괴를 손에 들고 우클릭하면", NamedTextColor.WHITE),
        Component.text("10개를 소모하여 다른 광물로 변환합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){
    override val details: List<Component> by lazy {
        listOf(
            Component.text("구리 주괴를 손에 들고 우클릭하면", NamedTextColor.WHITE),
            Component.text("10개를 소모하여 다른 광물로 변환합니다.", NamedTextColor.WHITE),
            Component.text("변환 가능한 광물은 다음과 같습니다:", NamedTextColor.WHITE),
            Component.text("석탄: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("coal") * 100}%", NamedTextColor.GREEN))
                .append(Component.text(" / 철 조각: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("iron_nugget") * 100}%", NamedTextColor.GREEN)))
                .append(Component.text(" / 철 주괴: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("iron_ingot") * 100}%", NamedTextColor.GREEN)))
                .append(Component.text(" / 금 조각: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("gold_nugget") * 100}%", NamedTextColor.GREEN))),
            Component.text("금 주괴: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("gold_ingot") * 100}%", NamedTextColor.GREEN))
                .append(Component.text(" / 레드스톤: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("redstone") * 100}%", NamedTextColor.GREEN)))
                .append(Component.text(" / 청금석: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("lapis_lazuli") * 100}%", NamedTextColor.GREEN)))
                .append(Component.text(" / 에메랄드: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("emerald") * 100}%", NamedTextColor.GREEN))),
            Component.text("다이아몬드: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("diamond") * 100}%", NamedTextColor.GREEN))
                .append(Component.text(" / 네더라이트 주괴: ", NamedTextColor.WHITE).append(Component.text("${config!!.getDouble("netherite_ingot") * 100}%", NamedTextColor.GREEN)))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("coal", 0.3)
            config?.set("iron_nugget", 0.15)
            config?.set("iron_ingot", 0.1)
            config?.set("gold_nugget", 0.15)
            config?.set("gold_ingot", 0.1)
            config?.set("redstone", 0.1)
            config?.set("lapis_lazuli", 0.05)
            config?.set("emerald", 0.039)
            config?.set("diamond", 0.01)
            config?.set("netherite_ingot", 0.001)
        }
        saveConfig()
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this) return
        if (event.item == null || event.item!!.type != Material.COPPER_INGOT || event.item!!.amount < 10) return
        if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK || event.action == Action.PHYSICAL) return

        event.item!!.amount -= 10
        val coalDrop = config!!.getDouble("coal")
        val ironNuggetDrop = coalDrop + config!!.getDouble("iron_nugget")
        val ironIngotDrop = ironNuggetDrop + config!!.getDouble("iron_ingot")
        val goldNuggetDrop = ironIngotDrop + config!!.getDouble("gold_nugget")
        val goldIngotDrop = goldNuggetDrop + config!!.getDouble("gold_ingot")
        val redstoneDrop = goldIngotDrop + config!!.getDouble("redstone")
        val lapisLazuliDrop = redstoneDrop + config!!.getDouble("lapis_lazuli")
        val emeraldDrop = lapisLazuliDrop + config!!.getDouble("emerald")
        val diamondDrop = emeraldDrop + config!!.getDouble("diamond")
        val netheriteIngotDrop = diamondDrop + config!!.getDouble("netherite_ingot")

        val random = Random.nextDouble()
        if (random < coalDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.COAL, 1))
            player.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.7f)
        }
        else if (random < ironNuggetDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.IRON_NUGGET, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < ironIngotDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.IRON_INGOT, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < goldNuggetDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.GOLD_NUGGET, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < goldIngotDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.GOLD_INGOT, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < redstoneDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.REDSTONE, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < lapisLazuliDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.LAPIS_LAZULI, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < emeraldDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.EMERALD, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < diamondDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.DIAMOND, 1))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        else if (random < netheriteIngotDrop) {
            player.world.dropItemNaturally(player.location, ItemStack(Material.NETHERITE_INGOT, 1))
            player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 2.0f)
        }
        else {
            player.world.dropItemNaturally(player.location, ItemStack(Material.COAL, 1))
            player.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.7f)
        }
        event.isCancelled = true
    }
}