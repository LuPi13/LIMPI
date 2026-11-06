package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GroundPathTest {
    private val full = listOf(CollisionBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
    private val path = listOf(CollisionBox(0.0, 0.0, 0.0, 1.0, 0.9375, 1.0)) // Also farmland.
    private val slab = listOf(CollisionBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0))
    private val fence = listOf(CollisionBox(0.375, 0.0, 0.375, 0.625, 1.5, 0.625))

    /** A flat floor of [floor] at y=64 for every x, with [blocks] on top of it (x to y) in the row z=0. */
    private fun world(floor: List<CollisionBox> = full, blocks: Map<Pair<Int, Int>, List<CollisionBox>> = emptyMap(),
                      holes: Set<Int> = emptySet(), unknownFrom: Int = Int.MAX_VALUE): GroundPath = GroundPath({ x, y, _ ->
        when {
            x >= unknownFrom -> null
            (x to y) in blocks -> blocks.getValue(x to y)
            y == 64 && x !in holes -> floor
            else -> emptyList()
        }
    })

    private fun walkEast(path: GroundPath, y: Double, blocks: Double = 5.0) = path.walk(GroundPoint(0.5, y, 0.5), blocks / 100, 0.0, 100)

    @Test fun `walks from where the feet are inside the block on a path and a slab`() {
        val onPath = walkEast(world(path), 64.9375)
        assertEquals(5.5, onPath.x, 1e-6); assertEquals(64.9375, onPath.y, 1e-9)
        val onSlab = walkEast(world(slab), 64.5)
        assertEquals(5.5, onSlab.x, 1e-6); assertEquals(64.5, onSlab.y, 1e-9)
        assertEquals(5.5, walkEast(world(), 65.0).x, 1e-6)
    }

    @Test fun `standing on any of them is standable`() {
        assertTrue(world(path).standable(GroundPoint(0.5, 64.9375, 0.5)))
        assertTrue(world(slab).standable(GroundPoint(0.5, 64.5, 0.5)))
        assertTrue(world().standable(GroundPoint(0.5, 65.0, 0.5)))
        assertFalse(world(slab).standable(GroundPoint(0.5, 65.0, 0.5))) // In the air above the slab.
        assertFalse(world().standable(GroundPoint(0.5, 64.5, 0.5))) // Inside the block.
    }

    @Test fun `follows the ground over what walking can step over, both ways`() {
        // Full block floor, a slab on it at x=2, then floor again: up 0.5 and down 0.5.
        val over = walkEast(world(blocks = mapOf((2 to 65) to slab)), 65.0)
        assertEquals(5.5, over.x, 1e-6); assertEquals(65.0, over.y, 1e-9)
        // Path to full block is up a sixteenth, and the walk ends on the higher ground.
        val up = walkEast(world(path, blocks = mapOf((3 to 64) to full, (4 to 64) to full, (5 to 64) to full)), 64.9375)
        assertEquals(5.5, up.x, 1e-6); assertEquals(65.0, up.y, 1e-9)
    }

    @Test fun `a one block ledge or a wall stops the walk in front with the player touching nothing`() {
        val ledge = walkEast(world(blocks = mapOf((3 to 65) to full)), 65.0)
        assertEquals(2.7, ledge.x, 0.05); assertEquals(65.0, ledge.y, 1e-9)
        val wall = walkEast(world(blocks = mapOf((3 to 65) to full, (3 to 66) to full)), 65.0)
        assertEquals(ledge.x, wall.x, 1e-9)
    }

    @Test fun `a low ceiling stops the walk though the floor goes on`() {
        // 1.8 high player: a block at y=66 leaves one block of room.
        val under = walkEast(world(blocks = mapOf((3 to 66) to full)), 65.0)
        assertEquals(2.7, under.x, 0.05)
    }

    @Test fun `a drop stops the walk at the edge of the footprint`() {
        val drop = walkEast(world(holes = setOf(3, 4, 5, 6)), 65.0)
        assertEquals(3.3, drop.x, 0.06); assertEquals(65.0, drop.y, 1e-9)
    }

    @Test fun `a fence is taller than its cell and cannot be walked over`() {
        val blocked = walkEast(world(blocks = mapOf((3 to 65) to fence)), 65.0)
        assertTrue(blocked.x < 3.375 - 0.3 + 1e-6, "${blocked.x}")
    }

    @Test fun `nothing to walk on or a blocked first step stays in place`() {
        val againstWall = GroundPoint(0.7, 65.0, 0.5) // The box's edge touches the block at x=1.
        assertEquals(againstWall, world(blocks = mapOf((1 to 65) to full, (1 to 66) to full)).walk(againstWall, 0.05, 0.0, 100))
        val inTheAir = GroundPoint(0.5, 65.0, 0.5)
        assertEquals(inTheAir, GroundPath({ _, _, _ -> emptyList() }).walk(inTheAir, 0.05, 0.0, 100))
    }

    @Test fun `an unloaded chunk counts as blocked`() {
        assertEquals(2.7, walkEast(world(unknownFrom = 3), 65.0).x, 0.05)
    }

    @Test fun `each block is asked only once`() {
        val asked = mutableMapOf<Triple<Int, Int, Int>, Int>()
        val ground = GroundPath({ x, y, z -> asked.merge(Triple(x, y, z), 1, Int::plus); if (y == 64) full else emptyList() })
        ground.walk(GroundPoint(0.5, 65.0, 0.5), 0.1, 0.0, 100)
        assertTrue(asked.values.all { it == 1 })
    }

    @Test fun `negative coordinates work`() {
        val ground = GroundPath({ _, y, _ -> if (y == 64) path else emptyList() })
        val end = ground.walk(GroundPoint(-0.5, 64.9375, -3.5), -0.05, -0.05, 100)
        assertEquals(-5.5, end.x, 1e-6); assertEquals(-8.5, end.z, 1e-6); assertEquals(64.9375, end.y, 1e-9)
    }
}
