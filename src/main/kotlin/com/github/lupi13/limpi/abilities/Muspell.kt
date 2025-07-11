package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.entity.Arrow
import org.bukkit.entity.Damageable
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.acos

object Muspell : Ability(
    grade = Grade.EPIC,
    element = Element.FIRE,
    displayName = Component.text("무스펠", NamedTextColor.DARK_RED),
    codeName = "muspell",
    material = Material.FIRE_CHARGE,
    description = listOf(
        Component.text("일반 화살을 유도력 있는", NamedTextColor.WHITE),
        Component.text("불덩이로 변환합니다.", NamedTextColor.WHITE),
    ),
    needFile = true
) {

    override val details: List<Component> by lazy {
        listOf(
            Component.text("최대로 당겨 쏜 일반 화살이 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 범위 피해를 주는 불덩이로 변환됩니다.", NamedTextColor.WHITE)),
            Component.text("불덩이는 이동 경로의 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("을 자동 추적하며, 착탄지점 주변의 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("들을 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("fire_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초동안 불태웁니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("damage", 10.0)
            config?.set("damage_range", 3.0)
            config?.set("lifetime_ticks", 100)
            config?.set("fire_ticks", 100)
            config?.set("velocity", 1.0)
            config?.set("track_search_distance", 20.0)
            config?.set("track_search_angle", 30.0)
            config?.set("tracking_coefficient", 0.5)
        }
        saveConfig()
    }


    @EventHandler
    fun onArrowLaunch(event: ProjectileLaunchEvent) {
        val player = event.entity.shooter as? Player ?: return
        if (player.ability != this) return
        if (event.entity !is Arrow) return
        val arrow = event.entity as Arrow
        if (!arrow.isCritical) return

        arrow.remove()

        val world = player.world

        // 불덩이 소환
        val fireball = world.spawn(player.eyeLocation, Marker::class.java)
        with (fireball) {
            isPersistent = true
            setGravity(false)
            isInvulnerable = true
            location.direction = player.location.direction
        }

        val damage = config!!.getDouble("damage")
        val damageRange = config!!.getDouble("damage_range")
        val lifetime = config!!.getInt("lifetimeTicks")
        val velocity = config!!.getDouble("velocity")
        val fireTicks = config!!.getInt("fire_ticks")
        val trackSearchDistance = config!!.getDouble("track_search_distance")
        val trackSearchAngle = config!!.getDouble("track_search_angle")
        val trackingCoefficient = config!!.getDouble("tracking_coefficient")

        object: BukkitRunnable() {
            var tick = 0
            var trackTarget: Damageable? = null
            var movingVector = fireball.location.direction.multiply(velocity)
            override fun run() {
                // 추적 목표 탐색
                // 5틱마다 탐색
                /*
                * 추적 대상 탐색 알고리즘
                * 1. 투사체가 진행하는 방향으로 distance / 2.0 만큼 떨어진 위치에서
                *    getNearbyEntities(distance, distance, distance)를 통해 주변 엔티티를 탐색합니다.
                * 2. 탐색된 엔티티 중 isEnemy()가 true인 엔티티를 필터링합니다.
                * 3. 필터링된 엔티티 중, 투사체가 있는 위치로부터 distance 이내에 있는 엔티티를 필터링합니다.
                * 5. 필터링된 엔티티들에 대해, 투사체의 진행 방향 벡터와 엔티티의 위치 벡터 사이의 각도를 계산합니다.
                * 6. 목적합수: 필터링된 엔티티들에 대해, 투사체벡터와 투사체-엔티티 벡터를 외적합니다.
                *             목적함수의 값이 가장 작은 엔티티를 추적 대상으로 선택합니다.
                 */
                if ((tick % 5 == 0) && trackTarget != null) {
                    val trackSearchLocation = fireball.location.clone().add(movingVector.clone().multiply(trackSearchDistance / 2))
                    val entities = trackSearchLocation.getNearbyEntities(trackSearchDistance, trackSearchDistance, trackSearchDistance)

                    entities.removeIf { it !is Damageable || !AbilityManager().isEnemy(player, it) }
                    entities.removeIf { it.location.distance(fireball.location) > trackSearchDistance }

                    var best: Damageable? = null
                    var bestValue = Double.MAX_VALUE

                    for (entity in entities) {
                        val entityVector = entity.location.toVector().subtract(fireball.location.toVector())
                        val angle = acos(movingVector.clone().normalize().dot(entityVector.clone().normalize()))
                        if (angle > Math.toRadians(trackSearchAngle)) continue

                        val crossValue = (movingVector.clone().crossProduct(entityVector.clone())).length()

                        if (crossValue < bestValue) {
                            best = entity as Damageable
                            bestValue = crossValue
                        }
                    }

                    trackTarget = best
                }


                // trackTarget이 null이 아니면 추적
                if (trackTarget != null) {
                    val normalizedTargetVector = trackTarget!!.location.toVector().subtract(fireball.location.toVector()).normalize()

                    movingVector.add(normalizedTargetVector.multiply(trackingCoefficient))
                    movingVector.normalize().multiply(velocity)
                }

                // 피격 계산
                val rayTrace = world.rayTrace(
                    fireball.location,
                    movingVector,
                    velocity,
                    FluidCollisionMode.NEVER,
                    true,
                    0.0,
                    null
                )

                // 히트 판정
                if (rayTrace != null) {
                    val hitLocation = rayTrace.hitPosition.toLocation(world)

                    val entities = world.getNearbyEntities(hitLocation, damageRange*2, damageRange*2, damageRange*2)
                    entities.removeIf { it !is Damageable || !AbilityManager().isEnemy(player, it) }
                    entities.removeIf { it.location.distance(hitLocation) > damageRange }
                }


            }
        }
    }
}