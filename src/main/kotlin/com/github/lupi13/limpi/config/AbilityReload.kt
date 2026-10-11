package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.storage.YamlFiles
import java.nio.file.Files
import java.nio.file.Path

object AbilityReload {
    /** No mutation, including file normalization, is allowed until every file has passed validation. */
    fun prepare(directory: Path, specs: Map<String, AbilitySpec>): Map<String, AbilitySettings> {
        val errors = mutableListOf<String>()
        val settings = linkedMapOf<String, AbilitySettings>()
        specs.forEach { (code, spec) ->
            val path = directory.resolve("$code.yml")
            try {
                require(Files.isRegularFile(path)) { "설정 파일이 없습니다." }
                settings[code] = spec.prepare(YamlFiles.read(path)).first
            } catch (failure: Exception) { errors += "${path.fileName}: ${failure.message}" }
        }
        require(errors.isEmpty()) { "능력 설정을 불러오지 못했습니다.\n${errors.joinToString("\n")}" }
        return settings
    }
}
