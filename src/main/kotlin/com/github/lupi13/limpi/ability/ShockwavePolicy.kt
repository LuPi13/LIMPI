package com.github.lupi13.limpi.ability

import kotlin.math.exp

data class ShockwavePolicy(val original: Double, val damping: Double) {
    init { require(original.isFinite() && original > 0 && damping.isFinite() && damping >= 0) }
    val radius: Double = (original * 2).coerceAtMost(128.0)
    val rings: Int = (original / 2).coerceAtMost(16.0).toInt()
    fun damage(distance: Double): Double {
        require(distance.isFinite() && distance >= 0)
        if (damping == 0.0 || distance == 0.0) return original
        return original * exp(-(distance * distance / original) * damping)
    }
    /** A single collision cannot schedule unbounded random retries. */
    fun repeats(probability: Double, roll: () -> Double): Int {
        require(probability.isFinite() && probability in 0.0..1.0)
        var count = 0
        while (count < 8 && roll() < probability) count++
        return count
    }
}
