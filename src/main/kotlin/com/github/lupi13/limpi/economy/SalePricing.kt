package com.github.lupi13.limpi.economy

import org.bukkit.configuration.ConfigurationSection

/** One sellPrice.yml entry. Unlisted items are [Fixed] 0: they can still be put in the sale window, for nothing. */
sealed interface SellRule {
    /** The same price for every unit. */
    data class Fixed(val price: Long) : SellRule
    /** The n-th unit a player sells (counting their recovering total) pays max(floor, price × 0.5^(n / half)). */
    data class Decaying(val price: Long, val half: Long, val floor: Long) : SellRule {
        init { require(price >= 0 && half > 0 && floor in 0..price) }
    }
    /** Sold as [count] units of [base], so a storage block cannot skip the base item's decay. */
    data class Same(val base: String, val count: Long) : SellRule {
        init { require(base.isNotBlank() && count > 0) }
    }

    companion object {
        private fun integral(raw: Any?, name: String): Long {
            require(raw is Byte || raw is Short || raw is Int || raw is Long) { "판매 가격의 $name 값은 정수여야 합니다." }
            return (raw as Number).toLong()
        }

        /** Strict: decimals, strings, negative values and unknown keys are rejected instead of guessed. */
        fun parse(raw: Any?): SellRule {
            if (raw == null) return Fixed(0)
            if (raw !is ConfigurationSection && raw !is Map<*, *>) {
                return Fixed(integral(raw, "price").also { require(it >= 0) { "판매 가격은 0 이상이어야 합니다." } })
            }
            val values: Map<String, Any?> = if (raw is ConfigurationSection) raw.getValues(false)
                else (raw as Map<*, *>).entries.associate { it.key.toString() to it.value }
            if ("same" in values) {
                require(values.keys == setOf("same", "count")) { "same 항목에는 same, count만 쓸 수 있습니다." }
                val base = values["same"] as? String ?: throw IllegalArgumentException("same 값은 아이템 이름이어야 합니다.")
                val count = integral(values["count"], "count")
                require(count > 0) { "count는 1 이상이어야 합니다." }
                return Same(base.lowercase(java.util.Locale.ROOT), count)
            }
            require(values.keys == setOf("price", "half", "floor")) { "감소 가격에는 price, half, floor를 모두 써야 합니다." }
            val price = integral(values["price"], "price"); val half = integral(values["half"], "half"); val floor = integral(values["floor"], "floor")
            require(price >= 0 && half > 0 && floor in 0..price) { "price ≥ floor ≥ 0, half ≥ 1이어야 합니다." }
            return Decaying(price, half, floor)
        }
    }
}

/** A player's recovering sold count for one decaying item, stored in thousandths so it stays integral. */
data class SaleCounter(val milli: Long, val at: Long) {
    init { require(milli >= 0) }
    fun effective(now: Long, recoveryMillis: Long): Double {
        val elapsed = (now - at).coerceAtLeast(0)
        return milli / 1000.0 * StrictMath.pow(0.5, elapsed.toDouble() / recoveryMillis)
    }
}

data class SaleLine(val slot: Int, val code: String, val amount: Long) {
    init { require(code.isNotBlank() && amount > 0) }
}

/** [slots] are the lines with a valid rule; [counters] is the full updated map for the decaying items touched. */
data class SaleQuote(val total: Money, val slots: Set<Int>, val counters: Map<String, SaleCounter>)

class SellCatalog(private val raw: (String) -> Any?) {
    /** Resolves [Same] once; a chain or a missing base is a configuration error for that item only. */
    fun rule(code: String): SellRule {
        val rule = SellRule.parse(raw(code))
        if (rule !is SellRule.Same) return rule
        val base = SellRule.parse(raw(rule.base))
        require(base !is SellRule.Same) { "same 대상(${rule.base})은 다시 same일 수 없습니다." }
        require(raw(rule.base) != null) { "same 대상(${rule.base})이 판매 목록에 없습니다." }
        return rule
    }

