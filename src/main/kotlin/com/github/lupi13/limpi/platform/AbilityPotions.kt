package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.ability.PotionOwnership
import com.github.lupi13.limpi.ability.PotionSignature
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.potion.PotionEffect
import java.util.UUID

object AbilityPotions : Listener {
    private val ownership = PotionOwnership()
    private data class Mutation(val entity: UUID, val effect: PotionEffect?, val type: String, var event: EntityPotionEffectEvent? = null)
    private var mutation: Mutation? = null
    private fun PotionEffect.signature() = PotionSignature(amplifier, isAmbient, hasParticles(), hasIcon())

    fun apply(effect: AbilityEffect, target: LivingEntity, potion: PotionEffect) {
        check(Bukkit.isPrimaryThread())
        require(potion.duration > 0 && potion.amplifier >= 0 && !potion.type.isInstant)
        if (effect.scope.closed || !target.isValid || target.isDead) return
        val type = potion.type.key.toString(); val entity = target.uniqueId; val scope = effect.scope.id
        val wasOwned = ownership.owns(entity, type, scope)
        if (!ownership.acquire(entity, type, scope, potion.signature(), target.getPotionEffect(potion.type)?.signature())) return
        if (!wasOwned) effect.own {
            if (ownership.release(entity, type, scope, target.getPotionEffect(potion.type)?.signature())) {
                mutate(Mutation(entity, null, type)) { target.removePotionEffect(potion.type) }
            }
        }
        try {
            mutate(Mutation(entity, potion, type)) { target.addPotionEffect(potion) }
            if (target.getPotionEffect(potion.type)?.signature() != potion.signature()) ownership.external(entity, type)
        } catch (failure: Exception) {
            ownership.external(entity, type)
            throw failure
        }
    }

    private inline fun mutate(next: Mutation, action: () -> Unit) {
        val previous = mutation; mutation = next
        try { action() } finally { mutation = previous }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun identify(event: EntityPotionEffectEvent) {
        val expected = mutation ?: return
        if (expected.event == null && expected.entity == event.entity.uniqueId && expected.type == event.modifiedType.key.toString() &&
            expected.effect == event.newEffect) expected.event = event
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun changed(event: EntityPotionEffectEvent) {
        if (mutation?.event !== event) ownership.external(event.entity.uniqueId, event.modifiedType.key.toString())
    }

    fun clear() { ownership.clear(); mutation = null }
}
