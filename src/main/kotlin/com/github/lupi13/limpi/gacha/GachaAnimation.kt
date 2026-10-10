package com.github.lupi13.limpi.gacha

import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.Grade
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.logging.Level

/** Only expresses a committed draw. Ending or failing an animation cannot revoke its result. */
class GachaAnimation(private val plugin: Plugin) {
    private class Session {
        val entities = mutableListOf<Entity>()
        var task: BukkitTask? = null
    }
    private val sessions = mutableMapOf<UUID, Session>()
    fun active(owner: UUID) = owner in sessions
    fun close(owner: UUID) {
        val session = sessions.remove(owner) ?: return
        session.task?.cancel()
        for (entity in session.entities) try { entity.remove() } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to remove gacha display ${entity.uniqueId}", failure)
        }
        session.entities.clear()
    }
    fun closeAll() = sessions.keys.toList().forEach(::close)

    fun start(player: Player, ability: Ability, ticket: ItemStack) {
        close(player.uniqueId)
        val session = Session()
        sessions[player.uniqueId] = session // Own even a partially spawned display group.
        try {
            val world = player.world
            val location = player.eyeLocation.add(player.location.direction)
            val yaw = player.yaw
            fun <T : Entity> spawn(type: Class<T>, offset: Double = 0.0): T =
                world.spawn(location.clone().add(0.0, offset, 0.0).apply { this.yaw = yaw + 180f }, type) { entity ->
                    session.entities.add(entity)
                    entity.isPersistent = false
                }
            val visual = spawn(ItemDisplay::class.java)
            visual.setItemStack(ticket)
            visual.setRotation(yaw, 0f)
            visual.transformation = transform(0.5f, -0.5f)
            visual.interpolationDuration = 0
            world.spawnParticle(Particle.CLOUD, location, 50, 0.3, 0.3, 0.3, 0.1)
            world.playSound(location, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1f, 0.5f)
            world.playSound(location, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 1f, 0.5f)
            val color = when (ability.grade) {
                Grade.LEGENDARY -> Color.ORANGE
                Grade.EPIC -> Color.PURPLE
                Grade.RARE -> Color.TEAL
                Grade.COMMON -> Color.GREEN
                else -> Color.fromRGB(1, 1, 1)
            }
            var tick = 0
            var name: TextDisplay? = null
            var lore: TextDisplay? = null
            val abilityItem = ability.getItem()
            session.task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                try {
                    if (!player.isOnline || player.isDead || player.world.uid != world.uid || !visual.isValid) {
                        close(player.uniqueId)
                        return@Runnable
                    }
                    when {
                        tick <= 100 -> {
                            if (tick == 1) {
                                visual.transformation = transform(1f)
                                visual.interpolationDelay = 0
                                visual.interpolationDuration = 100
                            }
                            visual.setRotation(visual.yaw + tick / 2f, 0f)
                            if (tick <= 60) world.spawnParticle(Particle.DUST, location, (tick / 10).coerceAtLeast(1),
                                0.3, 0.3, 0.3, 0.1, Particle.DustOptions(Color.WHITE, 0.5f))
                            else world.spawnParticle(Particle.DUST_COLOR_TRANSITION, location, ((tick - 60) / 2).coerceAtLeast(1),
                                0.3, 0.3, 0.3, 0.1, Particle.DustTransition(Color.WHITE, color, 1f))
                        }
                        tick <= 140 -> {
                            if (tick == 101) visual.setItemStack(abilityItem)
                            visual.setRotation(visual.yaw + (140 - tick) / 1.09f, 0f)
                        }
                        tick == 141 -> {
                            visual.setRotation(yaw, 0f)
                            visual.transformation = transform(0.7f)
                            visual.interpolationDuration = 10
                            name = spawn(TextDisplay::class.java).apply {
                                text(ability.displayName.append(Component.text(" ")).append(ability.grade.displayGrade)
                                    .append(Component.text(" ")).append(ability.element.displayElement))
                                transformation = transform(0f); interpolationDuration = 0
                            }
                            lore = spawn(TextDisplay::class.java, -0.4).apply {
                                text(Component.join(JoinConfiguration.separator(Component.text("\n")), ability.description))
                                transformation = transform(0f); interpolationDuration = 0
                            }
                        }
                        tick == 142 -> {
                            name?.apply { interpolationDelay = 0; interpolationDuration = 10; transformation = transform(0.5f, 0.4f) }
                            lore?.apply { interpolationDelay = 0; interpolationDuration = 10; transformation = transform(0.4f, -0.25f) }
                        }
                        tick >= 241 -> {
                            world.playSound(location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 2f)
                            world.spawnParticle(Particle.CLOUD, location.clone().add(0.0, 0.4, 0.0), 50, 0.3, 0.3, 0.3, 0.1)
                            close(player.uniqueId)
                        }
                    }
                    tick++
                } catch (failure: Exception) {
                    close(player.uniqueId)
                    plugin.logger.log(Level.WARNING, "Gacha animation failed for ${player.uniqueId}; committed result is retained", failure)
                }
            }, 0L, 1L)
        } catch (failure: Exception) {
            close(player.uniqueId)
            plugin.logger.log(Level.WARNING, "Unable to start gacha animation for ${player.uniqueId}; committed result is retained", failure)
        }
    }
    private fun transform(scale: Float, y: Float = 0f) =
        Transformation(Vector3f(0f, y, 0f), AxisAngle4f(), Vector3f(scale, scale, scale), AxisAngle4f())
}
