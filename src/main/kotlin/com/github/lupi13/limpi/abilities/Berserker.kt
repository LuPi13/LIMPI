package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.combat.DamageContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

object Berserker : Ability(
    grade = Grade.EPIC,
    displayName = Component.text("광전사", NamedTextColor.DARK_RED),
    codeName = "berserker",
    material = Material.RED_DYE,
    description = listOf(
        Component.text("체력이 적을수록 근접 공격이 강해집니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("잃은 체력에 비례하여 근접 공격이 강해집니다.", NamedTextColor.WHITE),
            Component.text("체력이 1 남았을 때 최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("max_multiplier")}", NamedTextColor.GREEN))
                .append(Component.text("배의 피해를 줍니다.", NamedTextColor.WHITE))
        )
    }

    /** 체력이 가득 차면 1배, 체력 1 이하에서 최대 배율. 최대 체력이 바뀌어도 같은 비율로 계산합니다. */
    fun multiplier(health: Double, maxHealth: Double, maxMultiplier: Double): Double {
        if (!health.isFinite() || !maxHealth.isFinite() || maxHealth <= 1.0) return 1.0
        val lost = ((maxHealth - health) / (maxHealth - 1.0)).coerceIn(0.0, 1.0)
        return 1.0 + (maxMultiplier - 1.0) * lost
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        if (DamageContext.current != null || event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
        val player = event.damager as? Player ?: return
        if (event.entity !is LivingEntity || player.ability != this) return
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: return
        event.damage *= multiplier(player.health, maxHealth, config!!.getDouble("max_multiplier"))
    }
}
