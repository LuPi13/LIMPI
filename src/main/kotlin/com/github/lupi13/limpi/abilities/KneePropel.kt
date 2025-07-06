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
import org.bukkit.scheduler.BukkitRunnable
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

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("baseLevel", 0)
            config?.set("increment", 1)
            config?.set("increaseTick", 10)
            config?.set("maxStrength", 10)
        }
        saveConfig()
    }


    val timer = mutableMapOf<Player, Int>()
    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (player.ability != this) return

        // 지면에서 웅크리기
        if (event.isSneaking && (player as LivingEntity).isOnGround) {
            timer[player] = 1

            object: BukkitRunnable() {
                val baseLevel = config!!.getInt("baseLevel")
                val increment = config!!.getInt("increment")
                val increaseTick = config!!.getInt("increaseTick")
                val maxStrength = config!!.getInt("maxStrength")
                val maxTick = maxStrength * increaseTick / increment
                override fun run() {
                    val strength = min(baseLevel + increment * (timer[player]!! / increaseTick), maxStrength)

                    if (strength > 0) {
                        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 2, strength - 1, true, false, false))
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
                    var message = Component.text("Jump Boost ${min(timer[player]!! / increaseTick, maxStrength / increment)} [", color)
                    val gaugeTimer = if (timer[player]!! >= maxTick) {
                        60
                    } else {
                        timer[player]!! % increaseTick
                    }

                    for (i in 0 until 60) {
                        message = if (i < gaugeTimer * 60 / increaseTick) {
                            message.append(Component.text("|", color))
                        } else {
                            message.append(Component.text("·", NamedTextColor.GRAY))
                        }
                    }
                    message = message.append(Component.text("]", color))
                    player.sendActionBar(message)

                    if (timer[player] != -1) {
                        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, min(timer[player]!! / (maxTick * 2.0) + 0.3, 0.8).toFloat(), min(timer[player]!! / (maxTick / 1.5) + 0.5, 2.0).toFloat())
                    }

                    if (timer[player] == -1) {
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
                        timer[player] = -1
                        cancel()
                    }
                    if (player.isDead) {
                        timer[player] = -1
                        cancel()
                    }
                    timer[player] = timer[player]!! + 1
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }
        else {
            timer[player] = -1
        }
    }


    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.ability != this) return

        if (!(player as LivingEntity).isOnGround) {
            timer[player] = -1
        }
    }
}