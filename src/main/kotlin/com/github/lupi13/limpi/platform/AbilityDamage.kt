package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.combat.*
import org.bukkit.Bukkit
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

object AbilityDamage : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun observe(event: EntityDamageEvent) {
        val context = DamageContext.current ?: return
        if (context.target == event.entity.uniqueId) context.receipt?.accepted = !event.isCancelled && event.finalDamage > 0
    }
    fun hit(player: Player, target: Damageable, ability: String, amount: Double,
            kind: HitKind = HitKind.ABILITY, chain: ChainBudget? = null, self: Boolean = false, respectTargets: Boolean = true): Boolean {
        check(Bukkit.isPrimaryThread())
        val damage = DamageNumbers.bounded(amount)
        if (damage == 0.0 || !player.isOnline || player.isDead || FileManager.delivery.blocked(player.uniqueId) ||
            !target.isValid || target.isDead || target.world.uid != player.world.uid) return false
        if (target.uniqueId == player.uniqueId) { if (!self) return false }
        else if (respectTargets && !AbilityManager.isEnemy(player, target)) return false
        val type = if (kind == HitKind.MAGIC_CONVERSION || kind == HitKind.EXECUTION) DamageType.MAGIC else DamageType.PLAYER_ATTACK
        val source = DamageSource.builder(type).withCausingEntity(player).withDirectEntity(player).build()
        val receipt = HitReceipt()
        DamageContext.with(AbilityHit(player.uniqueId, ability, kind, chain, target.uniqueId, receipt)) { target.damage(damage, source) }
        return receipt.accepted
    }

    /** Add armor-independent bonus to the same cancellable attack, preserving its original reductions. */
    @Suppress("DEPRECATION")
    fun addFixed(event: EntityDamageEvent, amount: Double, target: Damageable) {
        if (event.isCancelled) return
        val reductions = DamageModifier.entries.filter { it != DamageModifier.BASE && it != DamageModifier.ABSORPTION && event.isApplicable(it) }
            .sumOf { event.getDamage(it) }
        val (base, absorption) = DamageNumbers.fixedAddition(event.damage, amount, reductions, target.absorptionAmount)
        event.setDamage(DamageModifier.BASE, base)
        if (event.isApplicable(DamageModifier.ABSORPTION)) event.setDamage(DamageModifier.ABSORPTION, absorption)
    }
}
