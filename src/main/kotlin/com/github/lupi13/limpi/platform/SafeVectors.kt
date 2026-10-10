package com.github.lupi13.limpi.platform

import org.bukkit.util.Vector

/** Preserve Vector's mutation contract while coincident/invalid coordinates yield zero instead of NaN. */
fun Vector.normalizeSafely(): Vector {
    if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return zero()
    val scale = maxOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z))
    if (scale == 0.0) return zero()
    x /= scale; y /= scale; z /= scale
    val length = kotlin.math.sqrt(x * x + y * y + z * z)
    x /= length; y /= length; z /= length
    return this
}
