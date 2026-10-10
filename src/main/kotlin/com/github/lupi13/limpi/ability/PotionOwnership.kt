package com.github.lupi13.limpi.ability

import java.util.UUID

data class PotionSignature(val amplifier: Int, val ambient: Boolean, val particles: Boolean, val icon: Boolean)
data class PotionLease(val scope: UUID, val signature: PotionSignature)

/** An external same-type effect is never overwritten or removed by a temporary ability lease. */
class PotionOwnership {
    private val leases = mutableMapOf<Pair<UUID, String>, PotionLease>()
    fun acquire(entity: UUID, type: String, scope: UUID, desired: PotionSignature, current: PotionSignature?): Boolean {
        val key = entity to type
        val lease = leases[key]
        if (current != null && (lease?.scope != scope || lease.signature != current)) return false
        if (lease != null && lease.scope != scope) return false
        leases[key] = PotionLease(scope, desired)
        return true
    }
    fun release(entity: UUID, type: String, scope: UUID, current: PotionSignature?): Boolean {
        val key = entity to type; val lease = leases[key] ?: return false
        if (lease.scope != scope) return false
        leases.remove(key)
        return current == lease.signature
    }
    fun external(entity: UUID, type: String) { leases.remove(entity to type) }
    fun owns(entity: UUID, type: String, scope: UUID) = leases[entity to type]?.scope == scope
    fun clear() { leases.clear() }
}
