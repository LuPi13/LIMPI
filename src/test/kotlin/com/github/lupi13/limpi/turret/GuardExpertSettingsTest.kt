package com.github.lupi13.limpi.turret

import com.github.lupi13.limpi.config.AbilitySpecs
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuardExpertSettingsTest {
    private val spec = AbilitySpecs.all.getValue("guard_expert")

    @Test fun `guard expert defaults per kind with id prefixes`() {
        val (settings, _) = spec.prepare(YamlConfiguration())
        assertEquals(10, settings.getInt("cooldown_ticks"))
        assertEquals(TurretStats(1, 1, 20, 4.0, 30.0, 1.0, 16.0), IronTurretKind.stats(settings, "iron"))
        assertEquals(TurretStats(1, 1, 40, 2.0, 40.0, 1.0, 2.0, mapOf("burn_seconds" to 3.0, "armor" to 10.0)), CopperTurretKind.stats(settings, "copper"))
        assertEquals(10.0, CopperTurretKind.armor(CopperTurretKind.stats(settings, "copper")))
        assertEquals(0.0, IronTurretKind.armor(IronTurretKind.stats(settings, "iron")))
        val gold = GoldTurretKind.stats(settings, "gold")
        assertEquals(30.0, gold.maxHealth); assertEquals(1.0, gold.decayPerSecond)
        assertEquals(0.5, GoldTurretKind.speedBonus(gold))
    }

    @Test fun `support turrets have no attack keys and kinds keep their own extra keys`() {
        val keys = spec.fields.map { it.key }.toSet()
        assertTrue(keys.containsAll(listOf("copper_burn_seconds", "copper_armor", "gold_speed_bonus", "gold_max_health", "copper_range", "iron_range")))
        for (attackKey in listOf("gold_fire_interval_ticks", "gold_damage", "gold_range", "iron_burn_seconds", "gold_burn_seconds", "iron_armor", "gold_armor")) {
            assertFalse(attackKey in keys, attackKey)
        }
        assertFalse(GoldTurretKind.attacks); assertTrue(CopperTurretKind.attacks); assertTrue(IronTurretKind.attacks)
        assertEquals(0.0, IronTurretKind.speedBonus(IronTurretKind.defaults))
    }

    @Test fun `edited kind values are read back and out of range values are rejected`() {
        val (settings, _) = spec.prepare(YamlConfiguration().apply { set("copper_burn_seconds", 5.5); set("gold_speed_bonus", 0.75) })
        assertEquals(5.5, CopperTurretKind.stats(settings, "copper").extra(CopperTurretKind.BURN))
        assertEquals(0.75, GoldTurretKind.speedBonus(GoldTurretKind.stats(settings, "gold")))
        assertThrows(IllegalArgumentException::class.java) { spec.prepare(YamlConfiguration().apply { set("gold_speed_bonus", -0.1) }) }
        assertThrows(IllegalArgumentException::class.java) { spec.prepare(YamlConfiguration().apply { set("copper_range", 0.5) }) }
    }
}
