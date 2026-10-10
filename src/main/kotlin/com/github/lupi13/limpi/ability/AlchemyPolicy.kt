package com.github.lupi13.limpi.ability

/** Selection happens before consumption; the chosen output is persisted rather than rolled again. */
class AlchemyPolicy(weights: Map<String, Double>) {
    private val weights = weights.toList()
    init {
        require(this.weights.isNotEmpty() && this.weights.all { it.first.isNotBlank() && it.second.isFinite() && it.second >= 0 })
        require(kotlin.math.abs(this.weights.sumOf { it.second } - 1.0) <= 1e-9)
    }
    fun choose(roll: Double): String {
        require(roll.isFinite() && roll >= 0 && roll < 1)
        var remaining = roll
        for ((key, weight) in weights) {
            if (remaining < weight) return key
            remaining -= weight
        }
        // Only floating point rounding can reach here; never choose a zero-weight result.
        return weights.last { it.second > 0 }.first
    }
    companion object {
        val OUTPUTS = listOf("coal", "iron_nugget", "iron_ingot", "gold_nugget", "gold_ingot", "redstone", "lapis_lazuli", "emerald", "diamond", "netherite_ingot")
    }
}
