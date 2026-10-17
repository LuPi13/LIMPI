package com.github.lupi13.limpi.player

import org.bukkit.configuration.file.FileConfiguration
import java.util.UUID

interface PlayerRepository {
    fun profile(id: UUID): PlayerProfile
    fun abilityCode(id: UUID): String?
    fun snapshot(id: UUID): FileConfiguration
    /** Read a few values without copying the profile. [view] must neither modify nor keep the section. */
    fun <T> read(id: UUID, view: (org.bukkit.configuration.ConfigurationSection) -> T): T
    fun save(id: UUID, snapshot: FileConfiguration)
    fun ensure(id: UUID, name: String, initialBalance: Long)
    fun findByName(name: String): UUID?
    fun knownNames(): List<String>
    fun pin(id: UUID)
    fun release(id: UUID)
    fun invalidate(ids: Collection<UUID>)
    fun close()
}
