package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

enum class SettingType { INTEGER, NUMBER, FLAG }
data class AbilityField(val key: String, val default: Any, val type: SettingType, val minimum: Double = 0.0, val maximum: Double = Double.MAX_VALUE) {
    fun validate(raw: Any?): Any {
        if (type == SettingType.FLAG) { require(raw is Boolean) { "$key must be true or false" }; return raw }
        require(raw is Number) { "$key must be numeric" }
        if (type == SettingType.INTEGER) require(raw is Int || raw is Long) { "$key must be integral" }
        val value = raw.toDouble()
        require(value.isFinite() && value >= minimum && value <= maximum) { "$key must be in $minimum..$maximum" }
        return if (type == SettingType.INTEGER) raw.toInt() else value
    }
}

/** Validated immutable runtime values. No mutable YAML escapes to an ability handler. */
class AbilitySettings internal constructor(private val values: Map<String, Any>, private val yaml: String) {
    fun get(key: String): Any = values.getValue(key)
    fun getInt(key: String): Int = get(key) as Int
    fun getDouble(key: String): Double = (get(key) as Number).toDouble()
    fun getBoolean(key: String): Boolean = get(key) as Boolean
    fun asConfiguration(): YamlConfiguration = YamlFiles.parse(yaml)
}

class AbilitySpec(val fields: List<AbilityField>, private val check: (AbilitySettings) -> Unit = {}) {
    init { require(fields.isNotEmpty() && fields.map { it.key }.toSet().size == fields.size) }
    fun prepare(source: YamlConfiguration): Pair<AbilitySettings, YamlConfiguration> {
        val copy = YamlFiles.parse(source.saveToString())
        val version = copy.get("schemaVersion")
        require(version == null || version == 1 || version == 1L) { "Unsupported ability settings schema $version" }
        val values = fields.associate { field ->
            if (!copy.isSet(field.key)) copy.set(field.key, field.default)
            field.key to field.validate(copy.get(field.key))
        }
        copy.set("schemaVersion", 1)
        val result = AbilitySettings(values, copy.saveToString())
        check(result)
        return result to copy
    }
    fun load(path: Path): AbilitySettings {
        try {
            val source = YamlFiles.read(path)
            val (settings, normalized) = prepare(source)
            // A malformed present value is never replaced, and validation finishes before any write.
            if (!Files.exists(path) || !source.isSet("schemaVersion") || fields.any { !source.isSet(it.key) }) YamlFiles.write(path, normalized)
            return settings
        } catch (failure: Exception) {
            throw IllegalArgumentException("Invalid ability settings ${path.toAbsolutePath()}: ${failure.message}", failure)
        }
    }
}

fun integer(key: String, default: Int, minimum: Int = 0, maximum: Int = Int.MAX_VALUE) = AbilityField(key, default, SettingType.INTEGER, minimum.toDouble(), maximum.toDouble())
fun number(key: String, default: Double, minimum: Double = 0.0, maximum: Double = Float.MAX_VALUE.toDouble() / 4) = AbilityField(key, default, SettingType.NUMBER, minimum, maximum)
fun flag(key: String, default: Boolean) = AbilityField(key, default, SettingType.FLAG)
