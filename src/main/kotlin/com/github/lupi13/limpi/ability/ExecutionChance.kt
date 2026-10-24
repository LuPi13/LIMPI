package com.github.lupi13.limpi.ability

import kotlin.math.pow

/**
 * 죽음의 주사위 즉사 확률(2.6.0): `잃은 체력 비율^degree × min(1, referenceHealth / 최대 체력)`.
 * 최대 체력이 기준 이하인 몹과 플레이어는 예전과 같고, 체력이 큰 대상(보스)일수록 그 비율만큼 줄어든다.
 */
object ExecutionChance {
    fun of(health: Double, maxHealth: Double, degree: Double, referenceHealth: Double): Double {
        require(degree.isFinite() && degree > 0 && referenceHealth.isFinite() && referenceHealth > 0)
        if (!(maxHealth > 0) || !maxHealth.isFinite() || health.isNaN()) return 0.0
        val missing = (1 - health / maxHealth).coerceIn(0.0, 1.0)
        return missing.pow(degree) * (referenceHealth / maxHealth).coerceAtMost(1.0)
    }
}
