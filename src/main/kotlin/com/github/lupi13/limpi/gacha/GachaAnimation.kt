package com.github.lupi13.limpi.gacha

import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.Grade
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.bukkit.Color
import org.bukkit.Location
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

/**
 * Only expresses a committed draw. Ending or failing an animation cannot revoke its result.
 * Several draws of one player may play at the same time; each owns its own task and displays.
 */
class GachaAnimation(private val plugin: Plugin) {
    private class Session(val owner: UUID) {
        val entities = mutableListOf<Entity>()
        var task: BukkitTask? = null
    }
    private val sessions = mutableMapOf<UUID, Session>()

    fun active(owner: UUID) = sessions.values.any { it.owner == owner }

    private fun closeSession(id: UUID) {
        val session = sessions.remove(id) ?: return
        session.task?.cancel()
        for (entity in session.entities) try { entity.remove() } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to remove gacha display ${entity.uniqueId}", failure)
        }
        session.entities.clear()
    }

    /** Closes every draw animation of [owner] without running its finish expression. */
    fun close(owner: UUID) = sessions.filterValues { it.owner == owner }.keys.toList().forEach(::closeSession)
    fun closeAll() = sessions.keys.toList().forEach(::closeSession)

    /**
     * Plays the original draw presentation. [onFinish] runs when the result is revealed and removed (about 241 ticks),
     * before the closing sound and particles, matching the original order of messages.
     */
    fun start(player: Player, ability: Ability, ticket: ItemStack, onFinish: () -> Unit = {}) {
        val id = UUID.randomUUID()
        val session = Session(player.uniqueId)
        sessions[id] = session // Own even a partially spawned display group.
        try {
            val world = player.world
            val gachaLocation = player.eyeLocation.add(player.location.direction)
            val initialYaw = player.yaw
            fun <T : Entity> spawn(location: Location, type: Class<T>): T =
                world.spawn(location, type) { entity ->
                    session.entities.add(entity)
                    entity.isPersistent = false
                }

            val gachaVisual = spawn(gachaLocation.clone().apply { yaw = initialYaw; pitch = 0f }, ItemDisplay::class.java)
            gachaVisual.transformation = transform(0.5f, -0.5f)
            gachaVisual.interpolationDelay = 0
            gachaVisual.interpolationDuration = 0

            val dust = Particle.DustOptions(Color.WHITE, 0.5f)
            val dustTransition = when (ability.grade) {
                Grade.LEGENDARY -> Particle.DustTransition(Color.WHITE, Color.ORANGE, 1f)
                Grade.EPIC -> Particle.DustTransition(Color.WHITE, Color.PURPLE, 1f)
                Grade.RARE -> Particle.DustTransition(Color.WHITE, Color.TEAL, 1f)
                Grade.COMMON -> Particle.DustTransition(Color.WHITE, Color.GREEN, 1f)
                else -> Particle.DustTransition(Color.WHITE, Color.fromRGB(1, 1, 1), 1f)
            }

            gachaVisual.setItemStack(ticket)
            gachaVisual.setRotation(initialYaw, 0f)
            world.spawnParticle(Particle.CLOUD, gachaLocation, 50, 0.3, 0.3, 0.3, 0.1)
            world.playSound(gachaLocation, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 0.5f)
            world.playSound(gachaLocation, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 1.0f, 0.5f)

            var timer = 0
            var gachaName: TextDisplay? = null
            var gachaLore: TextDisplay? = null
            // The result text stays vertical (FIXED billboard, pitch 0) and faces back toward the drawing player.
            val textRotationYaw = initialYaw + 180f
            val abilityItem = ability.getItem()
            session.task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                try {
                    when {
                        // 5초간 회전, 파티클, 점점 빨라짐
                        timer <= 100 -> {
                            if (timer == 1) {
                                gachaVisual.transformation = transform(1f)
                                gachaVisual.interpolationDelay = 0
                                gachaVisual.interpolationDuration = 100
                            }
                            gachaVisual.setRotation(gachaVisual.yaw + (timer / 2f), gachaVisual.pitch)
                            if (timer <= 60) {
                                world.spawnParticle(Particle.DUST, gachaLocation, (timer / 10).coerceAtLeast(1),
                                    0.3, 0.3, 0.3, 0.1, dust)
                            } else {
                                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, gachaLocation, ((timer - 60) / 2).coerceAtLeast(1),
                                    0.3, 0.3, 0.3, 0.1, dustTransition)
                            }
                        }
                        // 감속, 아이템 표시
                        timer <= 140 -> {
                            gachaVisual.setItemStack(abilityItem)
                            gachaVisual.setRotation(gachaVisual.yaw + ((140 - timer) / 1.09f), gachaVisual.pitch)
                        }
                        // 뽑기 완료, 능력 표시
                        timer == 141 -> {
                            gachaVisual.setRotation(initialYaw, 0f)
                            gachaVisual.transformation = transform(0.7f)
                            gachaVisual.interpolationDelay = 0
                            gachaVisual.interpolationDuration = 10

                            gachaName = spawn(gachaLocation.clone().apply { yaw = textRotationYaw; pitch = 0f }, TextDisplay::class.java).apply {
                                text(ability.displayName
                                    .append(Component.text(" "))
                                    .append(ability.grade.displayGrade))
                                // 초기상태: 크기 0
                                transformation = transform(0f)
                                interpolationDelay = 0
                                interpolationDuration = 0
                            }
                            gachaLore = spawn(gachaLocation.clone().subtract(0.0, 0.4, 0.0).apply { yaw = textRotationYaw; pitch = 0f },
                                TextDisplay::class.java).apply {
                                text(Component.join(JoinConfiguration.separator(Component.text("\n")), ability.description))
                                // 초기상태: 크기 0
                                transformation = transform(0f)
                                interpolationDelay = 0
                                interpolationDuration = 10
                            }
                        }
                        // 1틱 후 크기 증가, 이름은 위로·설명은 아래로
                        timer == 142 -> {
                            gachaName?.apply {
                                transformation = transform(0.5f, 0.4f)
                                interpolationDelay = 0
                                interpolationDuration = 10
                            }
                            gachaLore?.apply {
                                transformation = transform(0.4f, -0.25f)
                                interpolationDelay = 0
                                interpolationDuration = 10
                            }
                        }
                        // 5초 후 디스플레이 제거
                        timer >= 241 -> {
                            try { onFinish() } catch (failure: Exception) {
                                plugin.logger.log(Level.WARNING, "Gacha finish failed for ${player.uniqueId}; committed result is retained", failure)
                            }
                            closeSession(id)
                            world.playSound(gachaLocation.clone().subtract(0.0, 0.4, 0.0), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 2f)
                            world.spawnParticle(Particle.CLOUD, gachaLocation, 50, 0.3, 0.3, 0.3, 0.1)
                            return@Runnable
                        }
                    }
                    timer++
                } catch (failure: Exception) {
                    closeSession(id)
                    plugin.logger.log(Level.WARNING, "Gacha animation failed for ${player.uniqueId}; committed result is retained", failure)
                }
            }, 0L, 1L)
        } catch (failure: Exception) {
            closeSession(id)
            plugin.logger.log(Level.WARNING, "Unable to start gacha animation for ${player.uniqueId}; committed result is retained", failure)
        }
    }

    private fun transform(scale: Float, y: Float = 0f) =
        Transformation(Vector3f(0f, y, 0f), AxisAngle4f(0f, 0f, 0f, 0f), Vector3f(scale, scale, scale), AxisAngle4f(0f, 0f, 0f, 0f))
}
