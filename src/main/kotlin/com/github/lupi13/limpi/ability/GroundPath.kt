package com.github.lupi13.limpi.ability

import kotlin.math.floor

/** A collision box relative to its block, as the block's collision shape reports it. */
data class CollisionBox(val minX: Double, val minY: Double, val minZ: Double, val maxX: Double, val maxY: Double, val maxZ: Double)

data class GroundPoint(val x: Double, val y: Double, val z: Double)

/**
 * Walks a player-sized box along the ground by collision shapes, not whole blocks: feet on a path, farmland or a bottom
 * slab are inside that block's cell. [shapes] answers for one block, or null where nothing is known (an unloaded chunk),
 * which counts as blocked. Each block is asked once, so a long path over the same few blocks stays cheap.
 */
class GroundPath(private val shapes: (x: Int, y: Int, z: Int) -> List<CollisionBox>?,
                 private val width: Double = 0.6, private val height: Double = 1.8, private val stepHeight: Double = 0.6) {
    private val known = HashMap<Triple<Int, Int, Int>, List<CollisionBox>?>()
    private fun shapeAt(x: Int, y: Int, z: Int) = known.getOrPut(Triple(x, y, z)) { shapes(x, y, z) }
    private fun cell(value: Double) = floor(value).toInt()

    /**
     * Runs [visit] with every box overlapping the footprint at ([x], [z]) in the block layers [fromY]..[toY], in world
     * height; a block taller than its cell (a fence) reaches up from the layer below. False where something is unknown.
     */
    private fun scan(x: Double, z: Double, fromY: Int, toY: Int, visit: (bottom: Double, top: Double) -> Unit): Boolean {
        val half = width / 2
        for (blockX in cell(x - half)..cell(x + half)) for (blockZ in cell(z - half)..cell(z + half)) for (blockY in fromY..toY) {
            val boxes = shapeAt(blockX, blockY, blockZ) ?: return false
            for (box in boxes) {
                val overlaps = blockX + box.minX < x + half - EPSILON && x - half < blockX + box.maxX - EPSILON &&
                    blockZ + box.minZ < z + half - EPSILON && z - half < blockZ + box.maxZ - EPSILON
                if (overlaps) visit(blockY + box.minY, blockY + box.maxY)
            }
        }
        return true
    }

    /** The highest surface under the footprint between [low] and [high], or null when there is none. */
    private fun surface(x: Double, z: Double, low: Double, high: Double): Double? {
        var best: Double? = null
        val complete = scan(x, z, cell(low) - 1, cell(high)) { _, top ->
            if (top >= low - EPSILON && top <= high + EPSILON && (best == null || top > best!!)) best = top
        }
        return if (complete) best else null
    }

    /** Whether the player's box at feet height [y] touches nothing. */
    private fun clear(x: Double, y: Double, z: Double): Boolean {
        var free = true
        val complete = scan(x, z, cell(y) - 1, cell(y + height)) { bottom, top ->
            if (bottom < y + height - EPSILON && y < top - EPSILON) free = false
        }
        return complete && free
    }

    /** Solid ground right at the feet and room for the whole player. */
    fun standable(point: GroundPoint): Boolean =
        surface(point.x, point.z, point.y - EPSILON, point.y + EPSILON) != null && clear(point.x, point.y, point.z)

    /**
     * Up to [steps] steps from [start]. Each one follows the ground up or down by at most what walking can step over, and
     * the last place reached is returned: a drop, a wall or a higher ledge stops the walk just before it.
     */
    fun walk(start: GroundPoint, stepX: Double, stepZ: Double, steps: Int): GroundPoint {
        var at = start
        for (i in 1..steps) {
            val x = at.x + stepX; val z = at.z + stepZ
            val ground = surface(x, z, at.y - stepHeight, at.y + stepHeight) ?: break
            if (!clear(x, ground, z)) break
            at = GroundPoint(x, ground, z)
        }
        return at
    }

    private companion object { const val EPSILON = 1e-7 }
}
