package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.platform.normalizeSafely
import com.github.lupi13.limpi.quests.DarkTime
import com.github.lupi13.limpi.quests.LightTime
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.UUID
import java.util.logging.Level

/**
 * Shared offhand time rewind (source: GotchaAbility TimeLeaper/TimeLeaperPlus/Chronobreak).
 *
 * While the player holds the ability, a scoped recorder keeps the last `rewind_ticks` locations and health values.
 * The recorder (and its buffer) ends on death, quit, world change, ability change, delivery block or plugin disable,
 * and restarts on the next tick once the player is eligible again. Rewinding pulls the player backwards along the recorded
 * path by velocity for `rewind_visual_ticks` ticks, then teleports to the oldest frame and restores its health; damage
 * is cancelled for the whole rewind.
 * Only location and health are rewound; inventory and items are never touched.
 */
abstract class TimeLeapAbility(
    grade: Grade,
    displayName: Component,
    codeName: String,
    material: Material,
    description: List<Component>,
    acquisition: AcquisitionType = AcquisitionType.GACHA,
    relatedQuest: List<Quest>? = null,
) : Ability(
    grade = grade, element = Element.MAGIC, displayName = displayName, codeName = codeName, material = material,
    description = description, acquisition = acquisition, relatedQuest = relatedQuest, needFile = true, restrictedSlot = 40
) {
    protected class Frame(val location: Location, val health: Double)
    private class Recorder(val effect: AbilityEffect) { val frames = ArrayDeque<Frame>() }

    private val recorders = mutableMapOf<UUID, Recorder>()
    private val rewinding = mutableMapOf<UUID, AbilityEffect>()
    private val trail by lazy { Particle.DustOptions(Color.fromRGB(150, 230, 255), 0.6f) }

    protected abstract val activeName: Component

    override val activeItem: ItemStack by lazy {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.itemName(activeName)
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 누르면 ${seconds(config!!.getInt("rewind_ticks"))}초 전의 위치와 체력으로 돌아갑니다.", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false),
            Component.text("(쿨타임: ${seconds(config!!.getInt("cooldown_ticks"))}초)", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        item
    }

    protected fun seconds(ticks: Int): String {
        val value = ticks / 20.0
        return if (value == Math.floor(value)) value.toLong().toString() else value.toString()
    }

    protected fun rewindLines(): List<Component> = listOf(
        Component.text("")
            .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
            .append(Component.text("키를 누르면 ", NamedTextColor.WHITE))
            .append(Component.text(seconds(config!!.getInt("rewind_ticks")), NamedTextColor.GREEN))
            .append(Component.text("초 전의 위치와 체력으로 돌아갑니다.", NamedTextColor.WHITE)),
        Component.text("되돌아가는 동안 피해를 받지 않습니다.", NamedTextColor.WHITE),
        Component.text("쿨타임이 끝나면 되돌아갈 경로가 자신에게만 보입니다.", NamedTextColor.WHITE)
    )

    protected fun cooldownLine(): Component = Component.text("(쿨타임: ", NamedTextColor.WHITE)
        .append(Component.text(seconds(config!!.getInt("cooldown_ticks")), NamedTextColor.GREEN))
        .append(Component.text("초)", NamedTextColor.WHITE))

    /** Called once the rewind arrived and health was restored; [before]/[after] are the health around the restore. */
    protected open fun afterRewind(player: Player, before: Double, after: Double) {}

    override fun start() {
        AbilityRuntime.global(1) {
            for (player in plugin.server.onlinePlayers) {
                val id = player.uniqueId
                if (player.ability !== this || id in recorders || id in rewinding) continue
                if (!player.isOnline || player.isDead || FileManager.delivery.blocked(id)) continue
                try { record(player) } catch (failure: Exception) {
                    plugin.logger.log(Level.WARNING, "Unable to start $codeName recording for $id", failure)
                }
            }
        }
    }

    private fun record(player: Player) {
        val id = player.uniqueId
        val effect = AbilityRuntime.begin(player, codeName)
        val recorder = Recorder(effect)
        recorders[id] = recorder
        effect.own {
            if (recorders[id] === recorder) recorders.remove(id)
            recorder.frames.clear()
        }
        effect.repeat { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            val frames = recorder.frames
            frames.addLast(Frame(owner.location, owner.health))
            val limit = config!!.getInt("rewind_ticks")
            while (frames.size > limit) frames.removeFirst()
            if (owner.getCooldown(material) <= 0) showTrail(owner, frames)
        }
    }

    private fun showTrail(owner: Player, frames: ArrayDeque<Frame>) {
        frames.forEachIndexed { index, frame ->
            val location = frame.location.clone().add(0.0, 0.9, 0.0)
            if (index == 0) owner.spawnParticle(Particle.BUBBLE_POP, location, 3, 0.15, 0.3, 0.15, 0.1)
            else if (index % 2 == 0) owner.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, trail)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val id = player.uniqueId
        if (player.ability !== this || FileManager.delivery.blocked(id)) return
        event.isCancelled = true // Reserved offhand is a custom input; its vanilla swap is intentionally cancelled.
        if (id in rewinding || player.isDead) return
        if (player.getCooldown(material) > 0) { AbilityManager.showCooldown(player, material); return }
        val recorder = recorders[id]
        val world = player.world.uid
        val frames = recorder?.frames?.filter { it.location.world?.uid == world }.orEmpty()
        if (frames.isEmpty()) {
            player.sendActionBar(Component.text("이동 기록이 없습니다!", NamedTextColor.RED))
            return
        }
        recorder?.effect?.close() // Recording pauses during the rewind and restarts afterwards with a fresh buffer.
        try { rewind(player, frames) } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to start $codeName rewind for $id", failure)
        }
    }

    /**
     * Fast rewind: for `rewind_visual_ticks` ticks the player is pulled backwards along the recorded path by velocity
     * (gravity removed, fall distance cleared, speed capped by `rewind_max_speed`) while the reversed trail is drawn,
     * then the player is teleported exactly to the oldest frame (position, yaw and pitch) and health is restored.
     */
    private fun rewind(player: Player, frames: List<Frame>) {
        val id = player.uniqueId
        val effect = AbilityRuntime.begin(player, codeName)
        rewinding[id] = effect
        effect.own { if (rewinding[id] === effect) rewinding.remove(id) }
        val visualTicks = config!!.getInt("rewind_visual_ticks")
        val maxSpeed = config!!.getDouble("rewind_max_speed")
        if (visualTicks > 0) effect.attribute(Attribute.GRAVITY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 2f)
        var tick = 0
        var drawn = frames.lastIndex + 1
        effect.repeat { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            tick++
            owner.fallDistance = 0f
            if (tick <= visualTicks) {
                // Frame reached at the end of this tick, evenly spread so the last velocity tick ends near the oldest frame.
                val target = (frames.lastIndex.toLong() * (visualTicks - tick) / (visualTicks + 1)).toInt()
                drawTrail(owner, frames, target, drawn)
                drawn = target
                val goal = frames[target].location
                val pull = if (goal.world?.uid == owner.world.uid) goal.toVector().subtract(owner.location.toVector()) else Vector()
                val length = pull.length()
                owner.velocity = if (!length.isFinite() || length <= 0.0) Vector()
                    else if (length > maxSpeed) pull.normalizeSafely().multiply(maxSpeed) else pull
                owner.playSound(owner.location, Sound.ENTITY_PLAYER_SWIM, 0.2f, 2f)
                return@repeat
            }
            drawTrail(owner, frames, 0, drawn)
            owner.velocity = Vector()
            if (!moveTo(owner, frames.first().location)) {
                owner.sendActionBar(Component.text("시간 역행이 중단되었습니다.", NamedTextColor.RED))
                return@repeat scope.close()
            }
            owner.velocity = Vector()
            finish(owner, frames.first())
            scope.close()
        }
    }

    /** Draws the reversed path segment covered this tick (frames from [to] up to, excluding, [from]). */
    private fun drawTrail(owner: Player, frames: List<Frame>, to: Int, from: Int) {
        val world = owner.world
        for (index in (from - 1).coerceAtMost(frames.lastIndex) downTo to.coerceAtLeast(0)) {
            val location = frames[index].location
            if (location.world?.uid != world.uid) continue
            world.spawnParticle(Particle.DUST, location.clone().add(0.0, 0.9, 0.0), 2, 0.1, 0.2, 0.1, 0.0, trail)
        }
    }

    /** Teleport only inside the current world, to a finite, loaded, in-border and in-height destination. */
    private fun moveTo(player: Player, destination: Location): Boolean {
        val world = destination.world ?: return false
        if (world.uid != player.world.uid) return false
        if (!destination.x.isFinite() || !destination.y.isFinite() || !destination.z.isFinite()) return false
        if (destination.y < world.minHeight - 64 || destination.y > world.maxHeight + 64) return false
        if (!world.worldBorder.isInside(destination)) return false
        if (!world.isChunkLoaded(destination.blockX shr 4, destination.blockZ shr 4)) return false
        return player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

    private fun finish(player: Player, origin: Frame) {
        player.setCooldown(material, config!!.getInt("cooldown_ticks"))
        player.fallDistance = 0f
        val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val before = player.health
        val past = origin.health.coerceIn(0.0, max)
        // Healing is scaled by heal_multiplier; health that the rewind takes away is only restored, never scaled.
        val restored = if (past > before) (before + (past - before) * config!!.getDouble("heal_multiplier")).coerceIn(0.0, max) else past
        if (restored > 0.0 && restored.isFinite()) player.health = restored
        player.playSound(player.location, Sound.ENTITY_DOLPHIN_JUMP, 1f, 2f)
        afterRewind(player, before, player.health)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val effect = rewinding[player.uniqueId] ?: return
        if (effect.scope.closed) return
        if (event.cause == EntityDamageEvent.DamageCause.KILL || event.cause == EntityDamageEvent.DamageCause.VOID) return
        event.isCancelled = true
    }

    override fun release(owner: UUID) {
        super.release(owner)
        rewinding.remove(owner)?.close()
        recorders.remove(owner)?.effect?.close()
    }

    override fun stop() {
        super.stop()
        rewinding.values.toList().forEach { it.close() }
        recorders.values.toList().forEach { it.effect.close() }
        rewinding.clear()
        recorders.clear()
    }
}

object TimeLeaper : TimeLeapAbility(
    grade = Grade.EPIC,
    displayName = Component.text("시간 역행", NamedTextColor.AQUA),
    codeName = "time_leaper",
    material = Material.CLOCK,
    description = listOf(
        Component.text("왼손이 시간여행장치로 고정됩니다.", NamedTextColor.WHITE),
        Component.text("3초 전의 위치와 체력으로 돌아갑니다.", NamedTextColor.WHITE)
    )
) {
    /** Quest health unit: tenths of a health point (1 heart = 2 HP = 20 units). */
    private const val UNITS_PER_HEALTH = 10.0
    private const val HEALED_TARGET = 200L * 10L
    private const val LOST_TARGET = 50L * 10L

    override val activeName: Component = Component.text("시간여행장치", NamedTextColor.AQUA)

    override val relatedQuest: List<Quest> by lazy { listOf(LightTime, DarkTime) }

    override val details: List<Component> by lazy {
        rewindLines() + listOf(
            cooldownLine()
        )
    }

    override fun afterRewind(player: Player, before: Double, after: Double) {
        val healed = Math.round((after - before) * UNITS_PER_HEALTH)
        val lost = Math.round((before - after) * UNITS_PER_HEALTH)
        try {
            if (healed > 0) QuestManager.progress(player, LightTime, "time_leaper_healed", healed, HEALED_TARGET)
            if (lost > 0) QuestManager.progress(player, DarkTime, "time_leaper_lost", lost, LOST_TARGET)
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to record $codeName quest progress for ${player.uniqueId}", failure)
        }
    }
}