    /**
     * Sums the lines in slot order. Decaying parts are added exactly and the sale total is rounded down once,
     * so a 2-unit price does not drop to 1 from the second unit. Lines whose rule is invalid are left out (0).
     */
    fun quote(lines: List<SaleLine>, counters: Map<String, SaleCounter>, now: Long, recoveryMillis: Long,
              invalid: (SaleLine, Exception) -> Unit = { _, _ -> }): SaleQuote {
        require(recoveryMillis > 0)
        var fixed = 0L
        var decaying = 0.0
        val sold = mutableMapOf<String, Double>()
        val slots = mutableSetOf<Int>()
        for (line in lines.sortedBy { it.slot }) {
            val rule = try { rule(line.code) } catch (failure: Exception) { invalid(line, failure); continue }
            val (code, base, units) = when (rule) {
                is SellRule.Same -> Triple(rule.base, SellRule.parse(raw(rule.base)), Math.multiplyExact(line.amount, rule.count))
                else -> Triple(line.code, rule, line.amount)
            }
            when (base) {
                is SellRule.Fixed -> fixed = Math.addExact(fixed, Math.multiplyExact(base.price, units))
                is SellRule.Decaying -> {
                    val start = sold[code] ?: counters[code]?.effective(now, recoveryMillis) ?: 0.0
                    decaying += sum(base, start, units)
                    sold[code] = start + units
                }
                is SellRule.Same -> error("unreachable")
            }
            slots += line.slot
        }
        // A tiny upward tolerance keeps an exact sum such as 300.0 from landing on 299.999… after the series formula.
        val rounded = StrictMath.floor(decaying + 1e-6)
        if (rounded >= Long.MAX_VALUE.toDouble()) throw ArithmeticException("sale total overflow")
        val total = Money(Math.addExact(fixed, rounded.toLong()))
        val updated = counters.toMutableMap()
        for ((code, count) in sold) updated[code] = SaleCounter(StrictMath.round(count * 1000).coerceAtLeast(0), now)
        return SaleQuote(total, slots, updated)
    }

    companion object {
        fun of(config: ConfigurationSection) = SellCatalog { config.get(it) }

        /** Σ max(floor, price × 0.5^(n/half)) for n = start, start+1, … (units terms), in closed form. */
        fun sum(rule: SellRule.Decaying, start: Double, units: Long): Double {
            if (units <= 0) return 0.0
            val p = rule.price.toDouble(); val f = rule.floor.toDouble()
            if (rule.floor == rule.price) return f * units
            // Terms with n < cross are still above the floor.
            val cross = if (rule.floor == 0L) Double.POSITIVE_INFINITY else rule.half * (StrictMath.log(p / f) / StrictMath.log(2.0))
            val above = if (cross.isInfinite()) units else StrictMath.ceil(cross - start).coerceIn(0.0, units.toDouble()).toLong()
            val ratio = StrictMath.pow(0.5, 1.0 / rule.half)
            val geometric = if (above == 0L) 0.0
                else p * StrictMath.pow(0.5, start / rule.half) * (1 - StrictMath.pow(ratio, above.toDouble())) / (1 - ratio)
            return geometric + f * (units - above)
        }
    }
}

object SaleCounters {
    fun key(owner: java.util.UUID) = "sale_count/$owner"

    fun decode(payload: String?): Map<String, SaleCounter> {
        if (payload == null) return emptyMap()
        val data = com.github.lupi13.limpi.storage.YamlFiles.parse(payload)
        return data.getKeys(false).associateWith { code ->
            val section = requireNotNull(data.getConfigurationSection(code)) { "판매 기록 $code 형식 오류" }
            val milli = section.get("milli"); val at = section.get("at")
            require((milli is Int || milli is Long) && (at is Int || at is Long)) { "판매 기록 $code 형식 오류" }
            SaleCounter((milli as Number).toLong(), (at as Number).toLong())
        }
    }

    fun encode(counters: Map<String, SaleCounter>): String = org.bukkit.configuration.file.YamlConfiguration().apply {
        for ((code, counter) in counters.toSortedMap()) { set("$code.milli", counter.milli); set("$code.at", counter.at) }
    }.saveToString()
}
