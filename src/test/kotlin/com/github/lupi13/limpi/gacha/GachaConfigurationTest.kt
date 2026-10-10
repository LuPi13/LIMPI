package com.github.lupi13.limpi.gacha

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GachaConfigurationTest {
    private fun config() = YamlConfiguration().apply {
        for (name in listOf("LEGENDARYProbability", "EPICProbability")) {
            set("$name.Base", .1); set("$name.Increment", .1); set("$name.IncreaseCount", 5)
            set("$name.PickUp", .5); set("$name.SemiCeiling", 1)
        }
        set("TROLLProbability", .01); set("RAREProbability", .3)
    }
    @Test fun `configuration snapshot is independent of later edits and strict field types are enforced`() {
        val data = config(); val snapshot = GachaConfiguration.settings(data)
        data.set("LEGENDARYProbability.Base", .9); assertEquals(.1, snapshot.legendary.base)
        data.set("LEGENDARYProbability.IncreaseCount", 5.5)
        assertThrows(IllegalArgumentException::class.java) { GachaConfiguration.settings(data) }
        data.set("LEGENDARYProbability.IncreaseCount", 5); data.set("EPICProbability.PickUp", Double.NaN)
        assertThrows(IllegalArgumentException::class.java) { GachaConfiguration.settings(data) }
    }
    @Test fun `legacy absent counters become zero while Long counters and unknown fields survive write`() {
        val data = YamlConfiguration(); assertEquals(GachaState(), GachaConfiguration.state(data))
        data.set("custom", "kept"); val state = GachaState(3_000_000_000, 10, 1, 2)
        GachaConfiguration.write(data, state); assertEquals(state, GachaConfiguration.state(data)); assertEquals("kept", data.getString("custom"))
    }
    @Test fun `negative fractional and textual counters fail instead of converting to zero`() {
        for (raw in listOf(-1, 1.5, "100")) {
            val data = YamlConfiguration(); data.set("LEGENDARY.Count", raw)
            assertThrows(IllegalArgumentException::class.java) { GachaConfiguration.state(data) }
        }
    }
}
