package com.github.lupi13.limpi.combat

import java.util.UUID

enum class HitKind { ABILITY, CHAIN, MAGIC_CONVERSION, EXECUTION }
class HitReceipt { var accepted = false }
data class AbilityHit(val owner: UUID, val ability: String, val kind: HitKind, val chain: ChainBudget? = null,
                      val target: UUID? = null, val receipt: HitReceipt? = null, val knockback: Boolean = true)

/** Context exists only during the synchronous server damage call, never in retained event/player maps. */
object DamageContext {
    private val active = ThreadLocal<AbilityHit>()
    val current: AbilityHit? get() = active.get()
    fun <T> with(hit: AbilityHit, action: () -> T): T {
        val previous = active.get(); active.set(hit)
        try { return action() } finally { if (previous == null) active.remove() else active.set(previous) }
    }
}

/** One chain cannot hit the same entity twice or grow forever when probability/ratio are one. */
class ChainBudget(origin: UUID, private val maximum: Int = 16) {
    private val visited = mutableSetOf(origin)
    init { require(maximum in 1..256) }
    val hops: Int get() = visited.size - 1
    fun visited(target: UUID) = target in visited
    fun reserve(target: UUID, damage: Double): Boolean {
        if (!damage.isFinite() || damage <= 0 || hops >= maximum) return false
        return visited.add(target)
    }
}

object DamageNumbers {
    // Bukkit accepts Double but the vanilla damage path ultimately uses float arithmetic.
    const val MAXIMUM = Float.MAX_VALUE.toDouble() / 4.0
    fun bounded(value: Double): Double {
        require(value.isFinite() && value >= 0)
        return value.coerceAtMost(MAXIMUM)
    }
    fun execution(health: Double, absorption: Double): Double {
        require(health.isFinite() && health >= 0 && absorption.isFinite() && absorption >= 0)
        return bounded((health + absorption + 1).coerceAtMost(MAXIMUM) * 1024.0)
    }
    fun fixedAddition(base: Double, bonus: Double, reductions: Double, absorption: Double): Pair<Double, Double> {
        require(listOf(base, bonus, reductions, absorption).all(Double::isFinite))
        require(base >= 0 && bonus >= 0 && absorption >= 0)
        val next = bounded(base + bonus)
        val absorbed = minOf(absorption, (next + reductions).coerceAtLeast(0.0))
        return next to if (absorbed == 0.0) 0.0 else -absorbed
    }
}
