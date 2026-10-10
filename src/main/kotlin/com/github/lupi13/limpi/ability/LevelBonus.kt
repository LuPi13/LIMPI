package com.github.lupi13.limpi.ability

object LevelBonus {
    fun level(old: Int, current: Int, bonus: Int): Int {
        require(old >= 0 && current >= 0 && bonus >= 0)
        return if (current <= old) current else minOf(current.toLong() + bonus, Int.MAX_VALUE.toLong()).toInt()
    }
    fun experience(amount: Int, multiplier: Int): Int {
        require(multiplier >= 1)
        return if (amount <= 0) amount else minOf(amount.toLong() * multiplier, Int.MAX_VALUE.toLong()).toInt()
    }
}
