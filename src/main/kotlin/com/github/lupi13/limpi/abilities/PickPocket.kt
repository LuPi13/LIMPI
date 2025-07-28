package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

object PickPocket : Ability(
    grade = Grade.RARE,
    element = Element.UTILITY,
    displayName = Component.text("소매치기", NamedTextColor.RED),
    codeName = "pick_pocket",
    material = Material.FISHING_ROD,
    description = listOf(
        Component.text("낚싯대로 엔티티를 당길 때, 해당 대상의", NamedTextColor.WHITE),
        Component.text("인벤토리에서 무작위 아이템을 하나 낚아챕니다.", NamedTextColor.WHITE)
    ),
){


    val playerFishHookMap = mutableMapOf<Player, FishHook>()

    @EventHandler
    fun onThrowFishHook(event: ProjectileLaunchEvent) {
        val entity = event.entity
        if (entity !is FishHook) return
        if (entity.shooter == null || entity.shooter !is Player) return

        val player = entity.shooter as Player
        if (player.ability != this) return

        // 플레이어가 낚싯대를 던졌을 때, 플레이어와 낚싯대의 매핑을 저장
        playerFishHookMap[player] = entity
    }



    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this) return
        if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK || event.action == Action.PHYSICAL) return
        if (event.item == null || event.item!!.type != Material.FISHING_ROD) return

        // 낚싯대 회수
        if (playerFishHookMap.containsKey(player)) {
            val fishHook = playerFishHookMap[player]!!
            if (fishHook.state == FishHook.HookState.HOOKED_ENTITY) {
                val entity = fishHook.hookedEntity

                // 상자달린 말
                if (entity is ChestedHorse) {
                    val inventory = entity.inventory.contents
                    for ((index, item) in inventory.withIndex().shuffled()) {
                        if (item != null && item.type != Material.AIR) {
                            val droppedItem: Item = player.world.dropItemNaturally(fishHook.location, item)
                            droppedItem.velocity = player.eyeLocation.toVector()
                                    .subtract(fishHook.location.toVector()).multiply(0.125)

                            inventory[index] = ItemStack(Material.AIR)
                            entity.inventory.contents = inventory
                            break
                        }
                    }

                }


                // inventory 속성이 있는 엔티티
                else if (entity is Player || entity is Villager || entity is AbstractHorse) {
                    if (entity is Player && entity.gameMode == GameMode.CREATIVE) {
                        playerFishHookMap.remove(player)
                        return
                    }
                    val inventory = when (entity) {
                        is Player -> {
                            entity.inventory
                        }

                        is Villager -> {
                            entity.inventory
                        }

                        else -> {
                            (entity as AbstractHorse).inventory
                        }
                    }

                    searchItem@for (item in inventory.shuffled()) {
                        if (item != null && item.type != Material.AIR) {
                            // 능력 아이템은 무시
                            for (active in AbilityManager().activeItems) {
                                if (item.isSimilar(active)) {
                                    continue@searchItem
                                }
                            }
                            val droppedItem: Item = player.world.dropItemNaturally(fishHook.location, item)
                            droppedItem.velocity = player.eyeLocation.toVector()
                                    .subtract(fishHook.location.toVector()).multiply(0.125)

                            inventory.removeItemAnySlot(item)
                            break
                        }
                    }
                }


                else if (entity is LivingEntity) {
                    val inventory = entity.equipment
                    // 생물의 장비에서 무작위 아이템을 하나 훔침
                    val slots = EquipmentSlot.entries.shuffled()
                    for (slot in slots) {
                        val item = inventory?.getItem(slot)
                        if (item != null && item.type != Material.AIR) {
                            val droppedItem: Item = player.world.dropItemNaturally(fishHook.location, item)
                            droppedItem.velocity = player.eyeLocation.toVector()
                                .subtract(fishHook.location.toVector()).multiply(0.125)
                            inventory.setItem(slot, ItemStack(Material.AIR))
                            break
                        }
                    }
                }
            }
            playerFishHookMap.remove(player)
        }
    }
}