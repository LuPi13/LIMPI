package com.github.lupi13.limpi.platform

import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class PlayerCheckpointFileTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val key = "limpi:delivery_checkpoint"
    private fun write(value: String?) {
        val values = CompoundBinaryTag.builder().apply { if (value != null) putString(key, value) }.build()
        val root = CompoundBinaryTag.builder().put("BukkitValues", values).putInt("DataVersion", 1).build()
        Files.newOutputStream(directory.resolve("$owner.dat")).use { BinaryTagIO.writer().write(root, it, BinaryTagIO.Compression.GZIP) }
    }

    @Test fun `checkpoint is read from a real gzip NBT player file and confirmed only for the matching UUID`() {
        val expected = UUID.randomUUID(); write(expected.toString())
        val reader = PlayerCheckpointFile(directory, key)
        assertEquals(expected, reader.read(owner)); assertDoesNotThrow { reader.confirm(owner, expected) }
        assertThrows(IllegalStateException::class.java) { reader.confirm(owner, UUID.randomUUID()) }
    }

    @Test fun `missing player file or missing checkpoint does not fabricate a successful save`() {
        val reader = PlayerCheckpointFile(directory, key)
        assertNull(reader.read(owner)); assertThrows(Exception::class.java) { reader.confirm(owner, UUID.randomUUID()) }
        write(null); assertNull(reader.read(owner))
        assertThrows(IllegalStateException::class.java) { reader.confirm(owner, UUID.randomUUID()) }
    }

    @Test fun `other plugin checkpoint key cannot authorize this delivery`() {
        write(UUID.randomUUID().toString())
        assertNull(PlayerCheckpointFile(directory, "different:delivery_checkpoint").read(owner))
    }

    @Test fun `malformed compressed file and malformed UUID fail instead of becoming an empty saved profile`() {
        Files.writeString(directory.resolve("$owner.dat"), "not compressed NBT")
        assertThrows(Exception::class.java) { PlayerCheckpointFile(directory, key).read(owner) }
        write("broken UUID")
        assertThrows(IllegalArgumentException::class.java) { PlayerCheckpointFile(directory, key).read(owner) }
    }
}
