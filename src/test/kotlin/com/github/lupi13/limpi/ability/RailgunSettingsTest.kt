package com.github.lupi13.limpi.ability

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RailgunSettingsTest {
    private fun data() = YamlConfiguration().apply {
        set("shootingDelayTicks", 0L); set("damage", 10); set("velocity", 2.0)
        set("accelerateTimeTicks", 2.5); set("size", 1.0); set("lifeTimeTicks", 20)
    }
    @Test fun `zero charge delay and fractional acceleration remain supported`() {
        val settings = RailgunSettings.read(data(), false)
        assertEquals(0, settings.delay); assertEquals(2.5, settings.acceleration); assertEquals(0.0, settings.heal)
    }
    @Test fun `invalid physics fail before a projectile or modifier is created`() {
        for ((key, value) in listOf("velocity" to 0.0, "size" to Double.NaN, "damage" to -1.0, "accelerateTimeTicks" to Double.POSITIVE_INFINITY, "lifeTimeTicks" to 0, "shootingDelayTicks" to 1.5)) {
            assertThrows(IllegalArgumentException::class.java) { RailgunSettings.read(data().apply { set(key, value) }, false) }
        }
    }
    @Test fun `total lifetime overflow is rejected`() {
        assertThrows(ArithmeticException::class.java) { RailgunSettings.read(data().apply { set("shootingDelayTicks", Int.MAX_VALUE) }, false) }
    }
    @Test fun `healing ray requires a finite nonnegative heal`() {
        assertThrows(IllegalStateException::class.java) { RailgunSettings.read(data(), true) }
        assertThrows(IllegalArgumentException::class.java) { RailgunSettings.read(data().apply { set("heal", -2) }, true) }
        assertEquals(3.0, RailgunSettings.read(data().apply { set("heal", 3) }, true).heal)
    }
}
