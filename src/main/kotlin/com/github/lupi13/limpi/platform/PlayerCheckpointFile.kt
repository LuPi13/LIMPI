package com.github.lupi13.limpi.platform

import net.kyori.adventure.nbt.BinaryTagIO
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Read the actual Minecraft file, rather than the online entity's in-memory PDC. */
class PlayerCheckpointFile(private val directory: Path, private val key: String) {
    fun read(owner: UUID): UUID? {
        val file = directory.resolve("$owner.dat")
        if (!Files.exists(file)) return null
        val value = Files.newInputStream(file).use {
            BinaryTagIO.reader(16L * 1024 * 1024).read(it, BinaryTagIO.Compression.GZIP)
                .getCompound("BukkitValues").getString(key)
        }
        return value.takeIf(String::isNotEmpty)?.let(UUID::fromString)
    }

    fun confirm(owner: UUID, expected: UUID) {
        val file = directory.resolve("$owner.dat")
        FileChannel.open(file, StandardOpenOption.WRITE).use { it.force(true) }
        check(read(owner) == expected) { "Player data checkpoint did not reach $file" }
    }
}
