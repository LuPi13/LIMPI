package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.ability.EffectRegistry
import com.github.lupi13.limpi.ability.EffectScope
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

object AbilityRuntime : Listener {
    private lateinit var plugin: Plugin
    private lateinit var registry: EffectRegistry
    private val globalTasks = mutableListOf<org.bukkit.scheduler.BukkitTask>()
    fun initialize(owner: Plugin) {
        plugin = owner
        globalTasks.clear()
        registry = EffectRegistry { failure -> plugin.logger.log(Level.WARNING, "Unable to clean an ability resource", failure) }
    }
    fun begin(player: Player, ability: String, expected: String = FileManager.players.abilityCode(player.uniqueId).orEmpty(), allowDead: Boolean = false): AbilityEffect {
        check(Bukkit.isPrimaryThread())
        require(ability.isNotBlank() && player.isOnline && (allowDead || !player.isDead) && !FileManager.delivery.blocked(player.uniqueId))
        return AbilityEffect(registry.begin(player.uniqueId), player.uniqueId, ability, expected, player.world.uid, plugin, allowDead)
    }
    fun closeOwner(owner: UUID, except: UUID? = null) { if (::registry.isInitialized) registry.closeOwner(owner, except) }
    fun closeAll() {
        globalTasks.forEach { it.cancel() }; globalTasks.clear()
        if (::registry.isInitialized) registry.closeAll()
        AbilityPotions.clear()
    }
    fun global(period: Long, action: () -> Unit) {
        require(period > 0)
        lateinit var task: org.bukkit.scheduler.BukkitTask
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            try { action() } catch (failure: Exception) {
                task.cancel(); globalTasks.remove(task)
                plugin.logger.log(Level.SEVERE, "A global ability task failed and was stopped", failure)
            }
        }, 0L, period)
        globalTasks.add(task)
    }
    fun clearAttributes(player: Player) {
        for (attribute in Registry.ATTRIBUTE) {
            val instance = player.getAttribute(attribute) ?: continue
            instance.modifiers.filter { it.key.namespace == "limpi" && it.key.key.startsWith("ability/") }.forEach(instance::removeModifier)
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    fun quit(event: PlayerQuitEvent) { closeOwner(event.player.uniqueId); clearAttributes(event.player) }
    @EventHandler(priority = EventPriority.LOWEST)
    fun death(event: PlayerDeathEvent) { closeOwner(event.entity.uniqueId); clearAttributes(event.entity) }
    @EventHandler(priority = EventPriority.LOWEST)
    fun worldChange(event: PlayerChangedWorldEvent) { closeOwner(event.player.uniqueId); clearAttributes(event.player) }
}

class AbilityEffect internal constructor(val scope: EffectScope, val owner: UUID, private val ability: String,
    private val expected: String, private val world: UUID, private val plugin: Plugin, private val allowDead: Boolean = false) : AutoCloseable {
    fun player(): Player? = plugin.server.getPlayer(owner)
    fun live(): Boolean {
        val player = player() ?: return false
        return !scope.closed && player.isOnline && (allowDead || !player.isDead) && player.world.uid == world &&
            !FileManager.delivery.blocked(owner) && FileManager.players.abilityCode(owner).orEmpty() == expected
    }
    fun own(action: () -> Unit) = scope.own(action)
    fun potion(target: org.bukkit.entity.LivingEntity, potion: org.bukkit.potion.PotionEffect) = AbilityPotions.apply(this, target, potion)
    fun ownEntity(entity: Entity) {
        entity.isPersistent = false
        own(entity::remove)
    }
    fun attribute(attribute: Attribute, amount: Double, operation: AttributeModifier.Operation): AutoCloseable {
        require(amount.isFinite())
        val instance = requireNotNull(player()?.getAttribute(attribute))
        val key = NamespacedKey("limpi", "ability/$ability/${scope.id}/${attribute.key.key}")
        val modifier = AttributeModifier(key, amount, operation)
        instance.addTransientModifier(modifier)
        return own { instance.removeModifier(modifier) }
    }
    fun repeat(delay: Long = 0, period: Long = 1, action: (AbilityEffect) -> Unit) {
        require(delay >= 0 && period > 0)
        try {
            val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                if (!live()) { close(); return@Runnable }
                try { action(this) } catch (failure: Exception) {
                    close()
                    plugin.logger.log(Level.WARNING, "Ability effect $ability for $owner failed", failure)
                }
            }, delay, period)
            own(task::cancel)
        } catch (failure: Exception) { close(); throw failure }
    }
    override fun close() = scope.close()
}
