package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/** Canonical account reads for transactions; never use a potentially stale player cache here. */
object Accounts {
    fun read(tx: StateTransaction, id: UUID): YamlConfiguration {
        val payload = tx.get(SqliteProfilePersistence.key(id)) ?: throw BankException(BankFailure.UNKNOWN_PLAYER)
        return YamlFiles.parse(payload).also { PlayerSchema.validate(it, id.toString()) }
    }

    fun integralBalance(data: FileConfiguration): Long = when (val value = data.get("money")) {
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        else -> throw BankException(BankFailure.INVALID_ACCOUNT)
    }

    fun balance(data: FileConfiguration): Money {
        val value = integralBalance(data)
        if (value < 0) throw BankException(BankFailure.INVALID_ACCOUNT)
        return Money(value)
    }

    fun write(tx: StateTransaction, id: UUID, data: FileConfiguration, balance: Money) {
        data.set("money", balance.units)
        tx.put(SqliteProfilePersistence.key(id), data.saveToString())
    }
}
