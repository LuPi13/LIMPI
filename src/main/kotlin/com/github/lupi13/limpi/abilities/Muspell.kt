package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Damageable
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object Muspell : Ability(
    grade = Grade.EPIC,
    displayName = Component.text("무스펠", NamedTextColor.DARK_RED),
    codeName = "muspell",
    material = Material.FIRE_CHARGE,
    description = listOf(
        Component.text("일반 화살을 유도력 있는", NamedTextColor.WHITE),
        Component.text("불덩이로 변환합니다.", NamedTextColor.WHITE),
    ),
    needFile = true
) {

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("최대로 당겨 쏜 일반 ", NamedTextColor.WHITE).append(Component.text("화살", NamedTextColor.YELLOW)).append(Component.text("이 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
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


    @EventHandler(ignoreCancelled = true)
    fun onArrowLaunch(event: ProjectileLaunchEvent) {
        val player = event.entity.shooter as? Player ?: return
        if (player.ability != this) return
        if (event.entity !is Arrow) return
        val arrow = event.entity as Arrow
        if (!arrow.isCritical) return

        arrow.remove()
        event.isCancelled = true

        val world = player.world

        world.playSound(player.location, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f)
        world.playSound(player.location, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f)
        world.playSound(player.location, Sound.ENTITY_BLAZE_HURT, 0.7f, 0.5f)
        world.spawnParticle(
            Particle.FLAME,
            player.eyeLocation,
            50,
            0.2, 0.2, 0.2, 0.1
        )

        val damage = config!!.getDouble("damage")
        val damageRange = config!!.getDouble("damage_range")
        val lifetime = config!!.getInt("lifetime_ticks")
        val velocity = config!!.getDouble("velocity")
        val fireTicks = config!!.getInt("fire_ticks")
        val trackSearchDistance = config!!.getDouble("track_search_distance")
        val trackSearchAngle = Math.toRadians(config!!.getDouble("track_search_angle"))
        val maxTurn = Math.toRadians(config!!.getDouble("max_turn_per_tick"))
        val maxTotalTurn = Math.toRadians(config!!.getDouble("max_total_turn"))
        val proximityFuse = config!!.getDouble("proximity_fuse")
        val maxLeadTicks = config!!.getDouble("max_lead_ticks")

        object: com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
            val fireball = spawn(player.eyeLocation, Marker::class.java) { marker ->
                marker.setGravity(false)
                marker.isInvulnerable = true
            }
            var tick = 0
            var trackTarget: Damageable? = null
            /** 추적 대상의 지난 틱 중심(속도 추정용). 대상이 바뀌면 지운다. */
            var lastTargetCenter: org.bukkit.util.Vector? = null
            /** 발사 방향. 진행 방향은 여기서 max_total_turn 이상 꺾이지 않는다(유턴 없음). */
            val launch = player.location.direction.normalizeSafely()
            var movingVector = launch.clone().multiply(velocity)

            /** 벽에 가리지 않았는지. */
            fun visible(to: org.bukkit.util.Vector): Boolean {
                val offset = to.clone().subtract(fireball.location.toVector())
                if (offset.lengthSquared() == 0.0) return true
                return world.rayTraceBlocks(fireball.location, offset.clone().normalizeSafely(), offset.length(),
                    FluidCollisionMode.NEVER, true)?.hitBlock == null
            }

            override fun tick() {
                if (!fireball.isValid || fireball.world.uid != world.uid) { cancel(); return }
                if (trackTarget?.let { !it.isValid || it.isDead || it.world.uid != world.uid } == true) { trackTarget = null; lastTargetCenter = null }
                /*
                 * 추적 대상 탐색(5틱마다, 대상이 있어도 더 나은 대상으로 바꿀 수 있음)
                 * 1. 진행 방향으로 탐색 거리의 절반만큼 앞을 중심으로 주변 적을 모은다.
                 * 2. 탐색 거리 이내, 진행 방향에서 track_search_angle 이내, 발사 방향에서 max_total_turn 이내(갈 수 있는 곳),
                 *    벽에 가리지 않은 적만 남긴다.
                 * 3. 비행선에서 가장 가까운 적(진행 방향과 적 방향 벡터의 외적 크기가 가장 작은 적)을 고른다.
                 */
                if (tick % 5 == 0) {
                    val direction = movingVector.clone().normalizeSafely()
                    val trackSearchLocation = fireball.location.clone().add(direction.clone().multiply(trackSearchDistance / 2))
                    val entities = trackSearchLocation.getNearbyEntities(trackSearchDistance, trackSearchDistance, trackSearchDistance)
                    entities.removeIf { it !is Damageable || !AbilityManager.isEnemy(player, it) }
                    entities.removeIf { it.boundingBox.center.distance(fireball.location.toVector()) > trackSearchDistance }

                    var best: Damageable? = null
                    var bestValue = Double.MAX_VALUE
                    for (entity in entities) {
                        val entityVector = entity.boundingBox.center.subtract(fireball.location.toVector())
                        if (entityVector.lengthSquared() == 0.0) { best = entity as Damageable; break }
                        if (com.github.lupi13.limpi.ability.HomingPolicy.angle(direction, entityVector) > trackSearchAngle) continue
                        if (com.github.lupi13.limpi.ability.HomingPolicy.angle(launch, entityVector) > maxTotalTurn) continue
                        if (!visible(entity.boundingBox.center)) continue
                        val crossValue = direction.clone().crossProduct(entityVector).length()
                        if (crossValue < bestValue) {
                            best = entity as Damageable
                            bestValue = crossValue
                        }
                    }
                    if (best?.uniqueId != trackTarget?.uniqueId) lastTargetCenter = null
                    trackTarget = best
                }

                // 대상이 진행 방향 뒤쪽이 되면(지나치면) 추적을 그만둔다. 되돌아가지 않는다.
                trackTarget?.let { current ->
                    val targetVector = current.boundingBox.center.subtract(fireball.location.toVector())
                    if (movingVector.dot(targetVector) < 0) { trackTarget = null; lastTargetCenter = null }
                }

                // 조향: 대상의 이동을 예측한 지점으로, 틱당 max_turn_per_tick까지, 발사 방향에서 max_total_turn까지
                val target = trackTarget
                val desired = target?.let {
                    val center = it.boundingBox.center
                    val targetVelocity = lastTargetCenter?.let { last -> center.clone().subtract(last) } ?: org.bukkit.util.Vector()
                    lastTargetCenter = center.clone()
                    com.github.lupi13.limpi.ability.HomingPolicy.leadPoint(fireball.location.toVector(), center, targetVelocity, velocity, maxLeadTicks)
                        .subtract(fireball.location.toVector())
                }
                movingVector = com.github.lupi13.limpi.ability.HomingPolicy.steer(movingVector, desired, launch, maxTurn, maxTotalTurn, velocity)

                // 근접 신관: 이번 틱 이동 경로가 추적 대상에게 proximity_fuse 이내로 지나가면 그 지점에서 터진다.
                val fuse = target?.boundingBox?.clone()?.expand(proximityFuse)
                    ?.rayTrace(fireball.location.toVector(), movingVector.clone().normalizeSafely(), velocity)

                // 피격 계산
                val rayTrace = world.rayTrace(
                    fireball.location,
                    movingVector,
                    velocity,
                    FluidCollisionMode.ALWAYS,
                    true,
                    0.0,
                    { it !is Marker && it != player },
                    { it.type == Material.WATER || !it.isPassable }
                )

                // 히트 판정: 직접 맞음(블록·엔티티)과 근접 신관 중 더 가까운 지점. 벽이 더 가까우면 벽에서 터진다.
                val origin = fireball.location.toVector()
                val direct = rayTrace?.hitPosition
                val fused = fuse?.hitPosition?.takeIf { direct == null || it.distanceSquared(origin) < direct.distanceSquared(origin) }
                val hitPosition = fused ?: direct
                if (hitPosition != null) {
                    val hitLocation = hitPosition.toLocation(world)

                    // 물에 닿으면 제거
                    if (fused == null && rayTrace?.hitBlock?.type == Material.WATER) {
                        tick = lifetime // 종료
                        fireball.remove()
                        cancel()
                        return
                    }

                    val entities = world.getNearbyEntities(hitLocation, damageRange*2, damageRange*2, damageRange*2)
                    entities.removeIf { it !is Damageable || !AbilityManager.isEnemy(player, it) }
                    // 거리는 몸 중심이 아니라 판정 상자 표면까지 잰다. 근접 신관 거리 안이면 직접 맞은 것과 같은 피해이고, 큰 몹도 손해 보지 않는다.
                    val surface = { entity: org.bukkit.entity.Entity -> com.github.lupi13.limpi.ability.HomingPolicy.surfaceDistance(entity.boundingBox, hitPosition) }
                    entities.removeIf { surface(it) > damageRange }

                    for (entity in entities) {
                        // 불을 먼저 붙여야 이 피해로 죽은 동물이 익힌 고기를 떨어뜨린다(바닐라는 죽는 순간 불타는지로 정한다).
                        // 피해가 거부되면(아군·보호·취소) 불도 되돌린다.
                        val previousFire = entity.fireTicks
                        entity.fireTicks = maxOf(previousFire, fireTicks)
                        if (!com.github.lupi13.limpi.platform.AbilityDamage.hit(player, entity as Damageable, codeName,
                                damage / surface(entity).coerceIn(1.0, damageRange.coerceAtLeast(1.0)))) {
                            if (entity.isValid) entity.fireTicks = previousFire
                        }
                    }
                    world.spawnParticle(
                        Particle.SMOKE,
                        hitLocation,
                        50,
                        0.2, 0.2, 0.2, 0.1
                    )
                    world.spawnParticle(
                        Particle.FLAME,
                        hitLocation,
                        100,
                        0.2, 0.2, 0.2, 0.1
                    )
                    world.playSound(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f)

                    tick = lifetime // 종료
                }

                // 이동
                fireball.teleport(fireball.location.add(movingVector))
                world.spawnParticle(Particle.FLAME, fireball.location, 10, 0.2, 0.2, 0.2, 0.01, null, true)
                world.spawnParticle(Particle.SMOKE, fireball.location, 20, 0.2, 0.2, 0.2, 0.01)
                world.playSound(fireball.location, Sound.ENTITY_BREEZE_CHARGE, 0.5f, 0.5f)

                tick++
                if (tick >= lifetime) {
                    fireball.remove()
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}
