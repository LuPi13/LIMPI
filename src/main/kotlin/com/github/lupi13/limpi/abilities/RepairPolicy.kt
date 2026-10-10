package com.github.lupi13.limpi.abilities

object RepairPolicy {
    const val DEFAULT_AMOUNT = 50
    fun amount(raw: Any?): Int {
        if (raw == null) return DEFAULT_AMOUNT
        require(raw is Int || raw is Long) { "repair는 비음수 정수여야 합니다." }
        val value = (raw as Number).toLong()
        require(value in 0..Int.MAX_VALUE.toLong()) { "repair는 Int 범위 비음수여야 합니다." }
        return value.toInt()
    }
    fun damageAfter(damage: Int, amount: Int): Int {
        require(damage >= 0 && amount >= 0)
        return (damage - amount).coerceAtLeast(0)
    }
}
