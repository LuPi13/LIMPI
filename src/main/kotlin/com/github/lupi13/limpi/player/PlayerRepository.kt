package com.github.lupi13.limpi.player

import org.bukkit.configuration.file.FileConfiguration
import java.util.UUID

interface PlayerRepository {
    fun profile(id: UUID): PlayerProfile
    fun abilityCode(id: UUID): String?
    fun snapshot(id: UUID): FileConfiguration
    fun save(id: UUID, snapshot: FileConfiguration)
    fun ensure(id: UUID, name: String, initialBalance: Long)
    fun findByName(name: String): UUID?
    fun knownNames(): List<String>
    fun pin(id: UUID)
    fun release(id: UUID)
    fun invalidate(ids: Collection<UUID>)
    fun close()
}
