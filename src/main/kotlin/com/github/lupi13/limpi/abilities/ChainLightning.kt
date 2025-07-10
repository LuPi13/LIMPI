package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

object ChainLightning : Ability(
    grade = Grade.EPIC,
    element = Element.ELECTRIC,
    displayName = Component.text("연쇄 번개", NamedTextColor.YELLOW),
    codeName = "chain_lightning",
    material = Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
    description = listOf(
        Component.text("공격이 주변 적에게도 피해를 입힙니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){

    override val details: List<Component> by lazy {
        listOf(
            Component.text("가한 피해의 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("damageRatio") * 100}%", NamedTextColor.GREEN))
                .append(Component.text(" 데미지가 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("probability") * 100}%", NamedTextColor.GREEN))
                .append(Component.text("확률로", NamedTextColor.WHITE)),
            Component.text("주변 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("radius")}", NamedTextColor.GREEN))
                .append(Component.text("칸 내 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게도 피해를 입힙니다.", NamedTextColor.WHITE)),
            Component.text("연쇄 번개 효과가 또 다른 연쇄 번개를 일으킬 수 있습니다.", NamedTextColor.WHITE)
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("damageRatio", 0.9)
            config?.set("probability", 0.8)
            config?.set("radius", 5.0)
        }
        saveConfig()
    }


    @EventHandler
    fun onAttack(event: EntityDamageByEntityEvent) {
//        if (event.damager !is Player || (event.damager as Player).ability != this) return
        if (event.damager !is Player && event.damager !is AbstractArrow) return
        val player: Player
        if (event.damager is Player) {
            player = event.damager as Player
        }
        else {
            if ((event.damager as AbstractArrow).shooter !is Player) return
            player = (event.damager as AbstractArrow).shooter as Player
        }
        if (player.ability != this) return
        if (event.entity !is Damageable) return

        val damage = event.damage * config!!.getDouble("damageRatio")
        val probability = config!!.getDouble("probability")
        val radius = config!!.getDouble("radius")

        if (Random.nextDouble() > probability) return

        val target = event.entity as Damageable


        val entities = target.getNearbyEntities(radius*2, radius*2, radius*2)
        entities.shuffle()

        var newTarget: Damageable? = null
        for (entity in entities) {
            if (entity !is Damageable) continue
            if (entity == target || entity == player) continue
            if (entity.location.distance(target.location) > radius) continue
            if (entity is LivingEntity && entity.noDamageTicks > 10) continue

            newTarget = entity
            break
        }

        if (newTarget == null) return

        object: BukkitRunnable() {
            override fun run() {
                //전기 효과
                val from = target.boundingBox.center.toLocation(target.world)
                val to = newTarget.boundingBox.center.toLocation(newTarget.world)
                val vector = to.clone().subtract(from.clone()).toVector().normalize()
                val distance = from.distance(to)

                for (i in 0..(distance*10).toInt()) {
                    val loc = from.clone().add(vector.clone().multiply(i/10.0))
                    loc.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, loc, 1, 0.0, 0.0, 0.0, 0.01)
                }
                to.world.playSound(to, Sound.ENTITY_BEE_DEATH, 0.7f, 2f)

                newTarget.damage(damage, player)
            }
        }.runTaskLater(plugin,4L)
    }
}