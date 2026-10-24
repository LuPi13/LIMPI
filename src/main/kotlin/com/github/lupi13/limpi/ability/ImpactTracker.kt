package com.github.lupi13.limpi.ability

import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Movement samples of one player, one per client move packet (one client tick).
 * Server-tick position deltas are not used: two packets can arrive in one server tick and double the speed.
 */
class ImpactTracker {
    data class Sample(val wallSpeed: Double, val fallSpeed: Double)

    private var previous: Vector? = null
    private val blocked = IntArray(3) // direction (x, y, z) blocked after the last sample

    fun reset() { previous = null; blocked.fill(0) }

    /**
     * [touches] tells whether a block lies right next to the player after this move along (axis, direction).
     * Wall speed: the previous move's speed on axes that just became blocked (floor excluded, capped at [maxWallSpeed]).
     * Fall speed: downward approach speed, one move behind so a partial landing move still reports the impact speed.
     */
    fun sample(moved: Vector, maxWallSpeed: Double, touches: (axis: Int, direction: Int) -> Boolean): Sample {
        val before = previous
        previous = moved.clone()
        if (before == null) return Sample(0.0, 0.0)

        val speeds = doubleArrayOf(before.x, before.y, before.z)
        var lost = 0.0
        for (axis in 0..2) {
            val speed = speeds[axis]
            val direction = if (abs(speed) < 1.0e-3 || (axis == 1 && speed < 0)) 0 else sign(speed).toInt()
            val now = if (direction != 0 && touches(axis, direction)) direction else 0
            if (now != 0 && blocked[axis] != now) lost += speed * speed
            blocked[axis] = now
        }
        val wall = sqrt(lost).coerceAtMost(maxWallSpeed.coerceAtLeast(0.0))

        val fall = when {
            !(moved.y < 0) -> 0.0
            before.y < 0 -> -before.y
            else -> -moved.y
        }.coerceAtMost(ImpactPolicy.terminalSpeed)
        return Sample(wall, fall)
    }
}
