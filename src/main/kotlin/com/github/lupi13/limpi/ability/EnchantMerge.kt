package com.github.lupi13.limpi.ability

/** HeavyEnchant anvil rule: keep every enchantment at its highest level among the vanilla result and both inputs. */
object EnchantMerge {
    fun <K> highest(vararg sources: Map<K, Int>): Map<K, Int> {
        val merged = LinkedHashMap<K, Int>()
        for (source in sources) for ((key, level) in source) {
            require(level > 0) { "Enchantment level must be positive" }
            merged[key] = maxOf(merged[key] ?: 0, level)
        }
        return merged
    }
}
