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

/** 매 틱 도는 포탑 작업의 주기 판정. */
object TurretSchedule {
    /** 마지막 실행([last]) 뒤 [interval]틱 이상 지났는지. */
    fun due(now: Int, last: Int, interval: Int): Boolean = now.toLong() - last >= interval
}

/** 자연 감소: 1초마다 [decay]씩 줄인 체력(0 미만 없음). */
object TurretDecay {
    fun next(health: Double, decay: Double): Double {
        require(health.isFinite() && decay.isFinite() && decay >= 0)
        return (health - decay).coerceAtLeast(0.0)
    }
}

/**
 * 충전식 발사: 매 틱 공격 속도만큼 충전하고 간격 이상이면 쏩니다. 금 포탑처럼 속도를 올리는 효과가 있어도 소수 간격이
 * 정확합니다. 표적이 없으면 간격에서 멈춰 기다리므로 적이 들어오면 바로 쏩니다.
 */
object TurretCharge {
    /** 설치 직후의 충전량. 0이라 설치하고 공격 간격만큼 지나야 첫 발을 쏩니다(PvP에서 설치를 보고 피할 시간). */
    const val INITIAL = 0.0

    /** 맞닿은 지원 포탑들의 기여([bonuses])를 더한 공격 속도(1 = 기본). */
    fun speed(bonuses: Iterable<Double>): Double = 1.0 + bonuses.sumOf { it.coerceAtLeast(0.0) }

    fun fill(charge: Double, speed: Double, interval: Int): Double = (charge + speed).coerceAtMost(interval.toDouble())

    fun ready(charge: Double, interval: Int): Boolean = charge >= interval

    fun spend(charge: Double, interval: Int): Double = charge - interval
}

/** 맞닿은 여섯 면의 칸 좌표 차이. */
val TURRET_NEIGHBOR_OFFSETS: List<Triple<Int, Int, Int>> = listOf(
    Triple(1, 0, 0), Triple(-1, 0, 0), Triple(0, 1, 0), Triple(0, -1, 0), Triple(0, 0, 1), Triple(0, 0, -1)
)

/** 최근 [window]틱 안의 기록이 [target]개 이상인지 셉니다(퀘스트 "1분 안에 10마리"). */
class KillWindow(private val window: Int, private val target: Int) {
    init { require(window > 0 && target > 0) }
    private val times = ArrayDeque<Int>()

    /** [now]에 하나를 더하고, 창 안의 개수가 [target]에 닿았으면 true(그때 기록을 비움). */
    fun add(now: Int): Boolean {
        times.addLast(now)
        while (times.isNotEmpty() && now.toLong() - times.first() >= window) times.removeFirst()
        if (times.size < target) return false
        times.clear()
        return true
    }

    val size: Int get() = times.size
}

/** 에메랄드 포탑 회복량: 1초 회복량 × 공격 속도(맞닿은 금 포탑 가속). */
object TurretHeal {
    fun amount(perSecond: Double, speed: Double): Double = (perSecond.coerceAtLeast(0.0) * speed.coerceAtLeast(0.0))
}

/**
 * 표적 분산: 포탑 탄이 날아가는 중이거나 막 맞아 무적 시간(10틱)인 대상은 피해가 거의 들어가지 않으므로 뒤로 미룹니다.
 * 대상 → "이 틱까지는 바쁨" 기록만 두고, 지난 기록은 가끔 지웁니다.
 */
class TurretFocus<K> {
    private val busyUntil = HashMap<K, Int>()

    /** [key]를 [until] 틱까지 바쁨으로 둡니다(더 늦은 기록만 남김). */
    fun reserve(key: K, until: Int) {
        val current = busyUntil[key]
        if (current == null || until > current) busyUntil[key] = until
    }

    fun busy(key: K, now: Int): Boolean = (busyUntil[key] ?: return false) > now

    /** 끝난 기록을 지웁니다. */
    fun prune(now: Int) { busyUntil.values.removeIf { it <= now } }

    val size: Int get() = busyUntil.size

    fun clear() = busyUntil.clear()

    companion object {
        /** 바닐라 무적 시간 중 피해가 깎이는 구간(맞은 뒤 10틱). */
        const val INVULNERABLE_TICKS = 10

        /**
         * 선호 순서 [ranked]의 앞에서 [limit]개까지 보며, 쏠 수 있는([usable]) 첫 한가한 대상을 고릅니다.
         * 모두 바쁘면 쏠 수 있는 첫 대상(대상이 하나뿐이어도 계속 쏨). [usable]은 시야 검사가 있어 꼭 필요한 만큼만 부릅니다.
         */
        fun <T> choose(ranked: List<T>, limit: Int, usable: (T) -> Boolean, busy: (T) -> Boolean): T? {
            var fallback: T? = null
            for (candidate in ranked.asSequence().take(limit)) {
                if (fallback != null && busy(candidate)) continue // 대안이 이미 있으면 바쁜 대상은 시야 검사도 생략
                if (!usable(candidate)) continue
                if (!busy(candidate)) return candidate
                fallback = candidate
            }
            return fallback
        }

        /** 거리와 탄속으로 어림한 도착까지의 틱. */
        fun flightTicks(distance: Double, speed: Double): Int =
            if (speed <= 0.0 || !distance.isFinite()) 0 else kotlin.math.ceil(distance.coerceAtLeast(0.0) / speed).toInt()
    }
}
