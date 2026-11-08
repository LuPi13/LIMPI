package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.platform.RecallScrolls
import com.github.lupi13.limpi.recall.RecallPolicy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import kotlin.math.floor

/**
 * 귀환서: 우클릭 후 [RecallPolicy.CAST_TICKS] 동안 가만히 있으면 리스폰 지점으로 이동하고 한 장을 쓴다.
 * 움직이거나 피해를 입으면 취소되고 귀환서는 그대로 남는다.
 */
class RecallScrollEvents(private val plugin: Plugin) : Listener {
    private inner class Cast(val player: Player) : BukkitRunnable() {
        private val origin = player.location
        private var elapsed = 0

        override fun run() {
            if (!player.isOnline || player.isDead) { stop(player); return }
            val now = player.location
            if (now.world != origin.world || RecallPolicy.moved(origin.x, origin.y, origin.z, now.x, now.y, now.z)) { interrupt(player); return }
            elapsed++
            player.world.spawnParticle(Particle.PORTAL, now.add(0.0, 1.0, 0.0), 6, 0.3, 0.6, 0.3, 0.4)
            if (elapsed >= RecallPolicy.CAST_TICKS) { stop(player); finish(player); return }
            if (elapsed % 20 == 0) countdown(player, RecallPolicy.secondsLeft(elapsed))
        }
    }

    private val casts = HashMap<UUID, Cast>()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun use(event: PlayerInteractEvent) {
        if (event.action !in setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)) return
        if (!RecallScrolls.identify(event.item)) return
        if (event.useItemInHand() == Event.Result.DENY) return
        // 베틀 등 블록을 열지 않는다.
        event.setUseItemInHand(Event.Result.DENY); event.setUseInteractedBlock(Event.Result.DENY)
        val player = event.player
        if (player.uniqueId in casts) return
        val cast = Cast(player)
        casts[player.uniqueId] = cast
        cast.runTaskTimer(plugin, 1L, 1L)
        player.sendMessage(Component.text("귀환을 시작합니다. 움직이거나 피해를 입으면 취소됩니다.", NamedTextColor.AQUA))
        player.playSound(player.location, Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.6f)
        countdown(player, RecallPolicy.secondsLeft(0))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun damage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (player.uniqueId in casts) interrupt(player)
    }

    @EventHandler
    fun quit(event: PlayerQuitEvent) = stop(event.player)

    private fun countdown(player: Player, seconds: Int) =
        player.sendActionBar(Component.text("귀환까지 ", NamedTextColor.AQUA).append(Component.text("${seconds}초", NamedTextColor.GREEN)))

    private fun stop(player: Player): Boolean {
        val cast = casts.remove(player.uniqueId) ?: return false
        if (!cast.isCancelled) cast.cancel()
        return true
    }

    private fun interrupt(player: Player) {
        if (!stop(player)) return
        player.stopSound(Sound.BLOCK_PORTAL_TRIGGER)
        player.sendActionBar(Component.empty())
        player.sendMessage(Component.text("귀환이 취소되었습니다.", NamedTextColor.RED))
        player.playSound(player.location, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.2f)
    }

    /** 바닐라 리스폰과 같은 곳: 유효한 침대·리스폰 정박기(충전은 쓰지 않음), 없으면 기본 월드의 스폰. */
    private fun destination(player: Player): Pair<Location, Boolean> {
        player.respawnLocation?.let { return it to true }
        val spawn = Bukkit.getWorlds().first().spawnLocation
        val centered = if (spawn.x == floor(spawn.x) && spawn.z == floor(spawn.z)) spawn.clone().add(0.5, 0.0, 0.5) else spawn
        return centered to false
    }

    private fun scrollSlot(player: Player): Int? {
        val inventory = player.inventory
        return RecallPolicy.slotOrder(inventory.heldItemSlot, inventory.size).firstOrNull { RecallScrolls.identify(inventory.getItem(it)) }
    }

    private fun finish(player: Player) {
        player.stopSound(Sound.BLOCK_PORTAL_TRIGGER)
        if (scrollSlot(player) == null) {
            player.sendMessage(Component.text("귀환서가 없어 귀환하지 못했습니다.", NamedTextColor.RED)); return
        }
        val (target, personal) = destination(player)
        val from = player.location
        if (player.isInsideVehicle) player.leaveVehicle()
        if (!player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            player.sendMessage(Component.text("귀환하지 못했습니다. 귀환서는 소모되지 않았습니다.", NamedTextColor.RED)); return
        }
        // 이동과 같은 틱이라 칸이 바뀌지 않지만, 다른 플러그인이 이동 중 인벤토리를 건드렸을 수 있어 다시 찾는다.
        val slot = scrollSlot(player)
        if (slot == null) plugin.logger.warning("Recall scroll of ${player.uniqueId} vanished during teleport; nothing consumed")
        else player.inventory.getItem(slot)?.let { stack ->
            player.inventory.setItem(slot, if (stack.amount > 1) stack.clone().apply { amount -= 1 } else null)
        }
        from.world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
        player.world.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
        player.sendMessage(if (personal) Component.text("귀환했습니다.", NamedTextColor.AQUA)
            else Component.text("침대나 리스폰 정박기가 없어 월드 스폰으로 귀환했습니다.", NamedTextColor.YELLOW))
    }
}
