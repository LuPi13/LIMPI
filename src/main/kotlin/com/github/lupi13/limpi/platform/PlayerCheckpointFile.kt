package com.github.lupi13.limpi.platform

import net.kyori.adventure.nbt.BinaryTagIO
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Read the actual Minecraft file, rather than the online entity's in-memory PDC.
 * With several candidate directories, the one where a saved probe actually appears is locked in;
 * until then a file present in more than one candidate is ambiguous and rejected.
 */
class PlayerCheckpointFile(candidates: List<Path>, private val key: String, private val located: (Path) -> Unit = {}) {
    constructor(directory: Path, key: String) : this(listOf(directory), key)

    private val candidates = candidates.map { it.toAbsolutePath().normalize() }.distinct()
    @Volatile private var directory: Path? = this.candidates.singleOrNull()
    init { require(this.candidates.isNotEmpty()) }

    fun read(owner: UUID): UUID? {
        val file = file(owner) ?: return null
        return readIn(file.parent, owner)
    }

    fun confirm(owner: UUID, expected: UUID) {
        directory?.let { confirmIn(it, owner, expected); return }
        val match = candidates.filter { Files.exists(it.resolve("$owner.dat")) && readIn(it, owner) == expected }
        val found = checkNotNull(match.singleOrNull()) {
            "Player data checkpoint did not reach any of ${candidates.joinToString { it.resolve("$owner.dat").toString() }}"
        }
        confirmIn(found, owner, expected)
        directory = found
        located(found)
    }

    private fun file(owner: UUID): Path? {
        directory?.let { dir -> return dir.resolve("$owner.dat").takeIf(Files::exists) }
        val present = candidates.map { it.resolve("$owner.dat") }.filter(Files::exists)
        check(present.size <= 1) { "Player data for $owner exists in several directories: $present; set PlayerDataDirectory" }
        return present.singleOrNull()
    }

    private fun readIn(dir: Path, owner: UUID): UUID? {
        val value = Files.newInputStream(dir.resolve("$owner.dat")).use {
            BinaryTagIO.reader(16L * 1024 * 1024).read(it, BinaryTagIO.Compression.GZIP)
                .getCompound("BukkitValues").getString(key)
        }
        return value.takeIf(String::isNotEmpty)?.let(UUID::fromString)
    }

    private fun confirmIn(dir: Path, owner: UUID, expected: UUID) {
        val file = dir.resolve("$owner.dat")
        FileChannel.open(file, StandardOpenOption.WRITE).use { it.force(true) }
        check(readIn(dir, owner) == expected) { "Player data checkpoint did not reach $file" }
    }
}
