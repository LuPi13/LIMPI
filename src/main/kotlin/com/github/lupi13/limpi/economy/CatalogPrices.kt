package com.github.lupi13.limpi.economy

import java.math.BigDecimal
import java.math.RoundingMode

/** Positive integral prices with truncating ratios and no Double/Int saturation; unlisted items sell for 1. */
object CatalogPrices {
    fun sell(raw: Any?): Money {
        if (raw == null) return Money(1)
        require(raw is Byte || raw is Short || raw is Int || raw is Long) { "판매 가격은 정수여야 합니다." }
        return Money((raw as Number).toLong())
    }
    fun ratio(raw: Any?): BigDecimal {
        require(raw is Number) { "구매 비율은 숫자여야 합니다." }
        if (raw is Double || raw is Float) require(raw.toDouble().isFinite()) { "구매 비율은 유한한 비음수여야 합니다." }
        return BigDecimal(raw.toString()).also { require(it.signum() >= 0) { "구매 비율은 비음수여야 합니다." } }
    }
    fun shop(sell: Money, ratio: BigDecimal): Money {
        require(ratio.signum() >= 0)
        return Money(BigDecimal.valueOf(sell.units).multiply(ratio).setScale(0, RoundingMode.DOWN).longValueExact())
    }
}
