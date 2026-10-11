package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely
import com.github.lupi13.limpi.quests.PerfectDistance
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Damageable
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.min

object ShieldStrikePlus : Ability(
    grade = Grade.EPIC,
    acquisition = AcquisitionType.QUEST,
    element = Element.NONE,
    displayName = Component.text("방패강타+", NamedTextColor.GRAY),
    codeName = "shield_strike_plus",
    material = Material.SHIELD,
    description = listOf(
        Component.text("방패강타", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("더 빠르고 멀리 돌진합니다. 돌진이", NamedTextColor.WHITE),
        Component.text("끝나는 시점에 부딪히거나, ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("을 처치하면", NamedTextColor.WHITE)),
        Component.text("쿨타임이 초기화됩니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(PerfectDistance.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("방패강타", NamedTextColor.GRAY)
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
            Component.text("지상에서 ", NamedTextColor.WHITE).append(Component.text("방패", NamedTextColor.YELLOW)).append(Component.text("를 든 상태로 빠르게 두 번 웅크려", NamedTextColor.WHITE)),
            Component.text("더 빠르고 멀리 돌진합니다. 부딪힌 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게", NamedTextColor.WHITE)),
            Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN)
                .append(Component.text("의 피해를 주고 넉백시킵니다.", NamedTextColor.WHITE)),
            Component.text("돌진이 끝나는 시점에 부딪히거나, ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("을 처치하면", NamedTextColor.WHITE)),
            Component.text("쿨타임(", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown") / 1000.0}", NamedTextColor.GREEN).append(Component.text("초", NamedTextColor.WHITE)))
                .append(Component.text(")이 초기화됩니다.", NamedTextColor.WHITE))
        )
    }

    val lastSneakTime: MutableMap<java.util.UUID, Long> = mutableMapOf()
    val lastStrikeTime: MutableMap<java.util.UUID, Long> = mutableMapOf()
    val vector: MutableMap<java.util.UUID, Vector> = mutableMapOf()

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (player.ability != this) return
        if (!(player as Entity).isOnGround || !event.isSneaking) return
        if (player.inventory.itemInMainHand.type != Material.SHIELD && player.inventory.itemInOffHand.type != Material.SHIELD) return
        val cooldown = config!!.getInt("cooldown")

        val last = lastSneakTime[player.uniqueId]
        if (last != null && System.currentTimeMillis() - last <= 300) { // 300ms 이내에 두 번 웅크리면
            val lastStrike = lastStrikeTime[player.uniqueId]
            if (lastStrike == null || System.currentTimeMillis() - lastStrike > cooldown) {
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
                    if (timer in 1..9) {
                        session.potion(player, PotionEffect(PotionEffectType.SLOWNESS, 2, 7, true, false, false))
                    }
                    if (timer == 10) {
                        vector[player.uniqueId] = player.location.direction.setY(0).normalizeSafely().multiply(velocity)
                    }
                    if (timer in 11..17) {
                        try {
                            player.velocity = vector[player.uniqueId]!!
                            player.world.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.6f, 2f)
                        } catch (ignored: Exception) {
                        }
                    }

                    // 충돌
                    if (timer in 11..22) {
                        for (entity in player.getNearbyEntities(4.0, 4.0, 4.0)) {
                            if (entity !is Damageable) continue
                            if (!entity.boundingBox.overlaps(player.boundingBox)) continue
                            if (entity == player) continue
                            if (!AbilityManager.isEnemy(player, entity)) continue

                            val perfectDistance = timer == 22
                            if (!com.github.lupi13.limpi.platform.AbilityDamage.hit(player, entity, codeName, damage)) continue
                            // The owner can die synchronously inside hit (e.g. guardian thorns), releasing this state.
                            val dash = vector[player.uniqueId] ?: break
                            entity.velocity = dash.clone().multiply(min(knockbackAmplifier / entity.boundingBox.volume.coerceAtLeast(1e-9), maxKnockback)).setY(0.3)
                            player.velocity = dash.clone().multiply(-0.1).setY(0.2)
                            val centerPosition = player.boundingBox.center.getMidpoint(entity.boundingBox.center).toLocation(player.world)
                            player.world.playSound(centerPosition, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.5f)
                            player.world.spawnParticle(Particle.CRIT, centerPosition, 30, 0.2, 0.2, 0.2, 1.0)
                            if (perfectDistance || entity.isDead) { // 쿨타임 초기화
                                lastStrikeTime.remove(player.uniqueId)
                                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
                            }

                            timer = 22 // 충돌 시 타이머를 22로 설정하여 반복문을 종료
                            break
                        }
                    }
                    if (timer >= 22) {
                        session.close()
                    }
                    timer += 1
                }
            } else {
                AbilityManager.showCooldown(player, cooldown, lastStrike)
            }
        }
        lastSneakTime[player.uniqueId] = System.currentTimeMillis()
    }

    override fun release(owner: java.util.UUID) { lastSneakTime.remove(owner); lastStrikeTime.remove(owner); vector.remove(owner) }
    override fun stop() { lastSneakTime.clear(); lastStrikeTime.clear(); vector.clear() }
}
