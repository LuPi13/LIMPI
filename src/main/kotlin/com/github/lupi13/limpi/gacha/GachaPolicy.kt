package com.github.lupi13.limpi.gacha

import java.math.BigDecimal
import java.math.RoundingMode

enum class DrawGrade { LEGENDARY, EPIC, TROLL, RARE, COMMON }
data class PityCurve(val base: Double, val increment: Double, val increaseAttempt: Long) {
    init { require(base.isFinite() && base in 0.0..1.0); require(increment.isFinite() && increment >= 0); require(increaseAttempt > 0) }
    fun probability(failures: Long): Double {
        require(failures >= 0)
        val steps = BigDecimal.valueOf(failures).subtract(BigDecimal.valueOf(increaseAttempt)).add(BigDecimal.TWO).max(BigDecimal.ZERO)
        return BigDecimal.valueOf(base).add(BigDecimal.valueOf(increment).multiply(steps)).min(BigDecimal.ONE).toDouble()
    }
    /** Attempt since last success, not failures or remaining draws. null means no finite guarantee. */
    fun guaranteeAttempt(): Long? {
        if (base == 1.0) return 1
        if (increment == 0.0) return null
        return runCatching {
            val steps = BigDecimal.ONE.subtract(BigDecimal.valueOf(base)).divide(BigDecimal.valueOf(increment), 0, RoundingMode.CEILING).longValueExact()
            Math.addExact(increaseAttempt - 1, steps)
        }.getOrNull()
    }
}
data class GachaState(val legendaryCount: Long = 0, val epicCount: Long = 0, val legendaryMiss: Long = 0, val epicMiss: Long = 0) {
    init { require(listOf(legendaryCount, epicCount, legendaryMiss, epicMiss).all { it >= 0 }) }
}
data class GachaSettings(val legendary: PityCurve, val epic: PityCurve, val troll: Double, val rare: Double,
    val legendaryPickup: Double, val epicPickup: Double, val legendarySemiCeiling: Long, val epicSemiCeiling: Long) {
    init {
        require(listOf(troll, rare, legendaryPickup, epicPickup).all { it.isFinite() && it in 0.0..1.0 })
        require(legendarySemiCeiling >= 0 && epicSemiCeiling >= 0)
    }
}
data class DrawOutcome(val grade: DrawGrade, val ability: String, val pickup: Boolean, val after: GachaState)

/** Shared by sampling and persisted-result validation; recovery never samples again. */
fun GachaState.advance(grade: DrawGrade, pickup: Boolean): GachaState {
    require(!pickup || grade == DrawGrade.LEGENDARY || grade == DrawGrade.EPIC)
    return GachaState(
        if (grade == DrawGrade.LEGENDARY) 0 else Math.incrementExact(legendaryCount),
        if (grade == DrawGrade.EPIC) 0 else Math.incrementExact(epicCount),
        if (grade == DrawGrade.LEGENDARY) { if (pickup) 0 else Math.incrementExact(legendaryMiss) } else legendaryMiss,
        if (grade == DrawGrade.EPIC) { if (pickup) 0 else Math.incrementExact(epicMiss) } else epicMiss)
}

class GachaPolicy(val settings: GachaSettings) {
    private fun sample(random: () -> Double): Double = random().also { require(it.isFinite() && it >= 0 && it < 1) }
    fun grade(state: GachaState, random: () -> Double): DrawGrade = when {
        sample(random) < settings.legendary.probability(state.legendaryCount) -> DrawGrade.LEGENDARY
        sample(random) < settings.epic.probability(state.epicCount) -> DrawGrade.EPIC
        sample(random) < settings.troll -> DrawGrade.TROLL
        sample(random) < settings.rare -> DrawGrade.RARE
        else -> DrawGrade.COMMON
    }
    fun draw(state: GachaState, candidates: Map<DrawGrade, List<String>>, pickups: Map<DrawGrade, String>, random: () -> Double): DrawOutcome {
        val pools = candidates.mapValues { it.value.toList() }
        val fixedPickups = pickups.toMap()
        for (grade in DrawGrade.entries) {
            val pool = requireNotNull(pools[grade]); require(pool.isNotEmpty() && pool.all(String::isNotBlank) && pool.distinct().size == pool.size)
        }
        for (grade in listOf(DrawGrade.LEGENDARY, DrawGrade.EPIC)) require(fixedPickups[grade] in pools[grade]!!)
        val grade = grade(state, random); val pool = pools[grade]!!
        val pickupCode = fixedPickups[grade]
        val forced = when (grade) {
            DrawGrade.LEGENDARY -> state.legendaryMiss >= settings.legendarySemiCeiling || sample(random) < settings.legendaryPickup
            DrawGrade.EPIC -> state.epicMiss >= settings.epicSemiCeiling || sample(random) < settings.epicPickup
            else -> false
        }
        val code = if (forced) requireNotNull(pickupCode) else pool[(sample(random) * pool.size).toInt()]
        val isPickup = pickupCode != null && code == pickupCode
        val after = state.advance(grade, isPickup)
        return DrawOutcome(grade, code, isPickup, after)
    }
    /** Unconditional next-draw probabilities preserve the original sequential conditional rolls. */
    fun rates(state: GachaState): Map<DrawGrade, Double> {
        val l = settings.legendary.probability(state.legendaryCount); val e = settings.epic.probability(state.epicCount)
        val remainder = (1 - l) * (1 - e)
        return mapOf(DrawGrade.LEGENDARY to l, DrawGrade.EPIC to (1 - l) * e,
            DrawGrade.TROLL to remainder * settings.troll, DrawGrade.RARE to remainder * (1 - settings.troll) * settings.rare,
            DrawGrade.COMMON to remainder * (1 - settings.troll) * (1 - settings.rare))
    }
}
