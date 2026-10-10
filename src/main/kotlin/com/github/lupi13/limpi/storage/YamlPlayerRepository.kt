package com.github.lupi13.limpi.storage

import com.github.lupi13.limpi.player.PlayerProfile
import com.github.lupi13.limpi.player.PlayerRepository
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap

class ProfileConflictException(id: UUID, field: String) :
    IllegalStateException("Player $id changed while editing '$field'; reload before retrying")

/** Main-thread repository. Returned configurations are isolated edits, never the cache itself. */
class YamlPlayerRepository(
    private val directory: Path,
    private val write: (Path, String) -> Unit = AtomicFiles::write,
    private val offlineCacheSize: Int = 256,
) : PlayerRepository {
    private data class Edit(val id: UUID, val original: Map<String, Any?>)
    private val cache = LinkedHashMap<UUID, YamlConfiguration>(16, 0.75f, true)
    private val edits = WeakHashMap<FileConfiguration, Edit>()
    private val online = mutableSetOf<UUID>()
    private val names = mutableMapOf<String, UUID>()
    private val indexedNames = mutableMapOf<UUID, String>()

    init {
        require(offlineCacheSize > 0)
        Files.createDirectories(directory)
        Files.newDirectoryStream(directory, "*.yml").use { files ->
            for (file in files) {
                val id = runCatching { UUID.fromString(file.fileName.toString().removeSuffix(".yml")) }.getOrNull()
                    ?: continue
                indexName(id, YamlFiles.read(file).getString("name"))
            }
        }
    }

    override fun profile(id: UUID): PlayerProfile {
        val data = current(id)
        return PlayerProfile(id, data.getString("name"), data.getLong("money"),
            data.getString("ability")?.takeIf { it.isNotBlank() }, data.getStringList("unlockedAbilities").toSet())
    }

    override fun abilityCode(id: UUID): String? = current(id).getString("ability")?.takeIf { it.isNotBlank() }

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
        require(snapshot.getInt("schemaVersion", 0) <= 1) { "Unsupported player schema version for $id" }
        if (!snapshot.contains("schemaVersion") && Files.exists(file(id))) {
            val backup = directory.resolve("backups/$id.pre-schema-1.yml")
            if (!Files.exists(backup)) AtomicFiles.write(backup, Files.readString(file(id)))
        }
        val defaults = mapOf(
            "schemaVersion" to 1, "money" to initialBalance,
            "LEGENDARY.Count" to 0, "LEGENDARY.Ceiling" to 0,
            "EPIC.Count" to 0, "EPIC.Ceiling" to 0,
            "AbilityEnemy.TAMED" to 0, "AbilityEnemy.NON_AGGRESSIVE" to 2,
            "AbilityEnemy.NEUTRAL" to 1, "AbilityEnemy.AGGRESSIVE" to 2, "AbilityEnemy.PLAYER" to 1,
        )
        defaults.forEach { (key, value) -> if (!snapshot.contains(key)) snapshot.set(key, value) }
        snapshot.set("name", name)
        save(id, snapshot)
    }

    override fun findByName(name: String): UUID? = names[name.lowercase(Locale.ROOT)]

    override fun pin(id: UUID) { online.add(id); current(id) }
    override fun release(id: UUID) { online.remove(id); cache.remove(id) }
    override fun close() { cache.clear(); edits.clear(); online.clear(); names.clear(); indexedNames.clear() }

    private fun current(id: UUID): YamlConfiguration {
        val data = cache.getOrPut(id) { YamlFiles.read(file(id)) }
        trimCache(id)
        return data
    }

    private fun persist(id: UUID, data: YamlConfiguration) {
        write(file(id), data.saveToString()) // A failed write cannot publish changes into the cache.
        cache[id] = data
        indexName(id, data.getString("name"))
        trimCache(id)
    }

    private fun trimCache(requested: UUID) {
        val offline = cache.keys.filter { it !in online && it != requested }
        val excess = cache.keys.count { it !in online } - offlineCacheSize.coerceAtLeast(1)
        offline.take(excess.coerceAtLeast(0)).forEach { cache.remove(it) }
    }

    private fun indexName(id: UUID, name: String?) {
        indexedNames.remove(id)?.let { previous -> if (names[previous] == id) names.remove(previous) }
        name?.lowercase(Locale.ROOT)?.let { names[it] = id; indexedNames[id] = it }
    }

    private fun file(id: UUID) = directory.resolve("$id.yml")

    private fun flatten(data: FileConfiguration): Map<String, Any?> = data.getValues(true)
        .filterValues { it !is ConfigurationSection }.mapValues { copyValue(it.value) }

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
        else -> left == right
    }
}
