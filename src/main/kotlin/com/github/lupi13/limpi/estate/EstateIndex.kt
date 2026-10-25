package com.github.lupi13.limpi.estate

import java.util.UUID

/**
 * Immutable lookup from a block to the estate containing it. Estates are listed under every 16x16 column they cover, so
 * a lookup reads one map entry and compares a few boxes; most of the world has no entry and returns at once.
 */
class EstateIndex(estates: Collection<Estate>) {
    private val worlds: Map<UUID, Map<Long, Array<Estate>>> = estates.groupBy { it.region.world }.mapValues { (_, list) ->
        val chunks = HashMap<Long, MutableList<Estate>>()
        for (estate in list) {
            val region = estate.region
            for (cx in (region.minX shr 4)..(region.maxX shr 4)) for (cz in (region.minZ shr 4)..(region.maxZ shr 4)) {
                chunks.getOrPut(key(cx, cz)) { ArrayList(1) }.add(estate)
            }
        }
        chunks.mapValues { it.value.toTypedArray() }
    }

    fun at(world: UUID, x: Int, y: Int, z: Int): Estate? {
        val chunks = worlds[world] ?: return null
        val candidates = chunks[key(x shr 4, z shr 4)] ?: return null
        for (estate in candidates) if (estate.region.contains(world, x, y, z)) return estate
        return null
    }

    fun hasWorld(world: UUID): Boolean = world in worlds

    private companion object {
        fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xffffffffL)
    }
}
