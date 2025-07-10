package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

object GatheringStorm : Ability(
    grade = Grade.LEGENDARY,
    element = Element.ELECTRIC,
    displayName = Component.text("폭풍 결집", NamedTextColor.AQUA),
    codeName = "gathering_storm",
    material = Material.TRIDENT,
    description = listOf(
        Component.text("삼지창의 ", NamedTextColor.WHITE)
            .append(Component.text("집전", NamedTextColor.AQUA))
            .append(Component.text(" 마법부여를 대폭 강화합니다.", NamedTextColor.WHITE)),
        Component.text("집전", NamedTextColor.AQUA)
            .append(Component.text(" 삼지창이 부딪힌 곳에", NamedTextColor.WHITE)),
        Component.text("번개 폭풍을 생성합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){

    override val details: List<Component> by lazy {
        listOf(
            Component.text("집전", NamedTextColor.AQUA)
                .append(Component.text(" 삼지창의 투사 속도가 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("projection_velocity")}", NamedTextColor.GREEN))
                .append(Component.text("배 증가합니다.", NamedTextColor.WHITE)),
            Component.text("밤 또는 비/뇌우 날씨중에 ", NamedTextColor.WHITE)
                .append(Component.text("집전", NamedTextColor.AQUA))
                .append(Component.text(" 삼지창이 부딪힌 곳에 번개 폭풍을 생성합니다.", NamedTextColor.WHITE)),
            Component.text("폭풍의 반경은 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("storm_radius")}", NamedTextColor.GREEN))
                .append(Component.text("블럭 이며, ", NamedTextColor.WHITE))
                .append(Component.text("매 틱마다 "))
                .append(Component.text("${config!!.getDouble("damage_probability") * 100}%", NamedTextColor.GREEN))
                .append(Component.text(" 확률로 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("storm_damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 줍니다.", NamedTextColor.WHITE)),
            Component.text("폭풍 지속 시간: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("storm_duration_tick") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초, 폭풍 생성 쿨타임: ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("storm_cooldown_milliseconds") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("projection_velocity", 2.0)
            config?.set("storm_radius", 10.0)
            config?.set("storm_damage", 4.0)
            config?.set("damage_probability", 0.2)
            config?.set("storm_duration_tick", 200)
            config?.set("storm_cooldown_milliseconds", 20000)
        }
        saveConfig()
    }


    //투사 속도 증가
    @EventHandler
    fun onTridentLaunch(event: ProjectileLaunchEvent) {
        if (event.entity !is Trident) return
        val trident = event.entity as Trident

        if (trident.shooter == null || trident.shooter !is Player) return
        val player = trident.shooter as Player
        if (player.ability != this) return

        // 집전 삼지창인지 확인
        if (!trident.itemStack.enchantments.containsKey(Enchantment.CHANNELING)) return

        // 투사 속도 증가
        trident.velocity = trident.velocity.multiply(config!!.getDouble("projection_velocity"))
    }


    // 폭풍 생성
    val stormCooldown = mutableMapOf<Player, Long>()
    val stormDuration = mutableMapOf<Location, Int>()
    @EventHandler
    fun onTridentHit(event: ProjectileHitEvent) {
        if (event.entity !is Trident) return
        val trident = event.entity as Trident

        if (trident.shooter == null || trident.shooter !is Player) return
        val player = trident.shooter as Player
        if (player.ability != this) return

        // 집전 삼지창인지 확인
        if (!trident.itemStack.enchantments.containsKey(Enchantment.CHANNELING)) return


        // 폭풍 생성 쿨타임 확인
        val currentTime = System.currentTimeMillis()
        val cooldown = config!!.getInt("storm_cooldown_milliseconds")
        if (stormCooldown.containsKey(player) && currentTime - stormCooldown[player]!! < cooldown) {
            AbilityManager().showCooldown(player, cooldown, stormCooldown[player]!!)
            return
        }

        val world = trident.world
        // 날씨 확인: 밤 또는 비/뇌우
        if (world.environment == World.Environment.NORMAL &&
            (world.time !in 14000..22000 && world.isClearWeather)) return


        // 폭풍 생성
        val radius = config!!.getDouble("storm_radius")
        val damage = config!!.getDouble("storm_damage")
        val probability = config!!.getDouble("damage_probability")
        val duration = config!!.getInt("storm_duration_tick")
        val location = trident.location
        stormCooldown[player] = currentTime
        stormDuration[location] = 0


        world.playSound(location, Sound.ITEM_ELYTRA_FLYING, 1f, 0.5f)


        object: BukkitRunnable() {
            override fun run() {
                if (!stormDuration.containsKey(location)) {
                    cancel()
                }

                if (stormDuration[location]!! == 20) {
                    world.strikeLightningEffect(location)
                    world.playSound(location, Sound.ITEM_TRIDENT_THUNDER, 1f, 1f)

                    // 사방으로 파티클 발사
                    for (i in 0..500) {
                        val direction = Vector(
                            Random.nextDouble(-1.0, 1.0),
                            Random.nextDouble(0.0, 2.0),
                            Random.nextDouble(-1.0, 1.0)
                        )
                        world.spawnParticle(Particle.SNOWFLAKE, location, 0, direction.x, direction.y, direction.z, 1.0)
                    }
                }

                for (y in location.y.toInt()..320 step 8) {
                    world.spawnParticle(
                        Particle.END_ROD,
                        location.x,
                        y.toDouble(),
                        location.z,
                        1,
                        0.0,
                        2.5,
                        0.0,
                        0.0,
                        null,
                        true
                    )
                }

                // 1초 후부터
                if (stormDuration[location]!! >= 20) {
                    // 주변 엔티티에 피해 주기
                    val entities = world.getNearbyEntities(location, radius*2, radius*2, radius*2)
                    for (entity in entities) {
                        if (entity !is Damageable) continue
                        if (entity.location.distance(location) > radius) continue
                        if (!AbilityManager().isEnemy(player, entity)) continue

                        // 확률적으로 피해를 줌
                        if (Random.nextDouble() <= probability) {
                            if (entity is Turtle) {
                                entity.damage(3.4*(10.0.pow(38.0)), player)
                            }
                            else {
                                entity.damage(damage, player)
                            }

                            // 번개에 의한 몹 변환
                            when (entity) {
                                is Creeper -> {
                                    entity.isPowered = true
                                }

                                is Pig -> {
                                    world.spawn(entity.location, PigZombie::class.java)
                                    val loc = entity.location.clone()
                                    loc.y = -100.0
                                    entity.teleport(loc)
                                    entity.remove()
                                }

                                is Villager -> {
                                    world.spawn(entity.location, Witch::class.java)
                                    val loc = entity.location.clone()
                                    loc.y = -100.0
                                    entity.teleport(loc)
                                    entity.remove()
                                }

                                is MushroomCow -> {
                                    when (entity.variant) {
                                        MushroomCow.Variant.BROWN -> entity.variant = MushroomCow.Variant.RED
                                        MushroomCow.Variant.RED -> entity.variant = MushroomCow.Variant.BROWN
                                    }
                                }
                            }

                            // 번개 효과
                            val lightVector = entity.boundingBox.center.subtract(location.toVector())
                            for (i in 1..10) {
                                val offset = lightVector.clone().multiply(i / 10.0)
                                val particleLocation = location.clone().add(offset)
                                world.spawnParticle(Particle.ELECTRIC_SPARK, particleLocation, 1, 0.1, 0.1, 0.1, 0.01)
                            }
                            world.playSound(entity.location, Sound.ENTITY_GUARDIAN_ATTACK, 1f, 2f)
                            world.playSound(entity.location, Sound.ENTITY_BEE_HURT, 1f, 2f)

                        }
                    }

                    world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 2f)
                }

                // 반경 효과
                for (i in 0..360 step 30) {
                    for (j in 0..180 step 30) {
                        val angle = Math.toRadians(i.toDouble() + Random.nextDouble(-15.0, 15.0))
                        val angle2 = Math.toRadians(j.toDouble() + Random.nextDouble(-15.0, 15.0))
                        // 두 각도에 대한 방향 벡터 계산
                        val direction = Vector(
                            cos(angle) * sin(angle2),
                            sin(angle),
                            cos(angle) * cos(angle2)
                        )

                        // 위치 계산
                        val hitLocation = location.clone().add(direction.multiply(radius))
                        world.spawnParticle(Particle.ELECTRIC_SPARK, hitLocation, 1, 0.1, 0.1, 0.1, 0.01)
                    }
                }


                // 회오리 효과
                for (r in 1 until radius.toInt()) {
                    for (angle in 0 until 360 step 30) {
                        if (Random.nextDouble() >= (stormDuration[location]!! / (duration / 10.0)).pow(2.0).coerceAtMost(0.3)) continue
                        val radius = r + Random.nextDouble(-0.5, 0.5)
                        val radians = Math.toRadians(angle.toDouble() + Random.nextDouble(-15.0, 15.0))
                        val x = radius * cos(radians)
                        val z = radius * sin(radians)
                        val offsetLocation = location.clone().add(x, 0.0, z)

                        // 이동방향 벡터
                        val offsetVector = Vector(x, 0.0, z).rotateAroundY(Math.toRadians(Random.nextDouble(80.0, 100.0)))

                        world.spawnParticle(
                            Particle.POOF,
                            offsetLocation.subtract(offsetVector.multiply(0.5)),
                            0,
                            offsetVector.x,
                            Random.nextDouble(-0.1, 1.0),
                            offsetVector.z,
                            0.3
                        )
                    }
                }

                stormDuration[location] = stormDuration[location]!! + 1
                if (stormDuration[location]!! >= duration) {
                    stormDuration.remove(location)
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    // 기존 집전 효과 취소
    @EventHandler
    fun cancelChannelingLightning(event: LightningStrikeEvent) {
        if (event.cause != LightningStrikeEvent.Cause.TRIDENT) return
        val player = event.lightning.causingEntity as? Player ?: return
        if (player.ability != this) return

        val currentTime = System.currentTimeMillis()
        val cooldown = config!!.getInt("storm_cooldown_milliseconds")
        if (stormCooldown.containsKey(player) && currentTime - stormCooldown[player]!! < cooldown) {
            return
        }

        event.isCancelled = true
    }
}