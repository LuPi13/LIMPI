package com.github.lupi13.limpi.turret

import com.github.lupi13.limpi.config.AbilitySpecs
import com.github.lupi13.limpi.config.specs.TurretSpecs
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TurretChampionSettingsTest {
    private val spec = AbilitySpecs.all.getValue("turret_champion")

    @Test fun `champion has eight kinds with distinct triggers and ids`() {
        val kinds = TurretSpecs.CHAMPION_KINDS
        assertEquals(8, kinds.size)
        assertEquals(kinds.size, kinds.map { it.trigger }.toSet().size)
        assertEquals(kinds.size, kinds.map { it.id }.toSet().size)
        assertEquals(listOf(Material.DIAMOND, Material.ECHO_SHARD, Material.AMETHYST_SHARD, Material.SHULKER_SHELL, Material.EMERALD),
            listOf(DiamondTurretKind, EchoTurretKind, AmethystTurretKind, ShulkerTurretKind, EmeraldTurretKind).map { it.trigger })
    }

    @Test fun `champion defaults per kind`() {
        val (settings, _) = spec.prepare(YamlConfiguration())
        val diamond = DiamondTurretKind.stats(settings, "diamond")
        assertEquals(TurretStats(1, 2, 12, 8.0, 50.0, 0.5, 32.0, mapOf("pierce" to 1.0)), diamond)
        assertEquals(1, DiamondTurretKind.pierce(diamond))
        assertEquals(TurretStats(1, 2, 60, 8.0, 30.0, 0.1, 24.0), EchoTurretKind.stats(settings, "echo"))
        assertEquals(TurretStats(1, 1, 20, 1.0, 30.0, 1.0, 14.0, mapOf("slowness_seconds" to 3.0, "slowness_level" to 2.0)),
            AmethystTurretKind.stats(settings, "amethyst"))
        assertEquals(TurretStats(1, 2, 60, 5.0, 40.0, 0.25, 16.0, mapOf("levitation_seconds" to 5.0)), ShulkerTurretKind.stats(settings, "shulker"))
        val emerald = EmeraldTurretKind.stats(settings, "emerald")
        assertEquals(1.0, emerald.extra(EmeraldTurretKind.HEAL)); assertEquals(30.0, emerald.maxHealth); assertEquals(2, emerald.ingotCost)
        assertEquals(0.5, GoldTurretKind.speedBonus(GoldTurretKind.stats(settings, "gold")))
    }

    @Test fun `kind behaviours and support keys`() {
        assertFalse(EchoTurretKind.needsSight); assertTrue(DiamondTurretKind.needsSight)
        assertFalse(EmeraldTurretKind.attacks); assertFalse(GoldTurretKind.attacks); assertTrue(ShulkerTurretKind.attacks)
        assertEquals(0, IronTurretKind.pierce(IronTurretKind.defaults))
        val keys = spec.fields.map { it.key }.toSet()
        assertTrue(keys.containsAll(listOf("emerald_heal_per_second", "diamond_pierce", "amethyst_slowness_level", "shulker_levitation_seconds", "echo_range")))
        assertFalse("emerald_damage" in keys); assertFalse("emerald_fire_interval_ticks" in keys)
        assertThrows(IllegalArgumentException::class.java) { spec.prepare(YamlConfiguration().apply { set("amethyst_slowness_level", 0) }) }
    }

    @Test fun `echo beam hits boxes along the line to the end of its range and nothing beside it`() {
        val origin = org.bukkit.util.Vector(0.0, 0.0, 0.0)
        val direction = org.bukkit.util.Vector(1.0, 0.0, 0.0)
        fun hit(x: Double, z: Double) = org.bukkit.util.BoundingBox(x - 0.3, -0.9, z - 0.3, x + 0.3, 0.9, z + 0.3)
            .expand(EchoTurretKind.BEAM_RADIUS).rayTrace(origin, direction, 24.0) != null
        assertTrue(hit(5.0, 0.0)); assertTrue(hit(20.0, 0.5)) // 표적 너머도 사거리 끝까지
        assertFalse(hit(5.0, 1.5)); assertFalse(hit(-3.0, 0.0)); assertFalse(hit(30.0, 0.0))
    }

    @Test fun `emerald healing scales with gold speed`() {
        assertEquals(1.0, TurretHeal.amount(1.0, 1.0))
        assertEquals(1.5, TurretHeal.amount(1.0, 1.5))
        assertEquals(3.0, TurretHeal.amount(1.0, 3.0))
        assertEquals(0.0, TurretHeal.amount(-1.0, 2.0))
    }
}
