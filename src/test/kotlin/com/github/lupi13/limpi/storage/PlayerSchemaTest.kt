package com.github.lupi13.limpi.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlayerSchemaTest {
    @Test fun `missing and supported integral schemas are compatible`() {
        for (payload in listOf("", "schemaVersion: 0", "schemaVersion: 1")) {
            assertDoesNotThrow { PlayerSchema.validate(YamlFiles.parse(payload), "test profile") }
        }
    }

    @Test fun `fractional string negative and future schemas cannot be coerced to supported values`() {
        for (value in listOf("1.5", "'1'", "-1", "2", "4294967296", "9223372036854775808")) {
            assertThrows(Exception::class.java) { PlayerSchema.validate(YamlFiles.parse("schemaVersion: $value"), "test profile") }
        }
    }
}
