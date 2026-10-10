package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.DeliveryJournal
import com.github.lupi13.limpi.delivery.ItemGrant
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class InitialTicketCounts(val gacha: Long, val common: Long, val rare: Long, val epic: Long) {
    init { require(listOf(gacha, common, rare, epic).all { it >= 0 }) }
    companion object {
        fun read(config: ConfigurationSection): InitialTicketCounts {
            fun count(path: String): Long {
                val value = config.get(path); require(value is Int || value is Long) { "$path must be an integer" }
                return (value as Number).toLong().also { require(it >= 0) { "$path must not be negative" } }
            }
            return InitialTicketCounts(count("InitialTicket"), count("InitialCOMMON"), count("InitialRARE"), count("InitialEPIC"))
        }
    }
}

/** New plugin profiles receive a frozen once-only bundle; a profile that already existed is never granted it. */
class InitialRewards(private val store: StateStore, private val deliveries: DeliveryJournal, private val players: PlayerRepository) {
    fun register(owner: UUID, name: String, balance: Money, items: List<ItemGrant>) {
        val id = identity("initial-register", owner)
        val result = store.execute(id, "initial-register:$owner") { tx ->
            val key = SqliteProfilePersistence.key(owner)
            val existing = tx.get(key)
            if (existing == null) tx.put(key, YamlConfiguration().apply {
                set("schemaVersion", 1); set("name", name); set("money", balance.units)
            }.saveToString()) else Accounts.read(tx, owner)
            val status = YamlConfiguration().apply {
                set("schemaVersion", 1); set("owner", owner.toString()); set("eligible", existing == null)
                set("state", if (existing == null) "WAITING" else "EXEMPT")
                set("items", if (existing == null) items.map { mapOf("payload" to it.payload, "count" to it.count, "limit" to it.limit) } else emptyList<Any>())
            }.saveToString()
            tx.put(statusKey(owner), status); status
        }
        players.invalidate(result.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
    }
    fun claim(owner: UUID): Boolean {
        val status = status(store.get(statusKey(owner)), owner)
        if (status.getString("state") == "EXEMPT") return false
        val id = identity("initial-grant", owner)
        store.execute(id, "initial-grant:$owner") { tx ->
            Accounts.read(tx, owner)
            val current = status(tx.get(statusKey(owner)), owner)
            require(current.getString("state") == "WAITING" && current.get("eligible") == true)
            val items = requireNotNull(current.getList("items")).map { raw ->
                require(raw is Map<*, *>)
                val count = raw["count"]; val limit = raw["limit"]
                require((count is Int || count is Long) && (limit is Int || limit is Long))
                val maximum = (limit as Number).toLong(); require(maximum in 1..Int.MAX_VALUE.toLong())
                ItemGrant(raw["payload"] as? String ?: error("Missing initial item"), (count as Number).toLong(), maximum.toInt())
            }
            if (items.isNotEmpty()) deliveries.stage(tx, id, owner, "initial ability tickets", items)
            current.set("state", "GRANTED"); tx.put(statusKey(owner), current.saveToString())
            current.saveToString()
        }
        return true
    }
    private fun status(payload: String?, owner: UUID): YamlConfiguration {
        val data = YamlFiles.parse(requireNotNull(payload)); require(data.get("schemaVersion") == 1 && data.get("owner") == owner.toString())
        require(data.get("eligible") is Boolean && data.get("state") in setOf("WAITING", "GRANTED", "EXEMPT"))
        return data
    }
    companion object {
        private fun statusKey(owner: UUID) = "initial_rewards/$owner"
        private fun identity(kind: String, owner: UUID) = UUID.nameUUIDFromBytes("$kind:$owner".toByteArray(Charsets.UTF_8))
    }
}
