package com.github.lupi13.limpi.storage

import com.github.lupi13.limpi.player.PlayerProfile
import com.github.lupi13.limpi.player.PlayerRepository
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap

class ProfileConflictException(id: UUID, field: String) :
    IllegalStateException("Player $id changed while editing '$field'; reload before retrying")

/** Main-thread repository. Returned configurations are isolated edits, never the cache itself. */
open class CachedPlayerRepository(
    private val persistence: ProfilePersistence,
    private val offlineCacheSize: Int = 256,
) : PlayerRepository {
    private data class Edit(val id: UUID, val original: Map<String, Any?>)
    private val cache = LinkedHashMap<UUID, YamlConfiguration>(16, 0.75f, true)
    private val edits = WeakHashMap<FileConfiguration, Edit>()
    private val online = mutableSetOf<UUID>()
    private val names = mutableMapOf<String, MutableSet<UUID>>()
    private val indexedNames = mutableMapOf<UUID, String>()
    private val displayNames = mutableMapOf<UUID, String>()

    init {
        require(offlineCacheSize > 0)
        persistence.names().forEach { (id, name) -> indexName(id, name) }
    }

    override fun profile(id: UUID): PlayerProfile {
        val data = current(id)
        return PlayerProfile(id, data.getString("name"), runCatching { com.github.lupi13.limpi.economy.Accounts.integralBalance(data) }.getOrNull(),
            data.getString("ability")?.takeIf { it.isNotBlank() }, data.getStringList("unlockedAbilities").toSet())
    }

    override fun abilityCode(id: UUID): String? = current(id).getString("ability")?.takeIf { it.isNotBlank() }

    override fun <T> read(id: UUID, view: (ConfigurationSection) -> T): T = view(current(id))

    override fun snapshot(id: UUID): FileConfiguration {
        val values = flatten(current(id))
        val snapshot = fromValues(values)
        edits[snapshot] = Edit(id, values)
        return snapshot
    }

    override fun save(id: UUID, snapshot: FileConfiguration) {
        val edit = requireNotNull(edits[snapshot]) { "Only a repository snapshot may be saved" }
        require(edit.id == id) { "A player snapshot cannot be saved under another UUID" }
        val proposed = flatten(snapshot)
        val latest = flatten(current(id))
        val merged = latest.toMutableMap()
        for (key in edit.original.keys + proposed.keys) {
            val before = edit.original[key]
            val after = proposed[key]
            if (sameValue(before, after)) continue
            if (!sameValue(latest[key], before) && !sameValue(latest[key], after)) throw ProfileConflictException(id, key)
            if (after == null) merged.remove(key) else merged[key] = after
        }
        if (!sameValue(merged, latest)) persist(id, fromValues(merged))
        // Rebase this edit after a successful save so subsequent edits keep the same contract.
        for (key in snapshot.getKeys(false)) snapshot.set(key, null)
        merged.forEach { (key, value) -> snapshot.set(key, copyValue(value)) }
        edits[snapshot] = Edit(id, merged.mapValues { copyValue(it.value) })
    }

    override fun ensure(id: UUID, name: String, initialBalance: Long) {
        require(initialBalance >= 0) { "InitialMoney must not be negative" }
        val snapshot = snapshot(id)
        PlayerSchema.validate(snapshot, id.toString())
        // Only a brand-new profile receives the initial balance. An existing profile without money is damaged and stays
        // visible ("계좌 복구 필요") for an audited /financial set instead of silently receiving InitialMoney.
        val created = snapshot.getKeys(false).isEmpty()
        if (created) snapshot.set("money", initialBalance)
        val defaults = mapOf(
            "schemaVersion" to 1,
            "LEGENDARY.Count" to 0, "LEGENDARY.Ceiling" to 0,
            "EPIC.Count" to 0, "EPIC.Ceiling" to 0,
            "AbilityEnemy.TAMED" to 0, "AbilityEnemy.NON_AGGRESSIVE" to 2,
            "AbilityEnemy.NEUTRAL" to 1, "AbilityEnemy.AGGRESSIVE" to 2, "AbilityEnemy.PLAYER" to 1,
        )
        defaults.forEach { (key, value) -> if (!snapshot.contains(key)) snapshot.set(key, value) }
        snapshot.set("name", name)
        save(id, snapshot)
    }

    override fun findByName(name: String): UUID? = names[name.lowercase(Locale.ROOT)]?.singleOrNull()
    override fun knownNames(): List<String> = displayNames.values.distinct().sorted()

    override fun pin(id: UUID) { online.add(id); current(id) }
    override fun release(id: UUID) { online.remove(id); cache.remove(id) }
    override fun invalidate(ids: Collection<UUID>) { ids.forEach { cache.remove(it) } }
    override fun close() { cache.clear(); edits.clear(); online.clear(); names.clear(); indexedNames.clear(); displayNames.clear() }

    private fun current(id: UUID): YamlConfiguration {
        val data = cache.getOrPut(id) { persistence.read(id) }
        trimCache(id)
        return data
    }

    private fun persist(id: UUID, data: YamlConfiguration) {
        try {
            persistence.write(id, data.saveToString())
        } catch (failure: Exception) {
            // A commit may be ambiguous: read canonical persistence again before any later operation.
            cache.remove(id)
            throw failure
        }
        cache[id] = data
        indexName(id, data.getString("name"))
        trimCache(id)
    }

    /** Runs on every profile read, so the common case (within the limit) is answered by counting only online players. */
    private fun trimCache(requested: UUID) {
        if (cache.size <= offlineCacheSize) return
        var excess = cache.size - online.count(cache::containsKey) - offlineCacheSize
        if (excess <= 0) return
        // Least recently used first, as before; the profile being read is kept.
        val keys = cache.keys.iterator()
        while (excess > 0 && keys.hasNext()) {
            val key = keys.next()
            if (key !in online && key != requested) { keys.remove(); excess-- }
        }
    }

    private fun indexName(id: UUID, name: String?) {
        if (name == null) displayNames.remove(id) else displayNames[id] = name
        indexedNames.remove(id)?.let { previous ->
            names[previous]?.remove(id)
            if (names[previous].isNullOrEmpty()) names.remove(previous)
        }
        name?.lowercase(Locale.ROOT)?.let { names.getOrPut(it) { mutableSetOf() }.add(id); indexedNames[id] = it }
    }

    private fun flatten(data: FileConfiguration): Map<String, Any?> = data.getValues(true)
        .filterValues { it !is ConfigurationSection || it.getKeys(false).isEmpty() }
        .mapValues { if (it.value is ConfigurationSection) emptyMap<String, Any?>() else copyValue(it.value) }

    private fun fromValues(values: Map<String, Any?>) = YamlConfiguration().apply {
        values.forEach { (key, value) -> set(key, copyValue(value)) }
    }

    private fun copyValue(value: Any?): Any? = when (value) {
        is ItemStack -> value.clone()
        is List<*> -> value.map { copyValue(it) }
        is Map<*, *> -> value.mapValues { copyValue(it.value) }
        is ByteArray -> value.clone()
        is IntArray -> value.clone()
        is LongArray -> value.clone()
        else -> value
    }

    private fun sameValue(left: Any?, right: Any?): Boolean = when {
        left is ByteArray && right is ByteArray -> left.contentEquals(right)
        left is IntArray && right is IntArray -> left.contentEquals(right)
        left is LongArray && right is LongArray -> left.contentEquals(right)
        left is List<*> && right is List<*> -> left.size == right.size &&
            left.indices.all { sameValue(left[it], right[it]) }
        left is Map<*, *> && right is Map<*, *> -> left.keys == right.keys &&
            left.keys.all { sameValue(left[it], right[it]) }
        // Saved values may be Long in cache but Int after a DB re-read; compare integral numbers by value.
        left is Number && right is Number && left !is Double && left !is Float && right !is Double && right !is Float ->
            left.toLong() == right.toLong()
        else -> left == right
    }
}
