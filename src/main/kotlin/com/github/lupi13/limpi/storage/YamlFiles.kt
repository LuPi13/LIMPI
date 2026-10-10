package com.github.lupi13.limpi.storage

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

object YamlFiles {
    fun parse(payload: String?): YamlConfiguration = YamlConfiguration().apply {
        if (payload != null) loadFromString(payload)
    }
    /** Unlike loadConfiguration, load propagates malformed YAML and I/O failures. */
    fun read(path: Path): YamlConfiguration = YamlConfiguration().apply {
        if (Files.exists(path)) load(path.toFile())
    }

    fun write(path: Path, configuration: FileConfiguration) {
        AtomicFiles.write(path, configuration.saveToString())
    }
}
