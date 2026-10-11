package com.github.lupi13.limpi.ops

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Called by a single worker, never with the live store's connection. */
class AutomaticBackups(private val source: Path, private val directory: Path, private val keep: Int,
    private val clock: Clock = Clock.systemDefaultZone()) {
    init { require(keep > 0) { "BackupKeep must be positive" } }

    fun run(): Path {
        Files.createDirectories(directory)
        val stamp = LocalDateTime.now(clock).format(FORMAT)
        val target = directory.resolve("limpi-$stamp.sqlite3")
        // Never overwrite a same-second backup or prune after an unsuccessful backup.
        OperationsFiles.backup(source, target)
        Files.list(directory).use { paths ->
            val backups = paths.filter { NAME.matches(it.fileName.toString()) && Files.isRegularFile(it, NOFOLLOW_LINKS) }
                .sorted(compareByDescending<Path> { it.fileName.toString() }).toList()
            backups.drop(keep).forEach(Files::delete)
        }
        return target
    }

    companion object {
        private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val NAME = Regex("limpi-[0-9]{8}-[0-9]{6}\\.sqlite3")
    }
}
