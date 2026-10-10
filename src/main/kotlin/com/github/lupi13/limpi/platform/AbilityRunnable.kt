package com.github.lupi13.limpi.platform

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.logging.Level

/** Adapter for existing effect bodies. Every scheduled run has a scope, lifecycle guard and cleanup. */
abstract class AbilityRunnable(player: Player, private val ability: String, allowDead: Boolean = false) : BukkitRunnable() {
    protected val effect = AbilityRuntime.begin(player, ability, allowDead = allowDead)
    private val logger = player.server.logger
    private var once = false
    abstract fun tick()
    final override fun run() {
        if (!effect.live()) { cancel(); return }
        try { tick() } catch (failure: Exception) {
            cancel()
            logger.log(Level.WARNING, "Ability task $ability for ${effect.owner} failed", failure)
        } finally { if (once) cancel() }
    }
    fun own(action: () -> Unit) = effect.own(action)
    fun setup(action: () -> Unit) {
        try { action() } catch (failure: Exception) { effect.close(); throw failure }
    }
    fun <T : Entity> spawn(location: Location, type: Class<T>, action: (T) -> Unit = {}): T = try {
        requireNotNull(location.world).spawn(location, type) { entity -> effect.ownEntity(entity); action(entity) }
    } catch (failure: Exception) { effect.close(); throw failure }
    override fun cancel() = effect.close()
    private fun schedule(action: () -> BukkitTask): BukkitTask {
        try { return action().also { task -> own(task::cancel) } } catch (failure: Exception) { cancel(); throw failure }
    }
    override fun runTask(plugin: Plugin): BukkitTask { once = true; return schedule { super.runTask(plugin) } }
    override fun runTaskLater(plugin: Plugin, delay: Long): BukkitTask { once = true; return schedule { super.runTaskLater(plugin, delay) } }
    override fun runTaskTimer(plugin: Plugin, delay: Long, period: Long): BukkitTask =
        schedule { super.runTaskTimer(plugin, delay, period) }
}
