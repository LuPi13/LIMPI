package com.github.lupi13.limpi.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AtomicFilesTest {
    @TempDir lateinit var directory: Path

    @Test fun `atomic replacement writes UTF-8 and leaves no temporary files`() {
        val target = directory.resolve("profile.yml")
        Files.writeString(target, "old")
        AtomicFiles.write(target, "name: 한글\nmoney: 3000000000\n")
        assertEquals("name: 한글\nmoney: 3000000000\n", Files.readString(target))
        Files.list(directory).use { assertEquals(1L, it.count()) }
    }

    @Test fun `failed replacement leaves original destination intact`() {
        val target = directory.resolve("profile.yml")
        Files.createDirectory(target)
        Files.writeString(target.resolve("preserve"), "original")
        assertThrows(Exception::class.java) { AtomicFiles.write(target, "new") }
        assertEquals("original", Files.readString(target.resolve("preserve")))
        Files.list(directory).use { assertEquals(1L, it.count()) }
    }
}
