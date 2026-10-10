package com.github.lupi13.limpi.storage

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

interface ProfilePersistence {
    fun read(id: UUID): YamlConfiguration
    fun write(id: UUID, payload: String)
    fun names(): Map<UUID, String>
    fun backupLegacy(id: UUID)
}

class YamlProfilePersistence(
    private val directory: Path,
    private val writer: (Path, String) -> Unit = AtomicFiles::write,
) : ProfilePersistence {
    init { Files.createDirectories(directory) }
    override fun read(id: UUID): YamlConfiguration = YamlFiles.read(file(id))
    override fun write(id: UUID, payload: String) = writer(file(id), payload)
    override fun names(): Map<UUID, String> = buildMap {
        Files.newDirectoryStream(directory, "*.yml").use { files ->
            for (file in files) {
                val id = runCatching { UUID.fromString(file.fileName.toString().removeSuffix(".yml")) }.getOrNull() ?: continue
                YamlFiles.read(file).getString("name")?.let { put(id, it) }
            }
        }
    }
    override fun backupLegacy(id: UUID) {
        if (!Files.exists(file(id))) return
        val backup = directory.resolve("backups/$id.pre-schema-1.yml")
        if (!Files.exists(backup)) AtomicFiles.write(backup, Files.readString(file(id)))
    }
    private fun file(id: UUID) = directory.resolve("$id.yml")
}

class SqliteProfilePersistence(private val store: StateStore) : ProfilePersistence {
    override fun read(id: UUID): YamlConfiguration = YamlFiles.parse(store.get(key(id)))
    override fun write(id: UUID, payload: String) = store.atomic { it.put(key(id), payload) }
    override fun names(): Map<UUID, String> = store.documents("player/").mapNotNull { (key, payload) ->
        YamlFiles.parse(payload).getString("name")?.let { UUID.fromString(key.removePrefix("player/")) to it }
    }.toMap()
    override fun backupLegacy(id: UUID) = Unit // The importer backs up every source before importing anything.
    companion object { fun key(id: UUID) = "player/$id" }
}
