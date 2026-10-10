package com.github.lupi13.limpi.combat

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EnemyPolicyTest {
    @Test fun `menu and targeting share default player team protection and explicit zero survives`() {
        val defaults = EnemySettings.read(YamlConfiguration())
        assertEquals(1, defaults.mode(TargetGroup.PLAYER)); assertEquals(2, defaults.mode(TargetGroup.TAMED))
        val explicit = EnemySettings.read(YamlConfiguration().apply { set("AbilityEnemy.PLAYER", 0L) })
        assertEquals(0, explicit.mode(TargetGroup.PLAYER))
    }
    @Test fun `player conditional targets only other teams and never itself`() {
        assertFalse(EnemyPolicy.hostile(TargetGroup.PLAYER, 1, sameTeam = true))
        assertTrue(EnemyPolicy.hostile(TargetGroup.PLAYER, 1, sameTeam = false))
        for (mode in 0..2) assertFalse(EnemyPolicy.hostile(TargetGroup.PLAYER, mode, self = true))
    }
    @Test fun `offline player owned pets obey the same owner policy as online pets`() {
        assertFalse(EnemyPolicy.hostile(TargetGroup.TAMED, 0, hasPlayerOwner = true))
        assertFalse(EnemyPolicy.hostile(TargetGroup.TAMED, 1, sameTeam = true, hasPlayerOwner = true))
        assertTrue(EnemyPolicy.hostile(TargetGroup.TAMED, 1, sameTeam = false, hasPlayerOwner = true))
        assertTrue(EnemyPolicy.hostile(TargetGroup.TAMED, 2, sameTeam = true, hasPlayerOwner = true))
    }
    @Test fun `mob categories respect no attack conditional hostility and all attack`() {
        for (group in listOf(TargetGroup.NON_AGGRESSIVE, TargetGroup.NEUTRAL, TargetGroup.AGGRESSIVE)) {
            assertFalse(EnemyPolicy.hostile(group, 0, targetingOwner = true))
            assertFalse(EnemyPolicy.hostile(group, 1, targetingOwner = false))
            assertTrue(EnemyPolicy.hostile(group, 1, targetingOwner = true))
            assertTrue(EnemyPolicy.hostile(group, 2))
        }
    }
    @Test fun `category cycle is limited to three valid values in either direction`() {
        assertEquals(0, EnemyPolicy.cycle(2, true)); assertEquals(2, EnemyPolicy.cycle(0, false))
        assertEquals(1, EnemyPolicy.cycle(0, true)); assertEquals(1, EnemyPolicy.cycle(2, false))
    }
    @Test fun `malformed or out of range stored choices are not silently rewritten`() {
        for (value in listOf("1", 1.5, -1, 3, Long.MAX_VALUE)) {
            val data = YamlConfiguration().apply { set("AbilityEnemy.TAMED", value) }
            assertThrows(IllegalArgumentException::class.java) { EnemySettings.read(data) }
            assertEquals(value, data.get("AbilityEnemy.TAMED"))
        }
    }
}
