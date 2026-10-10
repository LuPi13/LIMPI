package com.github.lupi13.limpi.storage

import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

interface ProfilePersistence {
    fun read(id: UUID): YamlConfiguration
    fun write(id: UUID, payload: String)
    fun names(): Map<UUID, String>
}

class SqliteProfilePersistence(private val store: StateStore) : ProfilePersistence {
    override fun read(id: UUID): YamlConfiguration = YamlFiles.parse(store.get(key(id)))
    override fun write(id: UUID, payload: String) = store.atomic { it.put(key(id), payload) }
    override fun names(): Map<UUID, String> = store.documents("player/").mapNotNull { (key, payload) ->
        YamlFiles.parse(payload).getString("name")?.let { UUID.fromString(key.removePrefix("player/")) to it }
    }.toMap()
    companion object { fun key(id: UUID) = "player/$id" }
}
