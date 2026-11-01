package com.github.lupi13.limpi.ability

/** 서서히 회복(응급처치): [amount]를 [seconds]초에 걸쳐 [period]틱마다 나눠 줍니다(Bukkit 없이 시험). */
object HealOverTime {
    const val PERIOD_TICKS = 10

    /** 단계마다 회복량. 합이 정확히 [amount]이고, [seconds]가 0이면 한 번에 줍니다. */
    fun steps(amount: Double, seconds: Double, period: Int = PERIOD_TICKS): List<Double> {
        require(amount.isFinite() && amount >= 0 && seconds.isFinite() && seconds >= 0 && period > 0)
        if (amount == 0.0) return emptyList()
        val count = kotlin.math.ceil(seconds * 20 / period).toInt().coerceIn(1, 72000)
        val each = amount / count
        return List(count) { index -> if (index == count - 1) amount - each * (count - 1) else each }
    }
}
