package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.combat.DamageContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.util.Vector
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.cos

/** 백스탭 계열 공통: 적의 등 뒤에서 근접 공격하면 피해가 늘어납니다. */
abstract class BackStabVariant(
    grade: Grade,
    displayName: Component,
    codeName: String,
    description: List<Component>,
    acquisition: AcquisitionType = AcquisitionType.GACHA,
) : Ability(
    grade = grade,
    displayName = displayName,
    codeName = codeName,
    material = Material.GOLDEN_SWORD,
    description = description,
    acquisition = acquisition,
    needFile = true
) {
    protected fun backStabLines(): List<Component> = listOf(
        Component.text("적", NamedTextColor.RED)
            .append(Component.text("의 등 뒤 ", NamedTextColor.WHITE))
            .append(Component.text("${config!!.getDouble("back_angle")}", NamedTextColor.GREEN))
            .append(Component.text("°", NamedTextColor.WHITE))
            .append(Component.text(" 범위에서 근접 공격하면", NamedTextColor.WHITE)),
        Component.text("${config!!.getDouble("damage_multiplier")}", NamedTextColor.GREEN)
            .append(Component.text("배의 피해를 줍니다.", NamedTextColor.WHITE))
    )

    /** 강화한 공격 이벤트. MONITOR에서 최종 결과를 확인한 뒤 비웁니다. */
    private val pending: MutableSet<EntityDamageByEntityEvent> = Collections.newSetFromMap(IdentityHashMap())

    /** 공격자가 대상의 등 뒤([back_angle]° 원뿔, 수평 기준)에 있는지 확인합니다. */
    private fun behind(attacker: Player, target: LivingEntity): Boolean {
        val facing = horizontal(target.location.direction) ?: return false
        val halfAngle = Math.toRadians(config!!.getDouble("back_angle") / 2.0)
        val toAttacker = horizontal(attacker.location.toVector().subtract(target.location.toVector()))
            // 바로 위/아래에서 공격하면 위치로 판단할 수 없으므로 원본처럼 바라보는 방향이 같은지로 판단합니다.
            ?: return (horizontal(attacker.location.direction) ?: return false).dot(facing) >= cos(halfAngle)
        return toAttacker.dot(facing) <= -cos(halfAngle)
    }

    private fun horizontal(vector: Vector): Vector? {
        val flat = Vector(vector.x, 0.0, vector.z)
        val length = flat.length()
        return if (length.isFinite() && length > 0.01) flat.multiply(1.0 / length) else null
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBackStab(event: EntityDamageByEntityEvent) {
        if (DamageContext.current != null || event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (player.ability != this || target.world.uid != player.world.uid) return
        if (!behind(player, target)) return
        event.damage *= config!!.getDouble("damage_multiplier")
        player.world.playSound(player.location, Sound.ENTITY_BREEZE_HURT, SoundCategory.PLAYERS, 1f, 2f)
        pending.add(event)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onBackStabResult(event: EntityDamageByEntityEvent) {
        if (!pending.remove(event)) return
        if (event.isCancelled) return
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (event.finalDamage < target.health) return
        try {
            onBackStabKill(player, target)
        } catch (failure: Exception) {
            plugin.logger.log(java.util.logging.Level.WARNING, "Back stab kill handling failed for ${player.uniqueId}", failure)
        }
    }

    /** 백스탭 공격이 대상을 처치했을 때(피해 확정 직전) 호출됩니다. */
    protected open fun onBackStabKill(player: Player, target: LivingEntity) {}

    override fun stop() {
        super.stop()
        pending.clear()
    }
}

object BackStab : BackStabVariant(
    grade = Grade.RARE,
    displayName = Component.text("백스탭", NamedTextColor.GOLD),
    codeName = "back_stab",
    description = listOf(
        Component.text("적", NamedTextColor.RED)
            .append(Component.text("의 등 뒤에서 근접 공격하면", NamedTextColor.WHITE)),
        Component.text("더 큰 피해를 줍니다.", NamedTextColor.WHITE)
    )
) {
    override val details: List<Component> by settingsCached { backStabLines() }
}
