package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.ability.ImpactPolicy
import com.github.lupi13.limpi.ability.ImpactTracker
import com.github.lupi13.limpi.platform.normalizeSafely

import fr.skytasul.guardianbeam.Laser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object WebShooter : Ability(
    grade = Grade.LEGENDARY,
    displayName = Component.text("웹슈터", NamedTextColor.WHITE),
    codeName = "web_shooter",
    material = Material.STRING,
    description = listOf(
        Component.text("왼손이 웹슈터로 고정됩니다.", NamedTextColor.WHITE),
        Component.text("엔티티, 벽에 붙여 해당 방향으로 가속합니다.", NamedTextColor.WHITE)
    ),
    restrictedSlot = 40,
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 ", NamedTextColor.WHITE).append(Component.text("웹슈터", NamedTextColor.YELLOW)).append(Component.text("를 사격합니다.", NamedTextColor.WHITE))),
            Component.text("${config!!.getDouble("range")}", NamedTextColor.GREEN)
                .append(Component.text("블럭의 사거리 내에 있는 블럭을 맞히면, 해당 방향으로 끌려갑니다.", NamedTextColor.WHITE)),
            Component.text("엔티티를 맞히면, 크기에 비례하여 서로 끌어당겨집니다.", NamedTextColor.WHITE),
            Component.text("이동중에 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("과 부딪히면 충돌 속도에 비례하여 피해와 넉백을 줍니다.", NamedTextColor.WHITE)),
            Component.text("이동중이나 줄을 놓은 뒤 땅, 벽에 부딪히면", NamedTextColor.WHITE),
            Component.text("충돌 속도에 비례하여 피해를 받습니다. (배수: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("impactDamageMultiplier")}", NamedTextColor.GREEN))
                .append(Component.text(")", NamedTextColor.WHITE)),
        )
    }



    fun getWebShooterItem(): ItemStack {
        val item = ItemStack(Material.TURTLE_SCUTE)
        val meta = item.itemMeta
        meta.itemModel = NamespacedKey("minecraft", "crossbow")
        meta.itemName(Component.text("웹슈터", NamedTextColor.WHITE))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 웹슈터를 사격합니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
            Component.text("(사거리: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("range")}", NamedTextColor.GREEN))
                .append(Component.text("블럭)", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta
        return item
    }


    override val activeItem: ItemStack by lazy {
        getWebShooterItem()
    }


    var isMoving = mutableSetOf<java.util.UUID>()
    @EventHandler(ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this) return

        event.isCancelled = true

        if (isMoving.contains(player.uniqueId)) {
            isMoving.remove(player.uniqueId)
            return
        }

        val world = player.world
        val direction = player.location.direction

        val rayTraceResult = world.rayTrace(player.eyeLocation, direction, config!!.getDouble("range"), FluidCollisionMode.NEVER, true, 0.0) { it != player }
        world.playSound(player.location, Sound.BLOCK_DISPENSER_LAUNCH, 1f, 2f)

        if (rayTraceResult == null) {
            player.sendActionBar(Component.text("웹슈터가 아무것도 맞히지 못했습니다.", NamedTextColor.RED))
            return
        }
        else {
            isMoving += player.uniqueId
            trackImpact(player)

            // 벽에 맞았을 때
            if (rayTraceResult.hitBlock != null) {
                val hitLocation = rayTraceResult.hitPosition.toLocation(world)
                player.playSound(player.location, Sound.BLOCK_CAVE_VINES_FALL, 1f, 1f)
                world.playSound(hitLocation, Sound.BLOCK_CAVE_VINES_FALL, 1f, 1f)

                //줄 이펙트
                val laser = Laser.GuardianLaser(hitLocation, player, -1, -1)

                object: com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
                    init { setup {
                        own { isMoving.remove(player.uniqueId) }
                        own { if (laser.isStarted) laser.stop() }
                        laser.start(plugin)
                    } }
                    override fun tick() {
                        if (player.isDead || !player.isOnline) {
                            isMoving.remove(player.uniqueId)
                            if (laser.isStarted) laser.stop()
                            cancel()
                            return
                        }

                        if (!isMoving.contains(player.uniqueId)) {
                            if (laser.isStarted) laser.stop()
                            cancel()
                            return
                        }


                        // 가속 이동
                        val delta = hitLocation.clone().subtract(player.location).toVector()
                        if (delta.lengthSquared() < 1.0e-8) { cancel(); return }
                        val movingVector = delta.normalizeSafely()
                        if (movingVector.y > 0) movingVector.setY(movingVector.y * 2.0)
                        movingVector.multiply(config!!.getDouble("velocityAmplifier"))

                        if ((player as LivingEntity).isOnGround && (hitLocation.distance(player.location) > 1.0)) {
                            movingVector.add(Vector(0.0, 0.15, 0.0)) // 지면에 있을 때는 약간 위로 튕겨오르게
                        }
                        player.velocity = player.velocity.clone().add(movingVector)
                        // 속도 제한
                        if (player.velocity.length() > config.getDouble("maxVelocity")
                            * (if (player.isInWater || player.isInLava) config.getDouble("liquidDragCoefficient") else 1.0)) {
                            player.velocity = player.velocity.clone().normalizeSafely().multiply(config.getDouble("maxVelocity")
                            * (if (player.isInWater || player.isInLava) config.getDouble("liquidDragCoefficient") else 1.0))
                        }

                        // 효과음: 속도에 비례
                        if (Random.nextDouble() <= max(0.01, player.velocity.length())) {
                            player.playSound(player.location, Sound.ITEM_SPYGLASS_USE, 1f, min(2f, (player.velocity.length() * 2.0).toFloat()))
                            player.playSound(player.location, Sound.ITEM_SPYGLASS_STOP_USING, 1f, min(2f, (player.velocity.length() * 2.0).toFloat()))
                        }


                        // 가까이 있는 적에게 피해
                        val nearbyEntities = player.getNearbyEntities(4.0, 4.0, 4.0)
                        for (entity in nearbyEntities) {
                            if (entity !is Damageable || entity == player || !entity.isValid || !entity.boundingBox.overlaps(player.boundingBox.expand(0.5))) continue
                            if ((entity as? LivingEntity)?.let { it.noDamageTicks > 10 } == true || !AbilityManager.isEnemy(player, entity)) continue

                            // 피해량: 상대속도에 비례
                            val damage = (config.getDouble("damageAmplifier") * (player.velocity.clone().subtract(entity.velocity).length().pow(2.0)))
//                            player.sendActionBar(Component.text("피해량: ${damage}", NamedTextColor.RED))
                            if (damage >= 1.0) {
                                if (!com.github.lupi13.limpi.platform.AbilityDamage.hit(player, entity, codeName, damage)) continue

                                val knockbackVector = entity.boundingBox.center.subtract(player.boundingBox.center).normalizeSafely()
                                    .multiply(config.getDouble("knockbackAmplifier")).multiply(player.velocity.length())
                                val playerRatio = entity.boundingBox.volume / (entity.boundingBox.volume + player.boundingBox.volume).coerceAtLeast(1e-9)
                                val entityRatio = 1.0 - playerRatio

                                player.velocity = player.velocity.clone().subtract(knockbackVector.clone().multiply(playerRatio))
                                entity.velocity = entity.velocity.clone().add(knockbackVector.clone().multiply(entityRatio))


                                player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f)
                            }

                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L)
            }


            // 엔티티에게 맞았을 때
            else {
                val hitEnemy = rayTraceResult.hitEntity!!
                player.playSound(player.location, Sound.BLOCK_CAVE_VINES_FALL, 1f, 1f)
                world.playSound(hitEnemy.location, Sound.BLOCK_CAVE_VINES_FALL, 1f, 1f)

                //줄 이펙트
                val laser = Laser.GuardianLaser(hitEnemy.boundingBox.center.toLocation(world), player, -1, -1)

                object: com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
                    init { setup {
                        own { isMoving.remove(player.uniqueId) }
                        own { if (laser.isStarted) laser.stop() }
                        laser.start(plugin)
                    } }
                    override fun tick() {
                        if (!hitEnemy.isValid || hitEnemy.isDead || hitEnemy.world.uid != world.uid) { cancel(); return }
                        val hitLocation = hitEnemy.boundingBox.center.toLocation(world)
                        laser.moveStart(hitLocation.toLocation(world))
                        if (player.isDead || !player.isOnline || !hitEnemy.isValid || hitEnemy.isDead) {
                            isMoving.remove(player.uniqueId)
                            if (laser.isStarted) laser.stop()
                            cancel()
                            return
                        }

                        if (!isMoving.contains(player.uniqueId)) {
                            if (laser.isStarted) laser.stop()
                            cancel()
                            return
                        }


                        // 가속 이동
                        val delta = hitLocation.clone().subtract(player.location.toVector()).toVector()
                        if (delta.lengthSquared() < 1.0e-8) { cancel(); return }
                        var movingVector = delta.normalizeSafely()
                        var entityVector = movingVector.clone().multiply(-1.0)
                        val playerVelRatio = 2 * hitEnemy.boundingBox.volume / (hitEnemy.boundingBox.volume + player.boundingBox.volume).coerceAtLeast(1e-9)
                        val entityVelRatio = 2.0 - playerVelRatio

                        if (movingVector.y > 0) movingVector.setY(movingVector.y * 2.0)
                        movingVector = movingVector.clone().multiply(config!!.getDouble("velocityAmplifier"))

                        if (entityVector.y > 0) entityVector.setY(entityVector.y * 2.0)
                        entityVector = entityVector.clone().multiply(config.getDouble("velocityAmplifier"))


                        if ((player as LivingEntity).isOnGround) {
                            movingVector.add(Vector(0.0, 0.15, 0.0)) // 지면에 있을 때는 약간 위로 튕겨오르게
                        }
                        player.velocity = player.velocity.clone().add(movingVector.multiply(playerVelRatio))
                        // 속도 제한
                        if (player.velocity.length() > config.getDouble("maxVelocity")
                            * (if (player.isInWater || player.isInLava) config.getDouble("liquidDragCoefficient") else 1.0)) {
                            player.velocity = player.velocity.clone().normalizeSafely().multiply(config.getDouble("maxVelocity")
                                    * (if (player.isInWater || player.isInLava) config.getDouble("liquidDragCoefficient") else 1.0))
                        }

                        if ((hitEnemy is LivingEntity) && hitEnemy.isOnGround) {
                            entityVector.add(Vector(0.0, 0.15, 0.0)) // 지면에 있을 때는 약간 위로 튕겨오르게
                        }
                        hitEnemy.velocity = hitEnemy.velocity.clone().add(entityVector.multiply(entityVelRatio))
                        // 속도 제한
                        if (hitEnemy.velocity.length() > config.getDouble("maxVelocity")
                            * (if (hitEnemy.isInWater || hitEnemy.isInLava) config.getDouble("liquidDragCoefficient") else 1.0)) {
                            hitEnemy.velocity = hitEnemy.velocity.clone().normalizeSafely().multiply(config.getDouble("maxVelocity")
                            * (if (hitEnemy.isInWater || hitEnemy.isInLava) config.getDouble("liquidDragCoefficient") else 1.0))
                        }

                        // 효과음: 속도에 비례
                        if (Random.nextDouble() <= max(0.01, player.velocity.length())) {
                            player.playSound(player.location, Sound.ITEM_SPYGLASS_USE, 1f, min(2f, (player.velocity.length() * 2.0).toFloat()))
                            player.playSound(player.location, Sound.ITEM_SPYGLASS_STOP_USING, 1f, min(2f, (player.velocity.length() * 2.0).toFloat()))
                        }


                        // 가까이 있는 적에게 피해
                        val nearbyEntities = player.getNearbyEntities(4.0, 4.0, 4.0)
                        for (entity in nearbyEntities) {
                            if (entity !is Damageable || entity == player || !entity.isValid || !entity.boundingBox.overlaps(player.boundingBox.expand(0.5))) continue
                            if ((entity as? LivingEntity)?.let { it.noDamageTicks > 10 } == true) continue

                            if (AbilityManager.isEnemy(player, entity)) {
                                // 피해량: 상대속도에 비례
                                val damage = (config.getDouble("damageAmplifier") * (player.velocity.clone().subtract(entity.velocity).length()).pow(2.0)) / 2.0
                                if (damage >= 1.0) {
                                    if (!com.github.lupi13.limpi.platform.AbilityDamage.hit(player, entity, codeName, damage)) continue

                                    val knockbackVector = entity.boundingBox.center.subtract(player.boundingBox.center).normalizeSafely()
                                        .multiply(config.getDouble("knockbackAmplifier")).multiply(player.velocity.length())
                                    val playerRatio = entity.boundingBox.volume / (entity.boundingBox.volume + player.boundingBox.volume).coerceAtLeast(1e-9)
                                    val entityRatio = 1.0 - playerRatio

                                    player.velocity = player.velocity.clone().subtract(knockbackVector.clone().multiply(playerRatio))
                                    entity.velocity = entity.velocity.clone().add(knockbackVector.clone().multiply(entityRatio))


                                    player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f)
                                }


                            }
                            if (entity == hitEnemy) {
                                isMoving.remove(player.uniqueId)
                                if (laser.isStarted) laser.stop()
                                cancel()
                                return
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L)
            }
        }
    }
    private val impactTrackers = mutableMapOf<java.util.UUID, Pair<ImpactTracker, com.github.lupi13.limpi.platform.AbilityEffect>>()

    /**
     * 줄에 끌려가는 동안과 줄을 놓은 뒤 첫 착지까지, 땅·벽 충돌 피해를 실제 이동 속도로 정한다.
     * 플레이어 이동은 클라이언트가 정하므로 player.velocity 대신 이동 패킷(클라이언트 1틱)마다 위치 차이를 잰다.
     * 서버 틱마다 재면 한 틱에 패킷 두 개가 도착할 때 속도가 두 배로 잡힌다.
     * 땅은 낙하 거리를 넘겨 바닐라 낙하 피해 규칙(블록, 방어구, 마법 부여)을 그대로 쓰고, 벽·천장은 FLY_INTO_WALL로 준다.
     */
    private fun trackImpact(player: Player) {
        if (impactTrackers.containsKey(player.uniqueId)) return
        val effect = com.github.lupi13.limpi.platform.AbilityRuntime.begin(player, codeName)
        effect.own { impactTrackers.remove(player.uniqueId) }
        impactTrackers[player.uniqueId] = ImpactTracker() to effect
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onImpactMove(event: PlayerMoveEvent) {
        val player = event.player
        val (tracker, effect) = impactTrackers[player.uniqueId] ?: return
        if (!effect.live()) { effect.close(); return }
        val attached = isMoving.contains(player.uniqueId)
        if (player.isGliding || player.isFlying || player.isInsideVehicle || player.isInWater || player.isInLava || player.isClimbing
            || player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            // 바닐라가 낙하를 따로 처리하는 상태: 줄을 놓았으면 끝내고, 매달려 있으면 측정만 다시 시작한다.
            if (attached) tracker.reset() else effect.close()
            return
        }
        val from = event.from; val to = event.to
        if (from.world != to.world) { tracker.reset(); return }
        val moved = to.toVector().subtract(from.toVector())
        if (moved.lengthSquared() > 100.0) { tracker.reset(); return } // 순간이동

        val half = player.width / 2
        val box = BoundingBox(to.x - half, to.y, to.z - half, to.x + half, to.y + player.height, to.z + half)
        val sample = tracker.sample(moved, config!!.getDouble("maxVelocity")) { axis, direction -> touches(player, box, axis, direction) }
        val safe = player.getAttribute(Attribute.SAFE_FALL_DISTANCE)?.value ?: 3.0
        val multiplier = config!!.getDouble("impactDamageMultiplier")
        val damage = ImpactPolicy.impactDamage(sample.wallSpeed, safe, multiplier)
        if (damage > 0.0) player.damage(damage, DamageSource.builder(DamageType.FLY_INTO_WALL).build())

        if (player.isOnGround) {
            if (!attached) effect.close()
            return
        }
        // 올라가는 중에는 0, 내려가는 중에는 아래로 떨어지는 속도에 닿는 자유낙하 높이
        player.fallDistance = ImpactPolicy.fallDistance(sample.fallSpeed, safe, multiplier).toFloat()
    }

    private fun touches(player: Player, box: BoundingBox, axis: Int, direction: Int): Boolean {
        val step = 0.05 * direction
        val probe = when (axis) {
            0 -> box.clone().expand(0.0, -0.01, -0.01).shift(step, 0.0, 0.0)
            1 -> box.clone().expand(-0.01, 0.0, -0.01).shift(0.0, step, 0.0)
            else -> box.clone().expand(-0.01, -0.01, 0.0).shift(0.0, 0.0, step)
        }
        return player.world.hasCollisionsIn(probe)
    }

    override fun release(owner: java.util.UUID) { isMoving.remove(owner); impactTrackers.remove(owner)?.second?.close() }
    override fun stop() { isMoving.clear(); impactTrackers.values.toList().forEach { it.second.close() }; impactTrackers.clear() }

}
