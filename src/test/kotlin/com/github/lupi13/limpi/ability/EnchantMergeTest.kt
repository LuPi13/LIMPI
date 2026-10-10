package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EnchantMergeTest {
    @Test fun `highest level wins regardless of input order and incompatible keys are kept`() {
        val result = mapOf("sharpness" to 5)
        assertEquals(mapOf("sharpness" to 5), EnchantMerge.highest(result, mapOf("sharpness" to 4), mapOf("sharpness" to 4)))
        assertEquals(mapOf("sharpness" to 6, "smite" to 3),
            EnchantMerge.highest(mapOf("sharpness" to 5), mapOf("sharpness" to 6), mapOf("sharpness" to 5, "smite" to 3)))
        assertEquals(mapOf("sharpness" to 5), EnchantMerge.highest(emptyMap(), mapOf("sharpness" to 5), mapOf("sharpness" to 3)))
        assertThrows(IllegalArgumentException::class.java) { EnchantMerge.highest(mapOf("sharpness" to 0)) }
    }
}
