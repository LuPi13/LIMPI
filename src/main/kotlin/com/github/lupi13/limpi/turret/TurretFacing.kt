package com.github.lupi13.limpi.turret

import org.bukkit.block.BlockFace
import org.bukkit.util.Vector
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 셜커 포탑의 머리 방향(Bukkit 서버 없이 시험).
 *
 * AI를 끈 셜커는 머리를 스스로 돌리지 않으므로 회전값(yaw, pitch)을 직접 넣습니다. 셜커 모델은 붙은 면의 반대 방향
 * (바닥에 붙으면 위)을 "위"로 삼아 돌려 그려지므로, 세계 방향을 그 회전의 역으로 돌려 모델 기준 방향을 구합니다.
 * 회전은 바닐라 `Direction.getRotation()`(UP=그대로, DOWN=X 180°, SOUTH=X 90°, NORTH=X 90°·Z 180°,
 * WEST=X 90°·Z 90°, EAST=X 90°·Z -90°)을 따른다고 가정합니다. 바닥 포탑은 세계 방향 그대로이고, 벽·천장은 서버에서 확인이 필요합니다.
 */
object TurretFacing {
    private const val HALF = Math.PI / 2

    /** 붙은 면 [attached]인 포탑이 세계 방향 [world]를 보게 하는 (yaw, pitch). */
    fun rotation(attached: BlockFace, world: Vector): Pair<Float, Float> = yawPitch(toModel(attached, world))

    /** 세계 방향 → 모델 방향(붙은 면의 반대 방향 회전의 역). */
    fun toModel(attached: BlockFace, world: Vector): Vector {
        val v = world.clone()
        return when (attached.oppositeFace) {
            BlockFace.UP -> v
            BlockFace.DOWN -> v.rotateAroundX(-Math.PI)
            BlockFace.SOUTH -> v.rotateAroundX(-HALF)
            BlockFace.NORTH -> v.rotateAroundX(-HALF).rotateAroundZ(-Math.PI)
            BlockFace.WEST -> v.rotateAroundX(-HALF).rotateAroundZ(-HALF)
            BlockFace.EAST -> v.rotateAroundX(-HALF).rotateAroundZ(HALF)
            else -> v
        }
    }

    /** 모델 방향 → 세계 방향([toModel]의 역, 시험용). */
    fun toWorld(attached: BlockFace, model: Vector): Vector {
        val v = model.clone()
        return when (attached.oppositeFace) {
            BlockFace.UP -> v
            BlockFace.DOWN -> v.rotateAroundX(Math.PI)
            BlockFace.SOUTH -> v.rotateAroundX(HALF)
            BlockFace.NORTH -> v.rotateAroundZ(Math.PI).rotateAroundX(HALF)
            BlockFace.WEST -> v.rotateAroundZ(HALF).rotateAroundX(HALF)
            BlockFace.EAST -> v.rotateAroundZ(-HALF).rotateAroundX(HALF)
            else -> v
        }
    }

    /** 마인크래프트 방식의 (yaw, pitch): +z가 yaw 0, -x 쪽이 yaw +90, 아래가 pitch +90. */
    fun yawPitch(direction: Vector): Pair<Float, Float> {
        val x = direction.x; val y = direction.y; val z = direction.z
        val horizontal = sqrt(x * x + z * z)
        if (horizontal < 1.0e-9 && y == 0.0) return 0f to 0f
        val yaw = Math.toDegrees(atan2(-x, z))
        val pitch = Math.toDegrees(-atan2(y, horizontal))
        return yaw.toFloat() to pitch.toFloat()
    }

    /**
     * 설치할 때 처음 볼 세계 방향: 바닥·천장 포탑은 플레이어가 바라보던 수평 방향, 벽 포탑은 벽 바깥쪽
     * (플레이어는 벽을 보고 설치하므로 같은 방향이면 벽 속을 봅니다).
     */
    fun initial(attached: BlockFace, playerLook: Vector): Vector {
        if (attached == BlockFace.UP || attached == BlockFace.DOWN) {
            val flat = Vector(playerLook.x, 0.0, playerLook.z)
            return if (flat.lengthSquared() < 1.0e-9) Vector(0.0, 0.0, 1.0) else flat.normalize()
        }
        return attached.oppositeFace.direction
    }
}
