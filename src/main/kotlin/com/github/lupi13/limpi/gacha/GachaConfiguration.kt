package com.github.lupi13.limpi.gacha

import org.bukkit.configuration.ConfigurationSection

object GachaConfiguration {
    private fun integer(data: ConfigurationSection, path: String, default: Long? = null): Long {
        val raw = data.get(path) ?: return requireNotNull(default) { "Missing $path" }
        require(raw is Int || raw is Long) { "$path must be integral" }
        return (raw as Number).toLong()
    }
    private fun probability(data: ConfigurationSection, path: String): Double {
        val raw = data.get(path); require(raw is Number) { "Missing or nonnumeric $path" }; return raw.toDouble()
    }
    fun settings(data: ConfigurationSection): GachaSettings {
        fun curve(name: String) = PityCurve(probability(data, "$name.Base"), probability(data, "$name.Increment"), integer(data, "$name.IncreaseCount"))
        return GachaSettings(curve("LEGENDARYProbability"), curve("EPICProbability"), probability(data, "TROLLProbability"), probability(data, "RAREProbability"),
            probability(data, "LEGENDARYProbability.PickUp"), probability(data, "EPICProbability.PickUp"),
            integer(data, "LEGENDARYProbability.SemiCeiling"), integer(data, "EPICProbability.SemiCeiling"))
    }
    fun state(data: ConfigurationSection) = GachaState(integer(data, "LEGENDARY.Count", 0), integer(data, "EPIC.Count", 0),
        integer(data, "LEGENDARY.Ceiling", 0), integer(data, "EPIC.Ceiling", 0))
    fun write(data: ConfigurationSection, state: GachaState) {
        data.set("LEGENDARY.Count", state.legendaryCount); data.set("EPIC.Count", state.epicCount)
        data.set("LEGENDARY.Ceiling", state.legendaryMiss); data.set("EPIC.Ceiling", state.epicMiss)
    }
}
