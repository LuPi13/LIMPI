package com.github.lupi13.limpi.ability

import org.bukkit.util.Vector
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * 앞으로 나아가며 앞쪽 적에게 휘는 유도(무스펠, 2.6.1). 유턴하지 않는다.
 * - 한 틱에 [maxTurn](라디안)까지만 목표 방향으로 돈다. 속도와 상관없다.
 * - 발사 방향에서 [maxTotal](라디안)보다 더 꺾이지 않는다.
 */
object HomingPolicy {
    fun angle(a: Vector, b: Vector): Double {
        val la = a.length(); val lb = b.length()
        if (la == 0.0 || lb == 0.0) return 0.0
        return acos((a.dot(b) / (la * lb)).coerceIn(-1.0, 1.0))
    }

    /** [from] 단위 벡터를 [toward] 쪽으로 [radians]만큼 돌린 단위 벡터. 이미 더 가까우면 [toward]. */
    fun rotateToward(from: Vector, toward: Vector, radians: Double): Vector {
        val a = from.clone().normalize()
        val b = toward.clone().normalize()
        val between = angle(a, b)
        if (between <= radians) return b
        // a에 수직이면서 b 쪽인 성분. 정반대(수직 성분 0)면 돌 방향이 정해지지 않으므로 그대로 둔다.
        val perpendicular = b.clone().subtract(a.clone().multiply(a.dot(b)))
        if (perpendicular.lengthSquared() < 1e-12) return a
        perpendicular.normalize()
        return a.multiply(cos(radians)).add(perpendicular.multiply(sin(radians))).normalize()
    }

    /**
     * 다음 진행 방향(길이 [speed]). [desired]가 null이면 직진한다.
     * 결과는 [launch]에서 [maxTotal] 이내로 묶인다.
     */
    fun steer(current: Vector, desired: Vector?, launch: Vector, maxTurn: Double, maxTotal: Double, speed: Double): Vector {
        require(maxTurn >= 0 && maxTotal >= 0 && speed > 0)
        var direction = current.clone().normalize()
        if (desired != null && desired.lengthSquared() > 0) direction = rotateToward(direction, desired, maxTurn)
        if (angle(launch, direction) > maxTotal) direction = rotateToward(launch, direction, maxTotal)
        return direction.multiply(speed)
    }

    /** 목표가 [targetVelocity](블록/틱)로 움직일 때 [speed]로 날아가 만날 지점을 겨냥한다. 예측 시간은 [maxLeadTicks]까지. */
    fun leadPoint(from: Vector, target: Vector, targetVelocity: Vector, speed: Double, maxLeadTicks: Double): Vector {
        val ticks = (target.distance(from) / speed).coerceAtMost(maxLeadTicks)
        return target.clone().add(targetVelocity.clone().multiply(ticks))
    }
}
