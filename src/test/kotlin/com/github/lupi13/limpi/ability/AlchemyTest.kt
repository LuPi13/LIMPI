package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlchemyTest {
    @Test fun `weighted boundaries skip zero weights and select the configured last outcome`() {
        val policy = AlchemyPolicy(linkedMapOf("coal" to 0.0, "diamond" to 0.25, "netherite_ingot" to 0.75, "gold" to 0.0))
        assertEquals("diamond", policy.choose(0.0)); assertEquals("diamond", policy.choose(0.249999))
        assertEquals("netherite_ingot", policy.choose(0.25)); assertEquals("netherite_ingot", policy.choose(Math.nextDown(1.0)))
        assertThrows(IllegalArgumentException::class.java) { policy.choose(1.0) }
        assertThrows(IllegalArgumentException::class.java) { AlchemyPolicy(mapOf("coal" to 0.5)) }
    }
}
