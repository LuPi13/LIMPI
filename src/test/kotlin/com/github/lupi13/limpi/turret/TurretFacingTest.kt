package com.github.lupi13.limpi.turret

import org.bukkit.block.BlockFace
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TurretFacingTest {
    private val faces = listOf(BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)

    private fun assertSame(expected: Vector, actual: Vector) {
        assertEquals(expected.x, actual.x, 1e-9); assertEquals(expected.y, actual.y, 1e-9); assertEquals(expected.z, actual.z, 1e-9)
    }

    @Test fun `yaw and pitch follow minecraft conventions`() {
        assertEquals(0f, TurretFacing.yawPitch(Vector(0, 0, 1)).first, 1e-4f)
        assertEquals(0f, TurretFacing.yawPitch(Vector(0, 0, 1)).second, 1e-4f)
        assertEquals(-90f, TurretFacing.yawPitch(Vector(1, 0, 0)).first, 1e-4f)
        assertEquals(90f, TurretFacing.yawPitch(Vector(-1, 0, 0)).first, 1e-4f)
        assertEquals(90f, TurretFacing.yawPitch(Vector(0, -1, 0)).second, 1e-4f)
        assertEquals(-45f, TurretFacing.yawPitch(Vector(0.0, 1.0, 1.0)).second, 1e-4f)
    }

    @Test fun `a floor turret looks in world directions as they are`() {
        val east = Vector(1.0, 0.3, 0.0)
        assertSame(east, TurretFacing.toModel(BlockFace.DOWN, east))
        assertEquals(TurretFacing.yawPitch(east), TurretFacing.rotation(BlockFace.DOWN, east))
    }

    @Test fun `every attached face turns the outward normal into model up and round-trips`() {
        for (face in faces) {
            assertSame(Vector(0, 1, 0), TurretFacing.toModel(face, face.oppositeFace.direction))
            for (world in listOf(Vector(1.0, 0.2, -0.5), Vector(0.0, -1.0, 0.3), Vector(-0.7, 0.0, 0.7))) {
                assertSame(world, TurretFacing.toWorld(face, TurretFacing.toModel(face, world)))
                assertEquals(world.length(), TurretFacing.toModel(face, world).length(), 1e-9)
            }
        }
    }

    @Test fun `initial facing is the player's horizontal look on floors and ceilings and outward on walls`() {
        assertSame(Vector(1, 0, 0), TurretFacing.initial(BlockFace.DOWN, Vector(2.0, -1.0, 0.0)))
        assertSame(Vector(0, 0, -1), TurretFacing.initial(BlockFace.UP, Vector(0.0, 0.9, -0.1)))
        assertSame(Vector(0, 0, 1), TurretFacing.initial(BlockFace.DOWN, Vector(0.0, -1.0, 0.0)))
        assertSame(Vector(0, 0, 1), TurretFacing.initial(BlockFace.NORTH, Vector(0.0, 0.0, -1.0))) // 북쪽 벽에 붙음 → 남쪽을 봄
        assertSame(Vector(-1, 0, 0), TurretFacing.initial(BlockFace.EAST, Vector(1.0, 0.0, 0.0)))
    }
}
