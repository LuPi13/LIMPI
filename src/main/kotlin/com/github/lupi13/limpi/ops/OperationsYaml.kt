package com.github.lupi13.limpi.ops

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Data only; never instantiate Bukkit or application objects from an exported YAML tag. */
object OperationsYaml {
    private fun parser() = Yaml(SafeConstructor(LoaderOptions().apply {
        isAllowDuplicateKeys = false
        codePointLimit = 256 * 1024 * 1024
        maxAliasesForCollections = 50
    }))
    fun parse(text: String): Any? = parser().load<Any>(text)
    fun map(text: String): Map<*, *> = parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Expected a YAML mapping")
    fun dump(value: Any): String = parser().dump(value)
    fun integer(value: Any?, name: String): Long = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is java.math.BigInteger -> value.longValueExact()
        else -> throw IllegalArgumentException("$name must be an integral Long")
    }
}
