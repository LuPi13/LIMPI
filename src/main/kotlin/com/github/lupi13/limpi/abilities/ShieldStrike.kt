package com.github.lupi13.limpi.abilities

import io.papermc.paper.command.brigadier.argument.ArgumentTypes.world
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Damageable
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.min

object ShieldStrike : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("방패강타", NamedTextColor.GRAY),
    codeName = "shield_strike",
    material = Material.SHIELD,
    description = listOf(
        Component.text("지상에서 방패를 든 상태로 빠르게 두 번 웅크려", NamedTextColor.WHITE),
        Component.text("전방으로 돌진합니다. 부딪힌 ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("에게", NamedTextColor.WHITE)),
        Component.text("피해를 주고 넉백시킵니다.", NamedTextColor.WHITE)
    ),
    needFile = true
//    relatedQuest =
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("지상에서 방패를 든 상태로 빠르게 두 번 웅크려", NamedTextColor.WHITE),
            Component.text("전방으로 돌진합니다. 부딪힌 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게", NamedTextColor.WHITE)),
            Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN)
                .append(Component.text("의 피해를 주고 넉백시킵니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("cooldown", 10000)
            config?.set("velocity", 1.1)
            config?.set("damage", 8.0)
            config?.set("knockbackAmplifier", 1.3)
            config?.set("maxKnockback", 1.5)
            config?.set("allowJump", false)
        }
        saveConfig()
    }

    val lastSneakTime: MutableMap<Player, Long> = mutableMapOf()
    val lastStrikeTime: MutableMap<Player, Long> = mutableMapOf()
    val vector : MutableMap<Player, Vector> = mutableMapOf()

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val cooldown = config!!.getInt("cooldown")
        if (player.ability != this) return
        if (!(player as Entity).isOnGround || !event.isSneaking) return
        if ((player.inventory.itemInMainHand.type != Material.SHIELD) && player.inventory.itemInOffHand.type != Material.SHIELD) return

        if ((lastSneakTime[player] != null) && (System.currentTimeMillis() - lastSneakTime[player]!! <= 300)) { // 300ms 이내에 두 번 웅크리면
            if ((lastStrikeTime[player] == null) || (System.currentTimeMillis() - lastStrikeTime[player]!! > config!!.getInt("cooldown"))) { // 쿨타임이 지나면
                lastStrikeTime[player] = System.currentTimeMillis()
                if (!config!!.getBoolean("allowJump")) {
                    player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH)!!.baseValue = 0.0
                }

                object: BukkitRunnable() {
                    val velocity = config!!.getDouble("velocity")
                    val damage = config!!.getDouble("damage")
                    val knockbackAmplifier = config!!.getDouble("knockbackAmplifier")
                    val maxKnockback = config!!.getDouble("maxKnockback")
                    var timer = 1
                    override fun run() {
                        if (timer in 1..5) {
                            player.world.playSound(player.location, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, 0.5f)
                        }
                        if (timer in 1..14) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 2, 7, true, false, false))
                        }
                        if (timer == 15) {
                            vector[player] = player.location.direction.setY(0).normalize().multiply(velocity)
                        }
                        if (timer in 16..20) {
                            try {
                                player.velocity = vector[player]!!
                                player.world.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.6f, 2f)
                            }
                            catch (ignored: Exception) {
                            }
                        }

                        // 충돌
                        if (timer in 16..25) {
                            for (entity in player.getNearbyEntities(4.0, 4.0, 4.0)) {
                                if (entity !is Damageable) continue
                                if (!entity.boundingBox.overlaps(player.boundingBox)) continue
                                if (entity == player) continue
                                if (!AbilityManager().isEnemy(player, entity)) continue

                                entity.damage(damage, player)
                                entity.velocity = vector[player]!!.multiply(min(knockbackAmplifier / entity.boundingBox.volume, maxKnockback)).setY(0.3)
                                player.velocity = vector[player]!!.multiply(-0.1).setY(0.2)
                                val centerPosition = player.boundingBox.center.getMidpoint(entity.boundingBox.center).toLocation(player.world)
                                player.world.playSound(centerPosition, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.5f)
                                player.world.spawnParticle(Particle.CRIT, centerPosition, 30, 0.2, 0.2, 0.2, 1.0)

                                timer = 25 // 충돌 시 타이머를 25로 설정하여 반복문을 종료
                                break
                            }
                        }
                        if (timer >= 25) {
                            if (!config!!.getBoolean("allowJump")) {
                                player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH)!!.baseValue = player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH)!!.defaultValue
                            }
                            cancel()
                        }
                        timer += 1
                    }
                }.runTaskTimer(plugin, 0L, 1L)
            }
            else {
                val text = Component.text("남은 쿨타임: ", NamedTextColor.WHITE)
                    .append(Component.text(String.format("%.2f", (cooldown - (System.currentTimeMillis() - (lastStrikeTime[player] ?: 0L))) / 1000.0), NamedTextColor.RED))
                    .append(Component.text("초", NamedTextColor.WHITE))
                player.sendActionBar(text)
            }
        }
        lastSneakTime[player] = System.currentTimeMillis()
    }
}