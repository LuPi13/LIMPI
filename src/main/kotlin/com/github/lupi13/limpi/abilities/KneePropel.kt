package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.min

object KneePropel : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("무릎을 꿇은 이유", NamedTextColor.YELLOW),
    codeName = "knee_propel",
    material = Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
    description = listOf(
        Component.text("길게 웅크렸다가 점프하면 추진력을 얻어", NamedTextColor.WHITE),
        Component.text("더욱 높게 도약합니다.", NamedTextColor.WHITE),
    ),
    needFile = true
) {


    private val charging = mutableMapOf<java.util.UUID, com.github.lupi13.limpi.platform.AbilityEffect>()
    val timer = mutableMapOf<java.util.UUID, Int>()
    @EventHandler(ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (player.ability != this) return

        // 지면에서 웅크리기
        if (event.isSneaking && (player as LivingEntity).isOnGround) {
            charging.remove(player.uniqueId)?.close()
            timer[player.uniqueId] = 1

            object: com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
                init {
                    charging[player.uniqueId] = effect
                    own { if (charging[player.uniqueId] === effect) { charging.remove(player.uniqueId); timer.remove(player.uniqueId) } }
                }
                val baseLevel = config!!.getInt("baseLevel")
                val increment = config!!.getInt("increment")
                val increaseTick = config!!.getInt("increaseTick")
                val maxStrength = config!!.getInt("maxStrength")
                // 표시(게이지/소리)는 main과 같은 내림 기준, 타이머 상한은 최대 강도에 도달하는 올림 기준을 쓴다.
                val maxTick = (maxStrength.toLong() * increaseTick / increment).toInt()
                val timerCap = ((maxStrength.toLong() * increaseTick + increment - 1) / increment).toInt()
                override fun tick() {
                    val current = timer[player.uniqueId] ?: -1
                    val strength = (baseLevel.toLong() + increment.toLong() * (current / increaseTick)).coerceAtMost(maxStrength.toLong()).toInt()

                    if (strength > 0) {
                        effect.potion(player, PotionEffect(PotionEffectType.JUMP_BOOST, 2, strength - 1, true, false, false))
                    }

                    val color = when (strength * 10 / maxStrength) {
                        in -127..0 -> NamedTextColor.GRAY
                        1 -> NamedTextColor.DARK_PURPLE
                        2 -> NamedTextColor.LIGHT_PURPLE
                        3 -> NamedTextColor.BLUE
                        4 -> NamedTextColor.AQUA
                        5 -> NamedTextColor.DARK_GREEN
                        6 -> NamedTextColor.GREEN
                        7 -> NamedTextColor.YELLOW
                        8 -> NamedTextColor.GOLD
                        9 -> NamedTextColor.RED
                        else -> NamedTextColor.DARK_RED
                    }
//                    [||||||····] 60칸 분할
                    var message = Component.text("Jump Boost ${min(current / increaseTick, maxStrength / increment)} [", color)
                    val filled = if (current >= maxTick) 60 else ((current % increaseTick).toLong() * 60 / increaseTick).toInt()

                    for (i in 0 until 60) {
                        message = if (i < filled) {
                            message.append(Component.text("|", color))
                        } else {
                            message.append(Component.text("·", NamedTextColor.GRAY))
                        }
                    }
                    message = message.append(Component.text("]", color))
                    player.sendActionBar(message)

                    if (current != -1) {
                        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, min(current / (maxTick * 2.0) + 0.3, 0.8).toFloat(), min(current / (maxTick / 1.5) + 0.5, 2.0).toFloat())
                    }

                    if (current < 0) {
                        player.getPotionEffect(PotionEffectType.JUMP_BOOST)?.let {
                            if (!(player as LivingEntity).isOnGround && (it.amplifier >= 0)) {
                                player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8F, 2F)
                                player.playSound(player.location, Sound.ITEM_TRIDENT_THROW, 0.8F, 0.7F)
                                player.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8F, 2F)
                                player.sendActionBar(Component.text("JUMP!!!", NamedTextColor.YELLOW, TextDecoration.BOLD))
                            } else {
                                player.playSound(player.location, Sound.ENTITY_VEX_HURT, 1f, 0.5f)
                                player.sendActionBar(Component.text("CANCELLED", NamedTextColor.GRAY, TextDecoration.BOLD))
                            }
                        }
                        cancel()
                        return
                    }

                    timer[player.uniqueId] = (current.toLong() + 1).coerceAtMost(timerCap.toLong()).toInt()
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }
        else {
            timer[player.uniqueId] = -1
        }
    }


    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.ability != this) return

        if (!(player as LivingEntity).isOnGround) {
            timer[player.uniqueId] = -1
        }
    }
    override fun release(owner: java.util.UUID) { charging.remove(owner)?.close(); timer.remove(owner) }
    override fun stop() { charging.values.toList().forEach { it.close() }; charging.clear(); timer.clear() }

}
