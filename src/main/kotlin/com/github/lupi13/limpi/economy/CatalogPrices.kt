package com.github.lupi13.limpi.economy

/** Positive integral prices with no Double/Int saturation. */
object CatalogPrices {
    /** A configured price: a non-negative integer only; decimals, strings and missing values are rejected. */
    fun price(raw: Any?): Money {
        require(raw is Byte || raw is Short || raw is Int || raw is Long) { "가격은 정수여야 합니다." }
        return Money((raw as Number).toLong().also { require(it >= 0) { "가격은 0 이상이어야 합니다." } })
    }

    /** The most a single unit can ever sell for (a decaying rule's starting price). */
    fun highestSell(catalog: SellCatalog, code: String, raw: (String) -> Any?): Long = when (val rule = catalog.rule(code)) {
        is SellRule.Fixed -> rule.price
        is SellRule.Decaying -> rule.price
        is SellRule.Same -> Math.multiplyExact(when (val base = SellRule.parse(raw(rule.base))) {
            is SellRule.Fixed -> base.price
            is SellRule.Decaying -> base.price
            is SellRule.Same -> error("unreachable")
        }, rule.count)
    }

    /** A shop price below what one unit sells for would let anyone buy and resell at a profit. */
    fun shop(raw: Any?, highestSell: Long): Money {
        val price = price(raw)
        require(price.units >= highestSell) { "구매 가격(${price.units})이 판매 가격(${highestSell})보다 낮습니다." }
        return price
    }
}
