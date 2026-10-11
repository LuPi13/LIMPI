package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Damageable
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
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
) {
    override val relatedQuest: List<com.github.lupi13.limpi.quests.Quest> by lazy {
        listOf(com.github.lupi13.limpi.quests.PerfectDistance, com.github.lupi13.limpi.quests.FireStrike)
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("지상에서 ", NamedTextColor.WHITE).append(Component.text("방패", NamedTextColor.YELLOW)).append(Component.text("를 든 상태로 빠르게 두 번 웅크려", NamedTextColor.WHITE)),
            Component.text("전방으로 돌진합니다. 부딪힌 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게", NamedTextColor.WHITE)),
            Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN)
                .append(Component.text("의 피해를 주고 넉백시킵니다.", NamedTextColor.WHITE))
        )
    }


    val lastSneakTime: MutableMap<java.util.UUID, Long> = mutableMapOf()
    val lastStrikeTime: MutableMap<java.util.UUID, Long> = mutableMapOf()
    val vector : MutableMap<java.util.UUID, Vector> = mutableMapOf()

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val cooldown = config!!.getInt("cooldown")
        if (player.ability != this) return
        if (!(player as Entity).isOnGround || !event.isSneaking) return
        if ((player.inventory.itemInMainHand.type != Material.SHIELD) && player.inventory.itemInOffHand.type != Material.SHIELD) return

        if ((lastSneakTime[player.uniqueId] != null) && (System.currentTimeMillis() - lastSneakTime[player.uniqueId]!! <= 300)) { // 300ms 이내에 두 번 웅크리면
            if ((lastStrikeTime[player.uniqueId] == null) || (System.currentTimeMillis() - lastStrikeTime[player.uniqueId]!! > config!!.getInt("cooldown"))) { // 쿨타임이 지나면
                lastStrikeTime[player.uniqueId] = System.currentTimeMillis()
                val effect = com.github.lupi13.limpi.platform.AbilityRuntime.begin(player, codeName)
                if (!config!!.getBoolean("allowJump")) effect.attribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH, -1.0,
                    org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1)

                    val velocity = config!!.getDouble("velocity")
                    val damage = config!!.getDouble("damage")
                    val knockbackAmplifier = config!!.getDouble("knockbackAmplifier")
                    val maxKnockback = config!!.getDouble("maxKnockback")
                    var timer = 1
                effect.repeat { session ->
                        if (timer in 1..5) {
                            player.world.playSound(player.location, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, 0.5f)
                        }
                        if (timer in 1..14) {
                            session.potion(player, PotionEffect(PotionEffectType.SLOWNESS, 2, 7, true, false, false))
                        }
                        if (timer == 15) {
                            vector[player.uniqueId] = player.location.direction.setY(0).normalizeSafely().multiply(velocity)
                        }
                        if (timer in 16..20) {
                            try {
                                player.velocity = vector[player.uniqueId]!!
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
                                if (!AbilityManager.isEnemy(player, entity)) continue

                                val perfectDistance = timer == 25
                                val burning = player.fireTicks > 0
                                if (!com.github.lupi13.limpi.platform.AbilityDamage.hit(player, entity, codeName, damage)) continue
                                questTriggers(player, entity, perfectDistance, burning)
                                // The owner can die synchronously inside hit (e.g. guardian thorns), releasing this state.
                                val dash = vector[player.uniqueId] ?: break
                                entity.velocity = dash.clone().multiply(min(knockbackAmplifier / entity.boundingBox.volume.coerceAtLeast(1e-9), maxKnockback)).setY(0.3)
                                player.velocity = dash.clone().multiply(-0.1).setY(0.2)
                                val centerPosition = player.boundingBox.center.getMidpoint(entity.boundingBox.center).toLocation(player.world)
                                player.world.playSound(centerPosition, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.5f)
                                player.world.spawnParticle(Particle.CRIT, centerPosition, 30, 0.2, 0.2, 0.2, 1.0)

                                timer = 25 // 충돌 시 타이머를 25로 설정하여 반복문을 종료
                                break
                            }
                        }
                        if (timer >= 25) {
                            session.close()
                        }
                        timer += 1
                }
            }
            else {
                AbilityManager.showCooldown(player, cooldown, lastStrikeTime[player.uniqueId]!!)
            }
        }
        lastSneakTime[player.uniqueId] = System.currentTimeMillis()
    }
    /** 완벽한 거리조절(돌진 마지막 틱 충돌)과 불타는 돌진(불타는 상태로 처치) 퀘스트를 확인합니다. */
    private fun questTriggers(player: org.bukkit.entity.Player, target: Damageable, perfectDistance: Boolean, burning: Boolean) {
        val quests = com.github.lupi13.limpi.quests.QuestManager
        try {
            if (perfectDistance && !quests.isClearedQuest(player, com.github.lupi13.limpi.quests.PerfectDistance))
                quests.clearQuests(player, com.github.lupi13.limpi.quests.PerfectDistance)
            if (burning && target.isDead && !quests.isClearedQuest(player, com.github.lupi13.limpi.quests.FireStrike))
                quests.clearQuests(player, com.github.lupi13.limpi.quests.FireStrike)
        } catch (failure: Exception) {
            plugin.logger.log(java.util.logging.Level.WARNING, "Shield strike quest check failed for ${player.uniqueId}", failure)
        }
    }
    override fun release(owner: java.util.UUID) { lastSneakTime.remove(owner); lastStrikeTime.remove(owner); vector.remove(owner) }
    override fun stop() { lastSneakTime.clear(); lastStrikeTime.clear(); vector.clear() }

}
