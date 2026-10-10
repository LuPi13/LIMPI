package com.github.lupi13.limpi.checks

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.absoluteValue

class LegacyCheckTest {
    private fun lore(amount: String = "500", issuer: String = "OldName"): List<String> {
        val fields = listOf(amount, issuer, "2026-10-01", "public")
        var hash = 0L
        for (field in fields) { hash = hash xor field.hashCode().toLong(); hash *= 127 }
        return listOf("사용 안내", "발행인: $issuer", "발행일: 2026-10-01", "공개키: public", "시리얼: ${hash.absoluteValue}")
    }

    @Test fun `valid legacy format yields stable semantic identity regardless of instructions`() {
        val first = LegacyCheck.parse("500원 수표", lore())!!
        val copied = LegacyCheck.parse("500원 수표", lore().toMutableList().apply { this[0] = "다른 사용 안내" })!!
        assertEquals(500, first.amount); assertEquals(first.fingerprint, copied.fingerprint)
        assertNotEquals(first.fingerprint, LegacyCheck.parse("500원 수표", lore(issuer = "Renamed"))!!.fingerprint)
    }

    @Test fun `malformed amounts fields and hash are rejected without exceptions`() {
        for (name in listOf("0원 수표", "-1원 수표", "1.5원 수표", "9223372036854775808원 수표", "500원 수표 fake")) assertNull(LegacyCheck.parse(name, lore()))
        assertNull(LegacyCheck.parse("500원 수표", emptyList()))
        assertNull(LegacyCheck.parse("500원 수표", lore().toMutableList().apply { this[1] = "발행인: " }))
        assertNull(LegacyCheck.parse("500원 수표", lore().toMutableList().apply { this[4] = "시리얼: 1" }))
    }

    @Test fun `public hash can be recomputed for invented values so format is never issuance authentication`() {
        val invented = LegacyCheck.parse("9000000000원 수표", lore("9000000000", "invented issuer"))
        assertNotNull(invented); assertEquals(9_000_000_000, invented!!.amount)
        // Recognition only supports the operator review path; it does not create a server check record.
    }
}
