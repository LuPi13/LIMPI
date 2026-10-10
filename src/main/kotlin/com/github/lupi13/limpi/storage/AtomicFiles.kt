package com.github.lupi13.limpi.storage

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/** Write beside the destination, then replace it atomically; never truncate the live file. */
object AtomicFiles {
    fun write(path: Path, content: String) {
        val target = path.toAbsolutePath().normalize()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".${target.fileName}.", ".tmp")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val bytes = ByteBuffer.wrap(content.toByteArray(UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            // An unsupported atomic move is a failure, not permission to perform a partial write.
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
