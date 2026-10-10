package com.github.lupi13.limpi.storage

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

data class LegacyImportReport(val players: Int, val balanceSum: String, val reused: Boolean)

/** Validate every source and back it up before a single atomic import. Source files are never deleted. */
object LegacyStateMigration {
    private const val MARKER = "migration/legacy_yaml_v1"
    /** Written only after a committed import. It survives a lost or swapped DB, unlike the in-DB marker. */
    const val COMPLETED_FILE = "backups/sqlite-v1/IMPORTED"

    fun import(root: Path, store: StateStore): LegacyImportReport {
        val completed = root.resolve(COMPLETED_FILE)
        store.get(MARKER)?.let { marker ->
            // Backfill for deployments imported before the external record existed.
            if (!Files.exists(completed)) AtomicFiles.write(completed, marker)
            return report(marker, true)
        }
        // An imported server whose DB is now missing or replaced must restore the DB backup. Re-importing the old
        // YAML would silently overwrite every later balance, unlock and trade.
        check(!Files.exists(completed)) {
            "LIMPI already imported legacy YAML ($COMPLETED_FILE) but limpi.sqlite3 has no import marker. " +
                "Restore the correct DB backup; delete that file only after confirming no SQLite-era changes exist."
        }
        val documents = linkedMapOf<String, String>()
        val sources = linkedMapOf<Path, String>()
        var sum = BigInteger.ZERO
        val directory = root.resolve("playerData")
        if (Files.exists(directory)) {
            Files.newDirectoryStream(directory, "*.yml").use { files ->
                for (file in files) {
                    val id = runCatching { UUID.fromString(file.fileName.toString().removeSuffix(".yml")) }.getOrNull() ?: continue
                    val payload = Files.readString(file)
                    val parsed = YamlFiles.parse(payload)
                    PlayerSchema.validate(parsed, id.toString())
                    val raw = parsed.get("money")
                    val balance = when (raw) {
                        null -> 0L
                        is Byte, is Short, is Int, is Long -> (raw as Number).toLong()
                        is BigInteger -> raw.longValueExact()
                        else -> throw IllegalArgumentException("Player $id has a non-integral money field")
                    }
                    sum += BigInteger.valueOf(balance)
                    val key = SqliteProfilePersistence.key(id)
                    require(key !in documents) { "Duplicate legacy UUID: $id" }
                    documents[key] = payload
                    sources[root.relativize(file)] = payload
                }
            }
        }
        val market = root.resolve("financial/market.yml")
        if (Files.exists(market)) {
            val payload = Files.readString(market)
            YamlFiles.parse(payload) // Includes validation of serialized items on the Paper server.
            documents["financial/market"] = payload
            sources[root.relativize(market)] = payload
        }
        for ((source, payload) in sources) {
            val backup = root.resolve("backups/sqlite-v1").resolve(source)
            if (Files.exists(backup)) {
                require(Files.readString(backup) == payload) { "Legacy source changed since its backup: $source" }
            } else AtomicFiles.write(backup, payload)
        }
        val metadata = org.bukkit.configuration.file.YamlConfiguration().apply {
            set("players", documents.keys.count { it.startsWith("player/") })
            set("balanceSum", sum.toString())
            sources.forEach { (path, payload) ->
                val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                set("sources.${path.toString().replace('\\', '/').replace('.', '_')}", hash)
            }
        }
        store.atomic { transaction ->
            check(transaction.get(MARKER) == null) { "Migration was completed by another process" }
            documents.forEach { (key, payload) ->
                check(transaction.get(key) == null) { "Cannot overwrite existing state during import: $key" }
                transaction.put(key, payload)
            }
            transaction.put(MARKER, metadata.saveToString())
        }
        AtomicFiles.write(completed, metadata.saveToString())
        return report(metadata.saveToString(), false)
    }

    private fun report(payload: String, reused: Boolean): LegacyImportReport {
        val data = YamlFiles.parse(payload)
        return LegacyImportReport(data.getInt("players"), requireNotNull(data.getString("balanceSum")), reused)
    }
}
