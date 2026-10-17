package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.ability.PickPocketSettlement
import com.github.lupi13.limpi.ability.PickPocketTerms
import com.github.lupi13.limpi.platform.AbilityRunnable
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.logging.Level

object PickPocket : Ability(
    grade = Grade.RARE,
    displayName = Component.text("소매치기", NamedTextColor.RED),
    codeName = "pick_pocket",
    material = Material.FISHING_ROD,
    description = listOf(
        Component.text("낚싯대", NamedTextColor.YELLOW).append(Component.text("로 엔티티를 당길 때, 해당 대상의", NamedTextColor.WHITE)),
        Component.text("인벤토리에서 무작위 아이템을 하나 낚아챕니다.", NamedTextColor.WHITE)
    ),
) {
    private val processed = linkedSetOf<UUID>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCatch(event: PlayerFishEvent) {
        if (event.state != PlayerFishEvent.State.CAUGHT_ENTITY) return
        val player = event.player
        val target = event.caught as? LivingEntity ?: return
        if (player.ability != this || !eligible(player, target)) return
        val hook = event.hook.uniqueId
        if (!processed.add(hook)) return
        while (processed.size > 256) processed.remove(processed.first())
        val targetId = target.uniqueId
        val world = target.world
        val position = event.hook.location.clone()
        object : AbilityRunnable(player, codeName) {
            override fun tick() {
                // Read the event's final cancellation on the next tick, after every listener.
                if (event.isCancelled) return
                val victim = world.getEntity(targetId) as? LivingEntity ?: return
                if (!eligible(player, victim)) return
                try {
                    if (victim is Player) stealPlayer(player, victim, hook)
                    else stealEntity(player, victim, position)
                } catch (failure: Exception) {
                    plugin.logger.log(Level.WARNING, "Pick pocket $hook from $targetId failed", failure)
                }
            }
        }.runTask(plugin)
    }

    private fun eligible(player: Player, target: LivingEntity): Boolean {
        if (!target.isValid || target.isDead || target.world != player.world || !AbilityManager.isEnemy(player, target)) return false
        if (player.location.distanceSquared(target.location) > 128.0 * 128.0) return false
        if (FileManager.delivery.blocked(player.uniqueId)) return false
        if (target is Player && (!target.isOnline || target.world.getGameRuleValue(org.bukkit.Registry.GAME_RULE.getOrThrow(io.papermc.paper.registry.keys.GameRuleKeys.PVP)) != true || target.gameMode == GameMode.CREATIVE ||
                target.gameMode == GameMode.SPECTATOR || FileManager.delivery.blocked(target.uniqueId))) return false
        return true
    }

    private fun protected(item: ItemStack?): Boolean = item == null || item.type.isAir ||
        AbilityManager.activeItems.any { it.isSimilar(item) } || EntityThrower.isRestrictHand(item)

    private fun stealPlayer(player: Player, victim: Player, hook: UUID) {
        val id = UUID.nameUUIDFromBytes("pick-pocket:$hook:${victim.uniqueId}:${player.uniqueId}".toByteArray(Charsets.UTF_8))
        if (FileManager.removals.find(victim.uniqueId, id) != null) return
        if (!FileManager.delivery.claim(player) || !FileManager.delivery.claim(victim)) return
        if (player.ability != this || !eligible(player, victim)) return
        val before = PaperItemCodec.snapshot(victim, includeOffHand = true, includeArmor = true)
        val candidates = before.indices.filter { slot ->
            val stack = before[slot] ?: return@filter false
            slot != victim.ability?.restrictedSlot && !protected(PaperItemCodec.item(stack.payload, stack.count))
        }
        val slot = candidates.randomOrNull() ?: return
        val terms = PickPocketTerms(player.uniqueId, slot, requireNotNull(before[slot]))
        val after = before.toMutableList().apply { this[slot] = null }
        if (!FileManager.delivery.remove(victim, id, PickPocketSettlement.KIND, terms.encode(), before, after)) return
        FileManager.delivery.claim(player, quiet = true)
    }

    private fun stealEntity(player: Player, victim: LivingEntity, position: org.bukkit.Location) {
        val inventory: Inventory? = when (victim) {
            is AbstractHorse -> victim.inventory
            is Villager -> victim.inventory
            else -> null
        }
        if (inventory != null) {
            val slot = inventory.contents.indices.filter { !protected(inventory.getItem(it)) }.randomOrNull() ?: return
            val original = requireNotNull(inventory.getItem(slot)).clone()
            transferWorldItem(player, position, original, { inventory.setItem(slot, null) }, { inventory.setItem(slot, original) })
        } else {
            val equipment = victim.equipment ?: return
            val slots = listOf(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
            val slot = slots.filter { !protected(equipment.getItem(it)) }.randomOrNull() ?: return
            val original = equipment.getItem(slot).clone()
            transferWorldItem(player, position, original, { equipment.setItem(slot, null) }, { equipment.setItem(slot, original) })
        }
    }

    /** NPC inventory and its dropped item both remain world state; never mint a DB delivery from unsaved world state. */
    private fun transferWorldItem(player: Player, position: org.bukkit.Location, original: ItemStack, remove: () -> Unit, restore: () -> Unit) {
        var dropped: Item? = null
        remove()
        try {
            dropped = position.world.dropItemNaturally(position, original.clone())
            check(dropped.isValid) { "Item spawn was rejected" }
            dropped.velocity = player.eyeLocation.toVector().subtract(position.toVector()).multiply(0.125)
        } catch (failure: Exception) {
            dropped?.remove()
            restore()
            throw failure
        }
    }

    override fun stop() { processed.clear() }
}
