package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely

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


    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        if (event.finalDamage <= 0) return
        val context = com.github.lupi13.limpi.combat.DamageContext.current
        if (context != null && context.kind != com.github.lupi13.limpi.combat.HitKind.CHAIN) return
        val player = when (val attacker = event.damager) {
            is Player -> attacker
            is AbstractArrow -> attacker.shooter as? Player
            else -> null
        } ?: return
        if (player.ability != this || !player.isOnline || player.isDead) return
        if (context != null && (context.owner != player.uniqueId || context.ability != codeName)) return
        val target = event.entity as? Damageable ?: return
        if (!AbilityManager.isEnemy(player, target)) return
        val ratio = config!!.getDouble("damageRatio")
        val probability = config!!.getDouble("probability")
        val radius = config!!.getDouble("radius")
        val maximum = config!!.getInt("maximumChainHops")
        require(ratio.isFinite() && ratio >= 0 && probability in 0.0..1.0 && radius.isFinite() && radius > 0 && maximum in 1..256)
        if (ratio == 0.0 || Random.nextDouble() >= probability) return
        val damage = (event.finalDamage * ratio).coerceAtMost(com.github.lupi13.limpi.combat.DamageNumbers.MAXIMUM)
        val budget = context?.chain ?: com.github.lupi13.limpi.combat.ChainBudget(target.uniqueId, maximum)
        val next = target.getNearbyEntities(radius * 2, radius * 2, radius * 2).shuffled().filterIsInstance<Damageable>()
            .firstOrNull { candidate ->
                candidate.isValid && !candidate.isDead && candidate.world.uid == player.world.uid &&
                    !budget.visited(candidate.uniqueId) && candidate.location.distanceSquared(target.location) <= radius * radius &&
                    (candidate !is LivingEntity || candidate.noDamageTicks <= 10) && AbilityManager.isEnemy(player, candidate)
            } ?: return
        if (!budget.reserve(next.uniqueId, damage)) return
        val from = target.boundingBox.center.toLocation(target.world)
        object : com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
            override fun tick() {
                if (!next.isValid || next.isDead || next.world.uid != player.world.uid || !AbilityManager.isEnemy(player, next)) return
                val to = next.boundingBox.center.toLocation(next.world)
                val delta = to.toVector().subtract(from.toVector())
                val distance = delta.length()
                if (distance > 1.0e-8) {
                    val direction = delta.normalizeSafely()
                    for (i in 0..(distance.coerceAtMost(radius * 2) * 10).toInt()) {
                        to.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, from.clone().add(direction.clone().multiply(i / 10.0)), 1, 0.0, 0.0, 0.0, 0.01)
                    }
                }
                to.world.playSound(to, Sound.ENTITY_BEE_DEATH, 0.7f, 2f)
                com.github.lupi13.limpi.platform.AbilityDamage.hit(player, next, codeName, damage,
                    com.github.lupi13.limpi.combat.HitKind.CHAIN, budget)
            }
        }.runTaskLater(plugin, 4L)
    }
}
