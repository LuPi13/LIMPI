package com.github.lupi13.limpi.checks

import com.github.lupi13.limpi.economy.BankService
import kotlin.math.absoluteValue

data class LegacyCheck(val amount: Long, val issuerName: String, val date: String, val publicKey: String, val serial: String) {
    val fingerprint get() = BankService.fingerprint("LEGACY_CHECK", amount.toString(), issuerName, date, publicKey, serial)
    companion object {
        /** Format validation only; the old public hash cannot authenticate a server-issued liability. */
        fun parse(name: String, lore: List<String>): LegacyCheck? = runCatching {
            val amount = Regex("([0-9]+)원 수표").matchEntire(name)?.groupValues?.get(1)?.toLong() ?: return null
            require(amount > 0 && lore.size >= 5)
            fun field(index: Int, prefix: String): String {
                require(lore[index].startsWith(prefix)); return lore[index].removePrefix(prefix).also { require(it.isNotBlank()) }
            }
            val issuer = field(1, "발행인: "); val date = field(2, "발행일: ")
            val key = field(3, "공개키: "); val serial = field(4, "시리얼: ")
            var hash = 0L
            for (value in listOf(amount.toString(), issuer, date, key)) { hash = hash xor value.hashCode().toLong(); hash *= 127 }
            require(hash.absoluteValue.toString() == serial)
            LegacyCheck(amount, issuer, date, key, serial)
        }.getOrNull()
    }
}
