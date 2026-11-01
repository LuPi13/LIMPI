package com.github.lupi13.limpi.turret

import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * 중력을 받는 투사체의 발사 각도(Bukkit 없이 시험).
 * 바닐라 투척물처럼 매 틱 "위치 += 속도, 속도 *= 감속, 속도.y -= 중력" 순서로 날아간다고 보고 수치로 따라갑니다.
 */
object TurretAim {
    /** 눈덩이·알 같은 투척물의 바닐라 값. */
    const val THROWN_GRAVITY = 0.03
    const val THROWN_DRAG = 0.99

    private const val MAX_TICKS = 200
    private const val MIN_PITCH = -85.0
    private const val MAX_PITCH = 85.0
    private const val STEP = 0.5
    private const val FARTHEST = 45.0

    /**
     * 수평 거리 [horizontal], 높이 차 [vertical]에 있는 목표를 맞히는 가장 낮은 발사 각도(도, 위가 +).
     * 속도 [speed]로 닿지 않으면 가장 높이 닿는 각도를 돌려줍니다(최선의 시도).
     */
    fun pitch(horizontal: Double, vertical: Double, speed: Double,
              gravity: Double = THROWN_GRAVITY, drag: Double = THROWN_DRAG): Double {
        require(horizontal.isFinite() && horizontal >= 0 && vertical.isFinite() && speed.isFinite() && speed > 0)
        require(gravity.isFinite() && gravity >= 0 && drag.isFinite() && drag > 0 && drag <= 1)
        if (horizontal < 1.0e-6) return if (vertical >= 0) 90.0 else -90.0
        var best = FARTHEST // 어떤 각도로도 수평으로 닿지 못하면 가장 멀리 가는 각도
        var bestHeight = Double.NEGATIVE_INFINITY
        var angle = MIN_PITCH
        while (angle <= MAX_PITCH) {
            val height = heightAt(angle, horizontal, speed, gravity, drag)
            if (height >= vertical) return angle
            if (height > bestHeight) { bestHeight = height; best = angle }
            angle += STEP
        }
        return best
    }

    /** [pitch]도로 쏜 투사체가 수평 [horizontal]만큼 갔을 때의 높이(닿지 못하면 -∞). */
    fun heightAt(pitch: Double, horizontal: Double, speed: Double,
                 gravity: Double = THROWN_GRAVITY, drag: Double = THROWN_DRAG): Double {
        val radians = Math.toRadians(pitch)
        var vx = speed * cos(radians); var vy = speed * sin(radians)
        var x = 0.0; var y = 0.0
        repeat(MAX_TICKS) {
            val nx = x + vx; val ny = y + vy
            if (nx >= horizontal) return if (vx <= 0) ny else y + (ny - y) * (horizontal - x) / (nx - x)
            x = nx; y = ny
            vx *= drag; vy = vy * drag - gravity
        }
        return Double.NEGATIVE_INFINITY
    }
}

/**
 * 포탑에게 맞은 몹의 표적 고정. 한 몹은 한 포탑에만 고정되고, 그 포탑이 없어질 때까지 다른 포탑에 맞아도 바뀌지 않습니다
 * (포탑 두 개 사이를 오가다 아무것도 못 하는 일을 막음).
 */
class TargetLocks {
    private val byMob = HashMap<UUID, UUID>()

    /** [mob]을 [turret]에 고정합니다. 이미 살아 있는 포탑([alive])에 고정되어 있으면 바꾸지 않고 false. */
    fun lock(mob: UUID, turret: UUID, alive: (UUID) -> Boolean): Boolean {
        val current = byMob[mob]
        if (current != null && current != turret && alive(current)) return false
        byMob[mob] = turret
        return true
    }

    fun lockedTo(mob: UUID): UUID? = byMob[mob]

    /** [mob]이 [target]을 노려도 되는지: 고정이 없거나, 고정한 포탑을 노릴 때만. */
    fun allows(mob: UUID, target: UUID?): Boolean {
        val locked = byMob[mob] ?: return true
        return target == locked
    }

    fun mobsOf(turret: UUID): List<UUID> = byMob.filterValues { it == turret }.keys.toList()

    fun forget(mob: UUID) { byMob.remove(mob) }

    /** 포탑이 없어지면 그 포탑에 고정된 몹을 모두 풀어 줍니다. */
    fun release(turret: UUID) { byMob.values.removeIf { it == turret } }

    fun clear() = byMob.clear()
}

/** 자연 감소: 1초마다 [decay]씩 줄인 체력(0 미만 없음). */
object TurretDecay {
    fun next(health: Double, decay: Double): Double {
        require(health.isFinite() && decay.isFinite() && decay >= 0)
        return (health - decay).coerceAtLeast(0.0)
    }
}
