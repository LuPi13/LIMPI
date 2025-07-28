package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Damageable
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object CometStrike : Ability(
    grade = Grade.EPIC,
    element = Element.EXPLOSIVE,
    displayName = Component.text("혜성강타", NamedTextColor.AQUA),
    codeName = "comet_strike",
    material = Material.BLAZE_POWDER,
    description = listOf(
        Component.text("지상에서 빠르게 두 번 웅크려 도약 후,", NamedTextColor.WHITE),
        Component.text("지면으로 빠르게 돌진하여 피해를 줍니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("지상에서 빠르게 두 번 웅크려 공중으로 도약합니다.", NamedTextColor.WHITE),
            Component.text("잠시 후, 바라보는 지면 방향으로 빠르게 돌진합니다.", NamedTextColor.WHITE),
            Component.text("지면에 부딪히면 주변 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("range")}", NamedTextColor.GREEN))
                .append(Component.text("블록 내의 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 줍니다.", NamedTextColor.WHITE)),
            Component.text("쿨타임: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown_milliseconds") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("cooldown_milliseconds", 10000)
            config?.set("jump_forward_amplifier", 0.02)
            config?.set("jump_amplifier", 0.1)
            config?.set("striking_speed", 5.0)
            config?.set("max_striking_angle", 45.0)
            config?.set("max_strike_ticks", 100)
            config?.set("range", 4.0)
            config?.set("damage", 10.0)
        }
        saveConfig()
    }



    val lastSneakTime: MutableMap<Player, Long> = mutableMapOf()
    val lastStrikeTime: MutableMap<Player, Long> = mutableMapOf()

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val cooldown = config!!.getInt("cooldown_milliseconds")
        if (player.ability != this && player.ability != Titan) return
        if (!(player as Entity).isOnGround || !event.isSneaking) return

        if ((lastSneakTime[player] != null) && (System.currentTimeMillis() - lastSneakTime[player]!! <= 300)) { // 300ms 이내에 두 번 웅크리면
            if ((lastStrikeTime[player] == null) || (System.currentTimeMillis() - lastStrikeTime[player]!! > cooldown)) { // 쿨타임이 지나면
                player.getAttribute(Attribute.MOVEMENT_SPEED)!!.baseValue = 0.0
                player.getAttribute(Attribute.GRAVITY)!!.baseValue = 0.0
                player.getAttribute(Attribute.SAFE_FALL_DISTANCE)!!.baseValue = Double.MAX_VALUE


                object: BukkitRunnable() {
                    var ticks = 0
                    var strikingDirection: Vector? = null
                    val range = config!!.getDouble("range")
                    val damage = config!!.getDouble("damage")
                    override fun run() {

                        var tempStrikingDirection = player.location.direction
                        val angle = player.pitch.toDouble()
                        if (angle < config!!.getDouble("max_striking_angle")) {
                            tempStrikingDirection = Vector(
                                -sin(Math.toRadians(player.yaw.toDouble())) * cos(Math.toRadians(config!!.getDouble("max_striking_angle"))),
                                -sin(Math.toRadians(config!!.getDouble("max_striking_angle"))),
                                cos(Math.toRadians(player.yaw.toDouble())) * cos(Math.toRadians(config!!.getDouble("max_striking_angle")))
                            )
                        }


                        // 피해 범위 시각화
                        val tempTrace = player.world.rayTrace(
                            player.eyeLocation,
                            tempStrikingDirection,
                            256.0,
                            FluidCollisionMode.NEVER,
                            true, 0.0
                        ) { entity -> entity != player }

                        if (tempTrace != null) {
                            val center = tempTrace.hitPosition.toLocation(player.world)
                            for (theta in 0 until 360 step 10) {
                                val angleRad = Math.toRadians(theta.toDouble())
                                val x = center.x + range * cos(angleRad)
                                val z = center.z + range * sin(angleRad)
                                player.world.spawnParticle(
                                    Particle.DUST,
                                    x, center.y, z,
                                    3,
                                    0.1, 0.1, 0.1, 0.1,
                                    Particle.DustOptions(Color.fromRGB(255, (255 * (20 - ticks) / 20).coerceIn(0, 255), (255 * (20 - ticks) / 20).coerceIn(0, 255)), 1.0f)
                                    , true
                                )
                            }

                            for (i in 1 until (ticks * range).toInt()) {
                                val randomLocation = center.clone().add(
                                    (Random.nextDouble() - 0.5) * range,
                                    0.0,
                                    (Random.nextDouble() - 0.5) * range
                                )
                                player.world.spawnParticle(
                                    Particle.DUST,
                                    randomLocation,
                                    1,
                                    0.1, 0.1, 0.1, 0.1,
                                    Particle.DustOptions(Color.fromRGB(255, (255 * (40 - ticks) / 40).coerceIn(0, 127), (255 * (40 - ticks) / 40).coerceIn(0,127)), 0.5f)
                                    , true
                                )
                            }
                        }

                        player.world.spawnParticle(
                            Particle.END_ROD,
                            player.location,
                            3,
                            0.1, 0.1, 0.1, 0.1
                        )



                        if (ticks < 10) {
                            player.world.playSound(
                                player.location,
                                Sound.BLOCK_TRIAL_SPAWNER_SPAWN_ITEM,
                                1.0f, (1.0f + (ticks / 10.0f))
                            )
                        }
                        else {
                            player.isGliding = true
                        }



                        if (ticks < 20) {
                            val lookingVector = Vector(
                                -sin(Math.toRadians(player.yaw.toDouble())),
                                0.0,
                                cos(Math.toRadians(player.yaw.toDouble()))
                            ).normalize().multiply((20 - ticks) * config!!.getDouble("jump_forward_amplifier"))

                            player.velocity = lookingVector.setY((20 - ticks) * config!!.getDouble("jump_amplifier"))
                            player.world.playSound(
                                player.location,
                                Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM,
                                1.0f, 2.0f
                            )
                        }

                        if (ticks == 20) {
                            strikingDirection = tempStrikingDirection
                        }

                        if (ticks > 20) {
                            val speed = config!!.getDouble("striking_speed")

                            player.velocity = strikingDirection!!.clone().multiply((ticks - 20.0).coerceAtMost(speed))


                            // 피격 계산: rayTrace or 블록 충돌
                            val rayTraceResult = player.world.rayTrace(
                                player.location,
                                strikingDirection!!,
                                2.0,
                                FluidCollisionMode.NEVER,
                                true,
                                0.0
                            ) { entity -> entity != player }

                            if (rayTraceResult != null ||
                                !player.location.block.getRelative(0, -1, 0).isPassable) {

                                var nearbyEntities = player.getNearbyEntities(range*2, range*2, range*2)
                                    .filter { entity -> entity != player && entity is Damageable }
                                if (player.ability == this@CometStrike) {
                                    nearbyEntities = nearbyEntities.filter { entity -> AbilityManager().isEnemy(player, entity as Damageable) }
                                }
                                for (entity in nearbyEntities) {
                                    val directionVector = entity.location.clone().subtract(player.location).toVector()
                                    if (directionVector.length() <= range) {
                                        (entity as Damageable).damage(damage, player)
                                        entity.velocity = directionVector.normalize().setY((if (directionVector.y > 0) { directionVector.y } else { 0.0 }) + 0.2)
                                    }
                                }
                                player.isGliding = false

                                if (player.ability == this@CometStrike) {
                                    player.velocity = Vector(0.0, 0.0, 0.0)
                                    player.fallDistance = 0.0f
                                }
                                else {
                                    player.damage(18.0)
                                }

                                player.world.spawnParticle(
                                    Particle.EXPLOSION,
                                    player.location,
                                    30,
                                    range/2.0, range/2.0, range/2.0, 1.0
                                )
                                player.world.spawnParticle(
                                    Particle.FLAME,
                                    player.location,
                                    300,
                                    0.2, 0.2, 0.2, 1.0
                                )
                                player.world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
                                ticks = config!!.getInt("max_strike_ticks")
                            }
                        }

                        if (player.isDead) {
                            ticks = config!!.getInt("max_strike_ticks")
                        }

                        if (ticks >= config!!.getInt("max_strike_ticks")) {
                            player.getAttribute(Attribute.MOVEMENT_SPEED)!!.baseValue = 0.1
                            player.getAttribute(Attribute.GRAVITY)!!.baseValue = 0.08
                            player.getAttribute(Attribute.SAFE_FALL_DISTANCE)!!.baseValue = 3.0
                            cancel()
                        }
                        ticks++
                    }
                }.runTaskTimer(plugin, 0L, 1L)


                lastStrikeTime[player] = System.currentTimeMillis()
            }
            else {
                AbilityManager().showCooldown(player, cooldown, lastStrikeTime[player]!!)
            }
        }
        lastSneakTime[player] = System.currentTimeMillis()
    }
}