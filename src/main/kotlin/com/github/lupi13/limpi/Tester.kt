package com.github.lupi13.limpi

import com.github.lupi13.limpi.items.Check
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.components.FoodComponent

class Tester: CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            val player: Player = sender
            player.inventory.addItem(Check(args[0].toLong(), player))
            var item: ItemStack = ItemStack(Material.IRON_INGOT, 1)
            var meta: ItemMeta = item.itemMeta!!
            var food: FoodComponent = meta.food
            food.nutrition = 1
            food.saturation = 10f
            food.eatSeconds = 10f
            meta.setFood(food)
            item.itemMeta = meta
            player.inventory.addItem(item)
        }
        return true
    }
}