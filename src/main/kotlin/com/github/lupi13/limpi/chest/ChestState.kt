package com.github.lupi13.limpi.chest

import com.github.lupi13.limpi.delivery.InventoryImages
import com.github.lupi13.limpi.delivery.StoredStack
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/** A player's purchased storage. rows == 0 means the chest has not been unlocked yet. */
data class ChestState(val rows: Int, val autoOwned: Boolean, val autoEnabled: Boolean, val ender: Boolean,
                      val slots: List<StoredStack?>) {
    init {
        require(rows in 0..MAX_ROWS) { "Invalid chest rows $rows" }
        require(slots.size == rows * 9) { "Chest slots do not match its rows" }
        require(rows > 0 || (!autoOwned && !ender)) { "Features require an unlocked chest" }
        require(!autoEnabled || autoOwned) { "Auto store is enabled without being bought" }
    }
    val unlocked: Boolean get() = rows > 0

    companion object {
        const val MAX_ROWS = 6
        val EMPTY = ChestState(0, false, false, false, emptyList())
    }
}

enum class ChestProduct { UNLOCK, UPGRADE, AUTO_STORE, ENDER }

data class ChestPrices(val unlock: Money, val upgrades: List<Money>, val autoStore: Money, val ender: Money) {
    init { require(upgrades.size == ChestState.MAX_ROWS - 1) { "Chest upgrade prices must cover rows 2..${ChestState.MAX_ROWS}" } }

    /** Price of buying [product] now, or null when it is not available for [state] (no refunds, no downgrades). */
    fun quote(state: ChestState, product: ChestProduct): Money? = when (product) {
        ChestProduct.UNLOCK -> unlock.takeIf { !state.unlocked }
        ChestProduct.UPGRADE -> if (state.rows in 1 until ChestState.MAX_ROWS) upgrades[state.rows - 1] else null
        ChestProduct.AUTO_STORE -> autoStore.takeIf { state.unlocked && !state.autoOwned }
        ChestProduct.ENDER -> ender.takeIf { state.unlocked && !state.ender }
    }

    companion object {
        val DEFAULT = ChestPrices(Money(10_000), listOf(20_000L, 40_000L, 80_000L, 160_000L, 320_000L).map(::Money),
            Money(50_000), Money(1_000_000))

        /** Strict integer prices; a malformed value refuses purchases instead of guessing a price. */
        fun read(config: ConfigurationSection): ChestPrices {
            fun money(path: String): Money = when (val value = config.get(path)) {
                is Byte, is Short, is Int, is Long -> (value as Number).toLong().also { require(it >= 0) { "$path must not be negative" } }.let(::Money)
                else -> throw IllegalArgumentException("$path must be an integer")
            }
            val upgrades = requireNotNull(config.getList("ChestUpgradePrice")) { "ChestUpgradePrice must be a list" }.map { value ->
                when (value) {
                    is Byte, is Short, is Int, is Long -> (value as Number).toLong().also { require(it >= 0) { "ChestUpgradePrice must not be negative" } }.let(::Money)
                    else -> throw IllegalArgumentException("ChestUpgradePrice must contain integers")
                }
            }
            return ChestPrices(money("ChestUnlockPrice"), upgrades, money("ChestAutoStorePrice"), money("ChestEnderPrice"))
        }
    }
}

/** chest/<owner>: unknown fields are preserved; a damaged document is refused, never reset. */
object ChestDocuments {
    fun key(owner: UUID) = "chest/$owner"

    fun decode(owner: UUID, payload: String?): Pair<YamlConfiguration, ChestState> {
        if (payload == null) return YamlConfiguration() to ChestState.EMPTY
        val data = YamlFiles.parse(payload)
        require(data.get("schemaVersion") == 1) { "Unsupported chest schema" }
        require(data.getString("owner") == owner.toString()) { "Chest owner mismatch" }
        fun flag(name: String): Boolean = data.get(name) as? Boolean ?: throw IllegalArgumentException("Invalid chest field $name")
        val rows = when (val value = data.get("rows")) {
            is Byte, is Short, is Int, is Long -> Math.toIntExact((value as Number).toLong())
            else -> throw IllegalArgumentException("Invalid chest rows")
        }
        val slots = InventoryImages.decode(requireNotNull(data.getList("slots")) { "Missing chest slots" })
        return data to ChestState(rows, flag("autoOwned"), flag("autoEnabled"), flag("ender"), slots)
    }

    fun encode(owner: UUID, data: YamlConfiguration, state: ChestState): String = data.apply {
        set("schemaVersion", 1); set("owner", owner.toString()); set("rows", state.rows)
        set("autoOwned", state.autoOwned); set("autoEnabled", state.autoEnabled); set("ender", state.ender)
        set("slots", InventoryImages.encode(state.slots))
    }.saveToString()
}

object ChestSlots {
    /** Merge into matching stacks first, then empty slots; returns the new slots and how many items fit. */
    fun insert(slots: List<StoredStack?>, payload: String, count: Int, limit: Int): Pair<List<StoredStack?>, Int> {
        require(payload.isNotBlank() && count > 0 && limit > 0)
        val after = slots.toMutableList(); var remaining = count
        for (slot in after.indices) {
            val stack = after[slot] ?: continue
            if (remaining == 0) break
            if (stack.payload != payload || stack.limit != limit) continue
            val moved = minOf(remaining, stack.limit - stack.count)
            if (moved > 0) { after[slot] = stack.copy(count = stack.count + moved); remaining -= moved }
        }
        for (slot in after.indices) {
            if (remaining == 0) break
            if (after[slot] != null) continue
            val moved = minOf(remaining, limit)
            after[slot] = StoredStack(payload, moved, limit); remaining -= moved
        }
        return after.toList() to count - remaining
    }
}
