package com.github.lupi13.limpi.abilities

import fr.skytasul.guardianbeam.Laser
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.KeybindComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Cow
import org.bukkit.entity.Creeper
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object WebShooter : Ability(
    grade = Grade.LEGENDARY,
    element = Element.UTILITY,
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
    override val details: List<Component> by lazy {
        listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 웹슈터를 사격합니다.", NamedTextColor.WHITE)),
            Component.text("${config!!.getDouble("range")}", NamedTextColor.GREEN)
                .append(Component.text("블럭의 사거리 내에 있는 블럭을 맞히면, 해당 방향으로 끌려갑니다.", NamedTextColor.WHITE)),
            Component.text("엔티티를 맞히면, 크기에 비례하여 서로 끌어당겨집니다.", NamedTextColor.WHITE),
            Component.text("이동중에 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("과 부딪히면 충돌 속도에 비례하여 피해와 넉백을 줍니다.", NamedTextColor.WHITE)),
        )
    }


    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("range", 64.0)
            config?.set("velocityAmplifier", 0.15)
            config?.set("maxVelocity", 1.0)
            config?.set("damageAmplifier", 8.0)
            config?.set("knockbackAmplifier", 3.0)
            config?.set("liquidDragCoefficient", 0.3)
        }
        saveConfig()
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

    /**
     * 속도에 비례하여 적에게 피해를 주고 넉백을 줍니다.
     */
    fun damageNearbyEnemy(player: Player) {
    }

    override val activeItem: ItemStack by lazy {
        getWebShooterItem()
    }


    var isMoving = mutableListOf<Player>()
    @EventHandler
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this) return

        event.isCancelled = true

        if (isMoving.contains(player)) {
            isMoving.remove(player)
            return
        }

        val world = player.world
        val direction = player.location.direction

        val rayTraceResult = world.rayTrace(player.eyeLocation, direction, config!!.getDouble("range"), FluidCollisionMode.NEVER, true, 0.0, {it != player})
        world.playSound(player.location, Sound.BLOCK_DISPENSER_LAUNCH, 1f, 2f)

        if (rayTraceResult == null) {
            player.sendActionBar(Component.text("웹슈터가 아무것도 맞히지 못했습니다.", NamedTextColor.RED))
            return
        }
        else {
            isMoving += player

            // 벽에 맞았을 때
            if (rayTraceResult.hitBlock != null) {
                val hitLocation = rayTraceResult.hitPosition.toLocation(world)
                player.playSound(player.location, Sound.BLOCK_CAVE_VINES_FALL, 1f, 1f)
                world.playSound(hitLocation, Sound.BLOCK_CAVE_VINES_FALL, 1f, 1f)

                //줄 이펙트
                val laser = Laser.GuardianLaser(hitLocation, player, -1, -1)
                laser.start(plugin)

                object: BukkitRunnable() {
                    override fun run() {
                        if (player.isDead || !player.isOnline) {
                            isMoving.remove(player)
                            laser.stop()
                            cancel()
                            return
                        }

                        if (!isMoving.contains(player)) {
                            laser.stop()
                            cancel()
                            return
                        }


                        // 가속 이동
                        var movingVector = hitLocation.clone().subtract(player.location).toVector().normalize()
                        if (movingVector.y > 0) movingVector.setY(movingVector.y * 2.0)
                        movingVector.multiply(config!!.getDouble("velocityAmplifier"))

                        if ((player as LivingEntity).isOnGround && (hitLocation.distance(player.location) > 1.0)) {
                            movingVector.add(Vector(0.0, 0.15, 0.0)) // 지면에 있을 때는 약간 위로 튕겨오르게
                        }
                        player.velocity = player.velocity.clone().add(movingVector)
                        // 속도 제한
                        if (player.velocity.length() > config!!.getDouble("maxVelocity")
                            * (if (player.isInWater || player.isInLava) config!!.getDouble("liquidDragCoefficient") else 1.0)) {
                            player.velocity = player.velocity.clone().normalize().multiply(config!!.getDouble("maxVelocity")
                            * (if (player.isInWater || player.isInLava) config!!.getDouble("liquidDragCoefficient") else 1.0))
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
                            if ((entity as LivingEntity).noDamageTicks > 10 || !AbilityManager().isEnemy(player, entity)) continue

                            // 피해량: 상대속도에 비례
                            val damage = (config!!.getDouble("damageAmplifier") * (player.velocity.clone().subtract(entity.velocity).length().pow(2.0)))
//                            player.sendActionBar(Component.text("피해량: ${damage}", NamedTextColor.RED))
                            if (damage >= 1.0) {
                                entity.damage(damage, player)

                                val knockbackVector = entity.boundingBox.center.subtract(player.boundingBox.center).normalize()
                                    .multiply(config!!.getDouble("knockbackAmplifier")).multiply(player.velocity.length())
                                val playerRatio = entity.boundingBox.volume / (entity.boundingBox.volume + player.boundingBox.volume)
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
                val laser = Laser.GuardianLaser(hitEnemy.location, player, -1, -1)
                laser.start(plugin)

                object: BukkitRunnable() {
                    override fun run() {
                        val hitLocation = hitEnemy.location
                        laser.moveStart(hitLocation.toLocation(world))
                        if (player.isDead || !player.isOnline || !hitEnemy.isValid || hitEnemy.isDead) {
                            isMoving.remove(player)
                            laser.stop()
                            cancel()
                            return
                        }

                        if (!isMoving.contains(player)) {
                            laser.stop()
                            cancel()
                            return
                        }


                        // 가속 이동
                        var movingVector = hitLocation.clone().subtract(player.location.toVector()).toVector().normalize()
                        var entityVector = movingVector.clone().multiply(-1.0)
                        val playerVelRatio = 2 * hitEnemy.boundingBox.volume / (hitEnemy.boundingBox.volume + player.boundingBox.volume)
                        val entityVelRatio = 2.0 - playerVelRatio

                        if (movingVector.y > 0) movingVector.setY(movingVector.y * 2.0)
                        movingVector = movingVector.clone().multiply(config!!.getDouble("velocityAmplifier"))

                        if (entityVector.y > 0) entityVector.setY(entityVector.y * 2.0)
                        entityVector = entityVector.clone().multiply(config!!.getDouble("velocityAmplifier"))


                        if ((player as LivingEntity).isOnGround) {
                            movingVector.add(Vector(0.0, 0.15, 0.0)) // 지면에 있을 때는 약간 위로 튕겨오르게
                        }
                        player.velocity = player.velocity.clone().add(movingVector.multiply(playerVelRatio))
                        // 속도 제한
                        if (player.velocity.length() > config!!.getDouble("maxVelocity")
                            * (if (player.isInWater || player.isInLava) config!!.getDouble("liquidDragCoefficient") else 1.0)) {
                            player.velocity = player.velocity.clone().normalize().multiply(config!!.getDouble("maxVelocity")
                                    * (if (player.isInWater || player.isInLava) config!!.getDouble("liquidDragCoefficient") else 1.0))
                        }

                        if ((hitEnemy is LivingEntity) && hitEnemy.isOnGround) {
                            entityVector.add(Vector(0.0, 0.15, 0.0)) // 지면에 있을 때는 약간 위로 튕겨오르게
                        }
                        hitEnemy.velocity = hitEnemy.velocity.clone().add(entityVector.multiply(entityVelRatio))
                        // 속도 제한
                        if (hitEnemy.velocity.length() > config!!.getDouble("maxVelocity")
                            * (if (hitEnemy.isInWater || hitEnemy.isInLava) config!!.getDouble("liquidDragCoefficient") else 1.0)) {
                            hitEnemy.velocity = hitEnemy.velocity.clone().normalize().multiply(config!!.getDouble("maxVelocity")
                            * (if (hitEnemy.isInWater || hitEnemy.isInLava) config!!.getDouble("liquidDragCoefficient") else 1.0))
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
                            if ((entity as LivingEntity).noDamageTicks > 10) continue

                            if (AbilityManager().isEnemy(player, entity)) {
                                // 피해량: 상대속도에 비례
                                val damage = (config!!.getDouble("damageAmplifier") * (player.velocity.clone().subtract(entity.velocity).length()).pow(2.0)) / 2.0
//                                player.sendActionBar(Component.text("피해량: ${damage}", NamedTextColor.RED))
                                if (damage >= 1.0) {
                                    entity.damage(damage, player)

                                    val knockbackVector = entity.boundingBox.center.subtract(player.boundingBox.center).normalize()
                                        .multiply(config!!.getDouble("knockbackAmplifier")).multiply(player.velocity.length())
                                    val playerRatio = entity.boundingBox.volume / (entity.boundingBox.volume + player.boundingBox.volume)
                                    val entityRatio = 1.0 - playerRatio

                                    player.velocity = player.velocity.clone().subtract(knockbackVector.multiply(playerRatio))
                                    entity.velocity = entity.velocity.clone().add(knockbackVector.multiply(entityRatio))


                                    player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f)
                                }


                            }
                            if (entity == hitEnemy) {
                                isMoving.remove(player)
                                laser.stop()
                                cancel()
                                return
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L)
            }
        }
    }
}