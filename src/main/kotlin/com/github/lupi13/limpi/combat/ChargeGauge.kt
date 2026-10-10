package com.github.lupi13.limpi.combat

object ChargeGauge {
    fun gain(current: Double, confirmedDamage: Double, required: Double): Double {
        require(current.isFinite() && current >= 0 && confirmedDamage.isFinite() && confirmedDamage >= 0 && required.isFinite() && required > 0)
        return (current + confirmedDamage).coerceAtMost(required)
    }
    fun consume(current: Double, confirmed: Boolean): Double {
        require(current.isFinite() && current >= 0)
        return if (confirmed) 0.0 else current
    }
}
