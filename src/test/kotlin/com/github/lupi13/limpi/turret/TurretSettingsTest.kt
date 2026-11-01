package com.github.lupi13.limpi.turret

import com.github.lupi13.limpi.config.AbilitySpecs
import com.github.lupi13.limpi.config.specs.TurretSpecs
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TurretSettingsTest {
    @Test fun `turret settings default to the requested values without prefixes`() {
        val (settings, _) = AbilitySpecs.all.getValue("turret").prepare(YamlConfiguration())
        assertEquals(20, settings.getInt("cooldown_ticks"))
        assertFalse(settings.getBoolean("allow_in_others_estate"))
        assertEquals(TurretStats(1, 1, 20, 4.0, 30.0, 1.0, 16.0), IronTurretKind.stats(settings, ""))
        assertEquals(Material.IRON_INGOT, IronTurretKind.trigger)
    }

    @Test fun `an ability with several kinds prefixes each kind's keys by its id`() {
        val gold = object : TurretKind("gold", Material.GOLD_INGOT, "금 주괴", DyeColor.YELLOW, Material.GOLD_NUGGET,
            defaults = TurretStats(2, 3, 10, 6.0, 40.0, 2.0, 12.0)) {}
        val kinds = listOf(IronTurretKind, gold)
        assertEquals("iron", TurretSpecs.prefix(kinds, IronTurretKind))
        assertEquals("", TurretSpecs.prefix(listOf(IronTurretKind), IronTurretKind))
        val spec = TurretSpecs.spec(kinds)
        val (settings, _) = spec.prepare(YamlConfiguration().apply { set("gold_damage", 7.5) })
        assertEquals(TurretStats(1, 1, 20, 4.0, 30.0, 1.0, 16.0), IronTurretKind.stats(settings, "iron"))
        assertEquals(TurretStats(2, 3, 10, 7.5, 40.0, 2.0, 12.0), gold.stats(settings, "gold"))
        assertTrue(spec.fields.map { it.key }.containsAll(listOf("cooldown_ticks", "allow_in_others_estate", "iron_ingot_cost", "gold_redstone_cost")))
    }

    @Test fun `invalid turret values are rejected`() {
        val spec = AbilitySpecs.all.getValue("turret")
        for ((key, value) in listOf("ingot_cost" to 0, "fire_interval_ticks" to 0, "max_health" to 0.0, "damage" to -1.0, "range" to 0.5, "range" to 65.0)) {
            assertThrows(IllegalArgumentException::class.java, { spec.prepare(YamlConfiguration().apply { set(key, value) }) }, key)
        }
    }
}
