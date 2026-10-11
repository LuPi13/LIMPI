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
    /** 넉백 없는 능력 피해: Paper/Bukkit 넉백 이벤트를 모두 취소합니다. 진행 중인 피해 호출의 대상에게만 적용됩니다. */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun suppressKnockback(event: io.papermc.paper.event.entity.EntityKnockbackEvent) {
        if (suppressesKnockback(event.entity.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun suppressBukkitKnockback(event: org.bukkit.event.entity.EntityKnockbackEvent) {
        if (suppressesKnockback(event.entity.uniqueId)) event.isCancelled = true
    }

    private fun suppressesKnockback(entity: java.util.UUID): Boolean {
        val context = DamageContext.current ?: return false
        return !context.knockback && context.target == entity
    }

    /**
     * 능력 피해를 줍니다. [knockback]이 false면 이 피해 호출 안에서 대상에게 생기는 넉백 이벤트를 모두 취소해
     * 대상을 밀지 않습니다 (속도를 덮어쓰지 않으므로 대상의 원래 움직임은 유지됩니다).
     */
    fun hit(player: Player, target: Damageable, ability: String, amount: Double,
            kind: HitKind = HitKind.ABILITY, chain: ChainBudget? = null, self: Boolean = false, respectTargets: Boolean = true,
            knockback: Boolean = true): Boolean {
        check(Bukkit.isPrimaryThread())
        val damage = DamageNumbers.bounded(amount)
        if (damage == 0.0 || !player.isOnline || player.isDead || FileManager.delivery.blocked(player.uniqueId) ||
            !target.isValid || target.isDead || target.world.uid != player.world.uid) return false
        if (target.uniqueId == player.uniqueId) { if (!self) return false }
        else if (respectTargets && !AbilityManager.isEnemy(player, target)) return false
        val type = if (kind == HitKind.MAGIC_CONVERSION || kind == HitKind.EXECUTION) DamageType.MAGIC else DamageType.PLAYER_ATTACK
        val source = DamageSource.builder(type).withCausingEntity(player).withDirectEntity(player).build()
        val receipt = HitReceipt()
        DamageContext.with(AbilityHit(player.uniqueId, ability, kind, chain, target.uniqueId, receipt, knockback)) { target.damage(damage, source) }
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
