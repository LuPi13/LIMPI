package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.DoEndermansDreamOfFreeingEnd
import com.github.lupi13.limpi.quests.ExpAbsorber
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Damageable
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.random.Random

object JudgementRay : Ability(
    grade = Grade.MYTHIC,
    element = Element.MAGIC,
    displayName = Component.text("심", NamedTextColor.RED)
        .append(Component.text("판", NamedTextColor.GREEN))
        .append(Component.text("의", NamedTextColor.BLUE))
        .append(Component.text(" 빛", NamedTextColor.WHITE)),
    codeName = "judgement_ray",
    material = Material.LIGHT,
    description = listOf(
        Component.text("", NamedTextColor.WHITE)
            .append(Component.text("밸런스 붕괴", NamedTextColor.YELLOW))
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("더이상 1회용이 아니며, ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("이 아닌 개체에게는", NamedTextColor.WHITE)),
        Component.text("회복을 부여합니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    restrictedSlot = 40
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(DoEndermansDreamOfFreeingEnd.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("")
                .append(Component.text("밸런스 붕괴", NamedTextColor.YELLOW))
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
            Component.text("적", NamedTextColor.RED)
                .append(Component.text("이 아닌 개체에게 체력을 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("heal")}", NamedTextColor.GREEN))
                .append(Component.text("회복시킵니다.", NamedTextColor.WHITE)),
            Component.text("해당 능력은 더이상 1회용이 아닙니다.", NamedTextColor.YELLOW)
        )
    }


    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("shootingDelayTicks", 60)
            config?.set("damage", 256.0)
            config?.set("heal", 128.0)
            config?.set("velocity", 3.0)
            config?.set("accelerateTimeTicks", 20)
            config?.set("size", 4.0)
            config?.set("lifeTimeTicks", 200)
        }
        saveConfig()
    }


    fun getRailGunItem(): ItemStack {
        val item = ItemStack(Material.ECHO_SHARD)
        val meta = item.itemMeta
        meta.itemName(Component.text("심", NamedTextColor.RED)
        .append(Component.text("판", NamedTextColor.GREEN))
        .append(Component.text("의", NamedTextColor.BLUE))
        .append(Component.text(" 빛", NamedTextColor.WHITE)))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 레일건을 사격합니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta
        return item
    }


    override val activeItem: ItemStack by lazy {
        getRailGunItem()
    }

    @EventHandler
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this) return

        if (player.getCooldown(material) > 0) {
            AbilityManager().showCooldown(player, material)
            return
        }

        val shootingDelay = config!!.getInt("shootingDelayTicks")
        val damage = config!!.getDouble("damage")
        val heal = config!!.getDouble("heal")
        val velocity = config!!.getDouble("velocity")
        val accelerationTime = config!!.getDouble("accelerateTimeTicks")
        val size = config!!.getDouble("size")
        val lifeTimeTicks = config!!.getInt("lifeTimeTicks")

        player.setCooldown(material, shootingDelay + 1)

        event.isCancelled = true


        object: BukkitRunnable() {
            val lastMovementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED)!!.baseValue
            val lastGravity = player.getAttribute(Attribute.GRAVITY)!!.baseValue
            var ticks = 0
            val fixedLocation = player.location
            val vector = player.location.direction
            val world = player.location.world
            val y0vector = Vector(vector.x, 0.0, vector.z).normalize()
            var perpendicular = if (y0vector.length() <= 0.01) {
                Vector(1.0, 0.0, 0.0)
            } else {
                y0vector.clone().crossProduct(vector).normalize().multiply(size / 2.0)
            }

            val rail = world.spawnEntity(player.eyeLocation.add(player.location.direction.multiply(3.0).subtract(Vector(0.0, size / 2.0, 0.0))), EntityType.INTERACTION) as Interaction
            init {
                rail.setGravity(false)
                rail.isInvulnerable = true
                rail.isPersistent = true
                rail.interactionHeight = size.toFloat()
                rail.interactionWidth = size.toFloat()
                rail.location.direction = vector

            }

            override fun run() {
                // initial
                if (ticks == 0) {
                    player.getAttribute(Attribute.MOVEMENT_SPEED)!!.baseValue = 0.0
                    player.getAttribute(Attribute.GRAVITY)!!.baseValue = 0.0
                }

                // shooting
                if (ticks <= shootingDelay) {
                    player.teleport(fixedLocation)
                    if (ticks % 5 == 0) world.playSound(fixedLocation, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, ticks / (shootingDelay / 1.5f) + 0.5f)

                    if (player.isDead || !player.isOnline || player.ability != this@JudgementRay) {
                        rail.remove()
                        cancel()
                        return
                    }
                }
                perpendicular.rotateAroundAxis(vector, Math.PI / 12.0)

                // rotating effect
                val redStart = 1
                val yellowStart = (shootingDelay * (1.0 / 8.0)).toInt()
                val greenStart = (shootingDelay * (2.0 / 8.0)).toInt()
                val cyanStart = (shootingDelay * (3.0 / 8.0)).toInt()
                val blueStart = (shootingDelay * (4.0 / 8.0)).toInt()
                val purpleStart = (shootingDelay * (5.0 / 8.0)).toInt()
                if (ticks >= redStart) {
                    val dustOption = Particle.DustOptions(Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(50, 70), Random.nextInt(50, 70)), 1.0f)
                    val redLocation = rail.boundingBox.center.toLocation(world).add(perpendicular)
                    world.spawnParticle(Particle.DUST, redLocation, (ticks - redStart).coerceAtMost(10), 0.2, 0.2, 0.2, 0.1, dustOption, true)
                }
                if (ticks >= yellowStart) {
                    val dustOption = Particle.DustOptions(Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(225, 255), Random.nextInt(50, 70)), 1.0f)
                    val yellowLocation = rail.boundingBox.center.toLocation(world).add(perpendicular.rotateAroundAxis(vector, Math.PI / 3.0))
                    world.spawnParticle(Particle.DUST, yellowLocation, (ticks - yellowStart).coerceAtMost(10), 0.2, 0.2, 0.2, 0.1, dustOption, true)
                }
                if (ticks >= greenStart) {
                    val dustOption = Particle.DustOptions(Color.fromRGB(Random.nextInt(50, 70), Random.nextInt(225, 255), Random.nextInt(50, 70)), 1.0f)
                    val greenLocation = rail.boundingBox.center.toLocation(world).add(perpendicular.rotateAroundAxis(vector, Math.PI / 3.0))
                    world.spawnParticle(Particle.DUST, greenLocation, (ticks - greenStart).coerceAtMost(10), 0.2, 0.2, 0.2, 0.1, dustOption, true)
                }
                if (ticks >= cyanStart) {
                    val dustOption = Particle.DustOptions(Color.fromRGB(Random.nextInt(50, 70), Random.nextInt(225, 255), Random.nextInt(225, 255)), 1.0f)
                    val cyanLocation = rail.boundingBox.center.toLocation(world).add(perpendicular.rotateAroundAxis(vector, Math.PI / 3.0))
                    world.spawnParticle(Particle.DUST, cyanLocation, (ticks - cyanStart).coerceAtMost(10), 0.2, 0.2, 0.2, 0.1, dustOption, true)
                }
                if (ticks >= blueStart) {
                    val dustOption = Particle.DustOptions(Color.fromRGB(Random.nextInt(50, 70), Random.nextInt(50, 70), Random.nextInt(225, 255)), 1.0f)
                    val blueLocation = rail.boundingBox.center.toLocation(world).add(perpendicular.rotateAroundAxis(vector, Math.PI / 3.0))
                    world.spawnParticle(Particle.DUST, blueLocation, (ticks - blueStart).coerceAtMost(10), 0.2, 0.2, 0.2, 0.1, dustOption, true)
                }
                if (ticks >= purpleStart) {
                    val dustOption = Particle.DustOptions(Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(50, 70), Random.nextInt(225, 255)), 1.0f)
                    val purpleLocation = rail.boundingBox.center.toLocation(world).add(perpendicular.rotateAroundAxis(vector, Math.PI / 3.0))
                    world.spawnParticle(Particle.DUST, purpleLocation, (ticks - purpleStart).coerceAtMost(10), 0.2, 0.2, 0.2, 0.1, dustOption, true)
                }

                // shooting
                if (ticks == shootingDelay) {
                    player.getAttribute(Attribute.MOVEMENT_SPEED)!!.baseValue = lastMovementSpeed
                    player.getAttribute(Attribute.GRAVITY)!!.baseValue = lastGravity
                }
                if (ticks >= shootingDelay) {
                    rail.teleport(rail.location.add(vector.clone().multiply(((ticks - shootingDelay) * velocity / accelerationTime).coerceAtMost(velocity))))
                    world.playSound(rail.location, Sound.ENTITY_ZOMBIE_CONVERTED_TO_DROWNED, 0.8f, 0.5f)
                    world.playSound(rail.location, Sound.ENTITY_HUSK_CONVERTED_TO_ZOMBIE, 0.8f, 0.5f)
                    val whiteDust = Particle.DustOptions(Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(225, 255), Random.nextInt(225, 255)), 4f)
                    val blackDust = Particle.DustOptions(Color.fromRGB(Random.nextInt(0, 30), Random.nextInt(0, 30), Random.nextInt(0, 30)), 2f)
                    world.spawnParticle(Particle.DUST, rail.boundingBox.center.toLocation(world), ((ticks - shootingDelay) / accelerationTime).toInt().coerceAtMost(50), size / 6.0, size / 6.0, size / 6.0, 1.0, whiteDust, true)
                    world.spawnParticle(Particle.DUST, rail.boundingBox.center.toLocation(world), ((ticks - shootingDelay) / accelerationTime).toInt().coerceAtMost(20), size / 6.0, size / 6.0, size / 6.0, 1.0, blackDust, true)


                    // damage
                    val entities = world.getNearbyEntities(rail.boundingBox.center.toLocation(world), size * 2, size * 2, size * 2)
                    for (entity in entities) {
                        if (entity !is Damageable) continue
                        if (entity == player || entity == rail) continue
                        if (!entity.boundingBox.overlaps(rail.boundingBox)) continue


                        if (AbilityManager().isEnemy(player, entity)) {
                            entity.damage(damage, player)
                        }
                        else {
                            entity.heal(heal)
                        }
                    }
                }

                // remove after lifeTime
                if (ticks >= shootingDelay + lifeTimeTicks) {
                    rail.remove()
                    cancel()
                }

                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}