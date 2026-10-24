package com.github.lupi13.limpi.ability

import kotlin.math.ceil

/**
 * Impact speed (blocks/tick) to the vanilla free-fall height that reaches the same speed.
 * A plain fall therefore hurts exactly like vanilla; faster or slower impacts scale with it.
 */
object ImpactPolicy {
    private const val GRAVITY = 0.08
    private const val DRAG = 0.98
    private const val TICKS = 600

    // speeds[i]: downward speed of fall tick i, heights[i]: total height fallen after that tick.
    private val speeds = DoubleArray(TICKS + 1)
    private val heights = DoubleArray(TICKS + 1)

    init {
        var speed = 0.0
        var height = 0.0
        for (tick in 1..TICKS) {
            speed = (speed + GRAVITY) * DRAG
            height += speed
            speeds[tick] = speed; heights[tick] = height
        }
    }

    /** Vanilla falling speed limit (about 3.92 blocks/tick). */
    val terminalSpeed: Double get() = speeds[TICKS]

    fun fallHeight(speed: Double): Double {
        if (speed.isNaN() || speed <= 0.0) return 0.0
        val last = speeds[TICKS]
        if (speed >= last) {
            // Faster than terminal velocity: continue with the drag-free energy relation.
            val extra = (minOf(speed, 1.0e6) * minOf(speed, 1.0e6) - last * last) / (2 * GRAVITY)
            return heights[TICKS] + extra
        }
        var low = 0; var high = TICKS
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (speeds[mid] <= speed) low = mid else high = mid
        }
        val ratio = (speed - speeds[low]) / (speeds[high] - speeds[low])
        return heights[low] + (heights[high] - heights[low]) * ratio
    }

    /** Fall distance to hand to vanilla so its own damage rules (blocks, armor, enchantments) still apply. */
    fun fallDistance(speed: Double, safe: Double, multiplier: Double): Double {
        val height = fallHeight(speed)
        if (height <= safe) return height
        return safe + (height - safe) * multiplier.coerceAtLeast(0.0)
    }

    /** Wall/ceiling impact damage, rounded like vanilla fall damage. */
    fun impactDamage(speed: Double, safe: Double, multiplier: Double): Double {
        val excess = (fallHeight(speed) - safe) * multiplier.coerceAtLeast(0.0)
        if (!(excess > 0.0)) return 0.0
        return ceil(excess)
    }
}
