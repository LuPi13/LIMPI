package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Damageable
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

object BladeLeaper : Ability(
    grade = Grade.EPIC,
    element = Element.MAGIC,
    displayName = Component.text("칼날 도약", NamedTextColor.YELLOW),
    codeName = "blade_leaper",
    material = Material.GOLDEN_SWORD,
    description = listOf(
        Component.text("검을 우클릭하여 전방으로 던지고,", NamedTextColor.WHITE),
        Component.text("잠시 후 검 위치로 점멸 공격합니다.", NamedTextColor.WHITE),
    ),
    needFile = true
){

    override val details: List<Component> by lazy {
        listOf(
            Component.text("오른손으로 검을 들고 우클릭하면 전방으로 검을 던집니다.", NamedTextColor.WHITE),
            Component.text("검이 어딘가에 닿거나, 최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("throwLifeTimeTicks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초를 날아간 후,", NamedTextColor.WHITE)),
            Component.text("검이 있던 위치로 점멸하여 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("damageRadius")}", NamedTextColor.GREEN))
                .append(Component.text("칸 내 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 줍니다.", NamedTextColor.WHITE)),
            Component.text("쿨타임: 각 검마다 ")
                .append(Component.text("${config!!.getInt("coolTimeTicks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("throwLifeTimeTicks", 40)
            config?.set("throwVelocity", 2.0)
            config?.set("throwGravity", 0.03)
            config?.set("damageRadius", 4.0)
            config?.set("damage", 8.0)
            config?.set("coolTimeTicks", 200)
        }
        saveConfig()
    }


    val swords = mutableListOf(
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.GOLDEN_SWORD,
        Material.IRON_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD
    )


    val vector = mutableMapOf<ItemDisplay, Vector>()
    @EventHandler
    fun onClick(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this) return

        if (event.action.isLeftClick) return

        if (event.hand != EquipmentSlot.HAND) return

        if (event.item == null || event.item!!.type !in swords) return

        val item = event.item!!

        val cooldown = config!!.getInt("coolTimeTicks")
        if (player.getCooldown(item.type) > 0) {
            AbilityManager().showCooldown(player, item.type)
            return
        }


        val meta = item.itemMeta as org.bukkit.inventory.meta.Damageable
        if (player.gameMode != GameMode.CREATIVE) {
            val unbreaking = meta.enchants[Enchantment.UNBREAKING] ?: 0
            if (Random.nextDouble() <= 1.0 / (unbreaking + 1)) {
                meta.damage = meta.damage + 1
            }
            item.itemMeta = meta
            player.inventory.setItem(EquipmentSlot.HAND, null)
        }

        val color = when (item.type) {
            Material.WOODEN_SWORD -> Color.fromRGB(0x856c3d)
            Material.STONE_SWORD -> Color.fromRGB(0xa19d9a)
            Material.GOLDEN_SWORD -> Color.fromRGB(0xeaee6b)
            Material.IRON_SWORD -> Color.fromRGB(0xfcfcfc)
            Material.DIAMOND_SWORD -> Color.fromRGB(0x60eddb)
            Material.NETHERITE_SWORD -> Color.fromRGB(0x3f3b3c)
            else -> Color.WHITE
        }


        player.setCooldown(item.type, cooldown)

        // 검 던져지는 시작 위치
        val direction = player.eyeLocation.direction.clone().rotateAroundY(Math.toRadians(-90.0))
        val startPosition = player.eyeLocation.add(direction.multiply(0.5))

        val thrownSword = player.world.spawn(startPosition, ItemDisplay::class.java)
        thrownSword.setItemStack(item)
        thrownSword.transformation = Transformation(
            Vector3f(0f, -0.5f, 0f),
            AxisAngle4f(0f, 0f, 0f, 0f),
            Vector3f(0f, 0f, 0f),
            AxisAngle4f(0f, 0f, 0f, 0f)
        )
        thrownSword.interpolationDelay = 0
        thrownSword.interpolationDuration = 0

        val world = thrownSword.world
        val velocity = config!!.getDouble("throwVelocity")
        val gravity = config!!.getDouble("throwGravity")
        val lifeTime = config!!.getInt("throwLifeTimeTicks")
        val radius = config!!.getDouble("damageRadius")

        world.playSound(thrownSword.location, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 2f)

        // 1초간 준비, 1초간 이동, 0.5초간 점멸 및 공격
        object: BukkitRunnable() {
            var ticks = 0
            var tpLocation: Location? = null

            override fun run() {
                ticks++

                // 10틱: 선딜; 플레이어 옆에 위치하게
                if (ticks <= 10) thrownSword.teleport(player.eyeLocation
                    .add(player.location.direction.rotateAroundY(Math.toRadians(-90.0)).multiply(0.5))
                    .add(player.location.direction.multiply(0.5)))

                // item_display 등장 및 initial 회전
                if (ticks == 2) {
                    thrownSword.transformation = Transformation(
                        Vector3f(0f, 0f, 0f),
                        AxisAngle4f(0f, 0f, 0f, 0f),
                        Vector3f(1.0f, 1.0f, 1.0f),
                        AxisAngle4f(0f, 0f, 0f, 0f)
                    )
                    thrownSword.interpolationDelay = 0
                    thrownSword.interpolationDuration = 7
                }
                if (ticks == 4) {
                    thrownSword.transformation = Transformation(
                        Vector3f(0f, 0f, 0f),
                        AxisAngle4f((-Math.PI/2.0).toFloat(), 0f, 1f, 0f),
                        Vector3f(1.0f, 1.0f, 1.0f),
                        AxisAngle4f(0f, 0f, 0f, 0f)
                    )
                    thrownSword.interpolationDelay = 0
                    thrownSword.interpolationDuration = 5
                }

                // 던지는 순간의 방향벡터 저장
                if (ticks == 10) {
                    vector[thrownSword] = player.location.direction
                    world.playSound(thrownSword.location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 2f)
                }

                // lifeTime 동안 던져지는 검의 위치 업데이트
                if (ticks in 11..10 + lifeTime) {
                    val angle = Math.PI * (ticks - 10) / 5.0
                    thrownSword.transformation = Transformation(
                        Vector3f(0f, 0f, 0f),
                        AxisAngle4f((-Math.PI/2.0).toFloat(), 0f, 1f, 0f),
                        Vector3f(1.0f, 1.0f, 1.0f),
                        AxisAngle4f(angle.toFloat(), 0f, 0f, -1f)
                    )
                    thrownSword.interpolationDelay = 0
                    thrownSword.interpolationDuration = 1

                    // RayTrace로 검 충돌 여부 확인
                    val rayTraceResult = world.rayTrace(thrownSword.location,
                        vector[thrownSword]!!,
                        velocity + 0.5,
                        FluidCollisionMode.NEVER,
                        true,
                        0.0
                    ) { it != player }
                    if (rayTraceResult != null) {
                        ticks = 11+lifeTime
                    }

                    thrownSword.teleport(thrownSword.location.add(vector[thrownSword]!!.clone().multiply(velocity)))
                    vector[thrownSword] = vector[thrownSword]!!.subtract(Vector(0.0, gravity, 0.0))
                    world.spawnParticle(
                        Particle.DUST,
                        thrownSword.location,
                        10,
                        0.1, 0.1, 0.1, 0.1,
                        Particle.DustOptions(color, 1f)
                    )
                }

                // 검 위치로 점멸 전에 이동 효과, 느린낙하
                if (ticks in 11+lifeTime..14+lifeTime) {
                    val direction = vector[thrownSword]!!.clone()
                    player.velocity = direction.multiply((ticks - lifeTime - 10) / 2.0)
                    player.playSound(player.location, Sound.ENTITY_BREEZE_CHARGE, 0.5f, 1f)
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 10, 0, true, false, false))
                }

                // 검 위치로 점멸
                if (ticks == 15 + lifeTime) {
                    val rayTraceResult = world.rayTrace(thrownSword.location, vector[thrownSword]!!, velocity, FluidCollisionMode.NEVER, true, 0.0, null)
                    tpLocation = rayTraceResult?.hitPosition?.toLocation(world)?.subtract(vector[thrownSword]!!.multiply(velocity)) ?: thrownSword.location.subtract(vector[thrownSword]!!.multiply(velocity))
                    player.teleport(tpLocation!!.setRotation(player.location.yaw, player.location.pitch))
                    player.playSound(player.location, Sound.ITEM_BOTTLE_FILL_DRAGONBREATH, 0.5f, 0.5f)
                    world.playSound(tpLocation!!, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f)
                    world.playSound(tpLocation!!, Sound.ENTITY_BREEZE_IDLE_AIR, 1f, 1.5f)
                    thrownSword.teleport(tpLocation!!.clone().setRotation(0f, 90f))

                    thrownSword.transformation = Transformation(
                        Vector3f(0.0f, 0.0f, 0.0f),
                        AxisAngle4f(0f, 0f, 0f, 0f),
                        Vector3f((radius*1.5).toFloat(), (radius*1.5).toFloat(), 1.0f),
                        AxisAngle4f(0f, 0f, 0f, 1f)
                    )
                    thrownSword.interpolationDelay = 0
                    thrownSword.interpolationDuration = 2
                }

                // 검 회전 효과
                if (ticks in 17+lifeTime..24+lifeTime) {
                    val angle = Math.PI * (ticks - lifeTime - 16.0).pow(1.5) / 8.0
                    thrownSword.transformation = Transformation(
                        Vector3f(0.0f, 0.0f, 0.0f),
                        AxisAngle4f(0f, 0f, 0f, 0f),
                        Vector3f((radius*1.5).toFloat(), (radius*1.5).toFloat(), 1.0f),
                        AxisAngle4f(angle.toFloat(), 0f, 0f, 1f)
                    )
                    thrownSword.interpolationDelay = 0
                    thrownSword.interpolationDuration = 1

                    // 회전 파티클
                    for (theta in 0..360 step 5) {
                        val x = radius * cos(Math.toRadians(theta.toDouble()))
                        val z = radius * sin(Math.toRadians(theta.toDouble()))
                        world.spawnParticle(
                            Particle.DUST,
                            tpLocation!!.clone().add(x, 0.0, z),
                            0, 0.1, 0.1, 0.1, 0.1,
                            Particle.DustOptions(color, 2f)
                        )

                        //x, z 90도 회전
                        val localRadius = Random.nextDouble(0.0, radius)
                        val xl = localRadius * cos(Math.toRadians(theta.toDouble()))
                        val zl = localRadius * sin(Math.toRadians(theta.toDouble()))
                        val xp = localRadius * cos(Math.toRadians(theta.toDouble() + 90.0))
                        val zp = localRadius * sin(Math.toRadians(theta.toDouble() + 90.0))

                        if (Random.nextDouble() < 0.2) {
                            world.spawnParticle(
                                Particle.POOF,
                                tpLocation!!.clone().add(xl, 0.0, zl),
                                0, xp, 0.0, zp, 0.3
                            )
                        }
                    }
                }

                // 검 위치에서 주변 적에게 피해
                if (ticks == 20+lifeTime) {
                    world.playSound(tpLocation!!, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f)
                    val entities = world.getNearbyEntities(
                        tpLocation!!,
                        radius*2.0,
                        radius,
                        radius*2.0)

                    for (entity in entities) {
                        if (entity !is Damageable) continue
                        val yEqualLocation = entity.location.clone()
                        yEqualLocation.y = player.location.y
                        if (yEqualLocation.distance(player.location) > radius) continue
                        if (entity == player || entity == thrownSword) continue
                        if (!AbilityManager().isEnemy(player, entity)) continue

                        entity.damage(config!!.getDouble("damage"))

                        //넉백
                        val knockback = entity.location.clone().subtract(tpLocation!!).toVector().normalize().multiply(0.5)
                        knockback.setY(0.3)
                        entity.velocity = entity.velocity.add(knockback)
                    }
                }

                // 검 제거
                if (ticks > 25+lifeTime) {
                    if (player.gameMode != GameMode.CREATIVE) {
                        val meta = thrownSword.itemStack.itemMeta as org.bukkit.inventory.meta.Damageable
                        if (meta.damage < item.type.maxDurability) {
                            player.world.dropItem(tpLocation!!, item)
                        }
                        else {
                            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
                        }
                    }


                    if (player.gameMode != GameMode.CREATIVE)
                    vector.remove(thrownSword)
                    thrownSword.remove()
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}