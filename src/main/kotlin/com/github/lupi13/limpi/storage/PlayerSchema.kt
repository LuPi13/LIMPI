package com.github.lupi13.limpi.storage

import org.bukkit.configuration.file.FileConfiguration
import java.math.BigInteger

/** Do not coerce malformed or future schema versions through Bukkit's numeric getters. */
object PlayerSchema {
    const val CURRENT = 1

    fun validate(data: FileConfiguration, description: String) {
        val version = when (val raw = data.get("schemaVersion")) {
            null -> 0L
            is Byte, is Short, is Int, is Long -> (raw as Number).toLong()
            is BigInteger -> raw.longValueExact()
            else -> throw IllegalArgumentException("Non-integral player schema version: $description")
        }
        require(version in 0L..CURRENT.toLong()) { "Unsupported player schema version $version: $description" }
    }
}
