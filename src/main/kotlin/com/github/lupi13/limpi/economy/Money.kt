package com.github.lupi13.limpi.economy

/** Integral currency. Invalid input and overflow fail before changing balances. */
@JvmInline
value class Money(val units: Long) {
    init { require(units >= 0) { "Money must not be negative" } }
    operator fun plus(other: Money) = Money(Math.addExact(units, other.units))
    operator fun minus(other: Money) = Money(Math.subtractExact(units, other.units))
    fun times(count: Long): Money {
        require(count >= 0) { "Quantity must not be negative" }
        return Money(Math.multiplyExact(units, count))
    }

    /** Fees round down to whole currency units, without floating point or overflowing intermediates. */
    fun fee(percent: Int): Money {
        require(percent in 0..100)
        return Money(Math.addExact(Math.multiplyExact(units / 100, percent.toLong()),
            (units % 100) * percent / 100))
    }
}
