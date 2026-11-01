package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.quests.QuestProgressDefinitions
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MedicSettingsTest {
    @Test fun `first aid and combat medic defaults`() {
        val (firstAid, _) = AbilitySpecs.all.getValue("first_aid").prepare(YamlConfiguration())
        assertEquals(4.0, firstAid.getDouble("heal_amount")); assertEquals(4.0, firstAid.getDouble("heal_seconds"))
        assertEquals(100, firstAid.getInt("cooldown_ticks"))
        val (medic, _) = AbilitySpecs.all.getValue("combat_medic").prepare(YamlConfiguration())
        assertEquals(2.0, medic.getDouble("throw_speed_multiplier")); assertEquals(4.0, medic.getDouble("heal_amount"))
        assertEquals(50, QuestProgressDefinitions.FIRST_AID_OTHERS.target)
    }

    @Test fun `invalid medic values are rejected`() {
        val spec = AbilitySpecs.all.getValue("combat_medic")
        for ((key, value) in listOf("heal_amount" to -1.0, "heal_seconds" to 61.0, "cooldown_ticks" to -1, "throw_speed_multiplier" to 11.0)) {
            assertThrows(IllegalArgumentException::class.java, { spec.prepare(YamlConfiguration().apply { set(key, value) }) }, key)
        }
    }
}
