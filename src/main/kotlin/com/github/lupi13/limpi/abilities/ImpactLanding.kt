package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object ImpactLanding : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("충격 피해 변환", NamedTextColor.GRAY),
    codeName = "impact_landing",
    material = Material.HEAVY_CORE,
    description = listOf(
        Component.text("낙하 피해를 주변 ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("에게", NamedTextColor.WHITE)),
        Component.text("피해를 주는 충격파로 변환합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){

    override val details: List<Component> by lazy {
        listOf(
            Component.text("낙하 피해를 무효화하고, 주변 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ",NamedTextColor.WHITE)),
            Component.text("피해를 주는 충격파로 변환합니다.", NamedTextColor.WHITE),
            Component.text("충격파의 피해는 기존 낙하 피해에 비례하고,", NamedTextColor.WHITE),
            Component.text("거리에 반비례합니다.", NamedTextColor.WHITE)
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("dampingRatio", 2.0)
            config?.set("minimumDamage", 0.5)
        }
        saveConfig()
    }

    /**
     * 가우스분포를 응용한 충격파 피해 계산 함수
     * @param damage 원래 낙하 피해
     * @param distance 충격파가 미치는 거리
     * @return shock damage * e^(-dampingRatio * distance^2 / damage)
     */
    private fun calculateShockwaveDamage(damage: Double, distance: Double): Double {
        val dampingRatio = config!!.getDouble("dampingRatio")
        return damage * exp(-dampingRatio * distance * distance / damage)
    }


    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability != this) return

        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        event.isCancelled = true
        val damage = event.damage

        player.world.playSound(player.location, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, (damage / 8.0).toFloat(), (8.0 / damage).toFloat())

        val entities = player.getNearbyEntities(damage*2, damage*2, damage*2)

        for (entity in entities) {
            if (entity !is Damageable) continue
            if (entity == player) continue
            if (!AbilityManager().isEnemy(player, entity)) continue

            val distance = player.location.distance(entity.location)
            val shockDamage = calculateShockwaveDamage(damage, distance)
            if (shockDamage < config!!.getDouble("minimumDamage")) continue // 최소 피해량 설정

            entity.damage(shockDamage, player)
        }

        // 파티클 효과
        for (r in 1..(damage / 2).toInt()) {
            for (theta in 0 until 360 step 10) {
                val angle = Math.toRadians(theta.toDouble() + Random.nextDouble(-5.0, 5.0))
                val x = (r + Random.nextDouble(-0.5, 0.5)) * cos(angle)
                val z = (r + Random.nextDouble(-0.5, 0.5)) * sin(angle)
                val loc = player.location.clone().add(x, 0.0, z)

                val shock = calculateShockwaveDamage(damage, loc.distance(player.location))
                player.world.spawnParticle(
                    Particle.SPIT,
                    player.location,
                    0,
                    x, 0.1, z,
                    shock / 10.0
                )
                while (Random.nextDouble() <= (shock / (damage * 1.1))) {
                    player.world.spawnParticle(
                        Particle.SMOKE,
                        loc.clone(),
                        0,
                        Random.nextDouble(-0.1, 0.1), 1.0, Random.nextDouble(-0.1, 0.1),
                        shock / (damage * 2.0)
                    )
                }

                while (Random.nextDouble() <= (shock / (damage * 3.0))) {
                    player.world.spawnParticle(
                        Particle.FLAME,
                        loc.clone(),
                        0,
                        Random.nextDouble(-0.1, 0.1), 1.0, Random.nextDouble(-0.1, 0.1),
                        shock / (damage * 2.0)
                    )
                }

            }
        }
    }
}