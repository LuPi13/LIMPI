package com.github.lupi13.limpi.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class YamlPlayerRepositoryTest {
    @TempDir lateinit var directory: Path
    private val id = UUID.randomUUID()
    private fun file() = directory.resolve("$id.yml")

    @Test fun `existing players keep assets progress and custom fields on initialization`() {
        val original = "name: OldName\nmoney: 3000000000\nability: web_shooter\nunlockedAbilities: [web_shooter]\nquest:\n  progress: 12\ncustom: keep\n"
        Files.writeString(file(), original)
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "NewName", 10000)
        repository.ensure(id, "NewName", 1)
        val profile = repository.profile(id)
        assertEquals(3_000_000_000L, profile.balance)
        assertEquals("web_shooter", profile.abilityCode)
        assertEquals(setOf("web_shooter"), profile.unlockedAbilities)
        assertEquals("keep", repository.snapshot(id).getString("custom"))
        assertEquals(12, repository.snapshot(id).getInt("quest.progress"))
        assertEquals(original, Files.readString(directory.resolve("backups/$id.pre-schema-1.yml")))
        assertEquals(id, repository.findByName("NEWNAME"))
        assertNull(repository.findByName("OldName"))
    }

    @Test fun `new profiles get defaults once regardless of vanilla played-before flag`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 12345)
        assertEquals(12345L, repository.profile(id).balance)
        assertEquals(1, repository.snapshot(id).getInt("AbilityEnemy.NEUTRAL"))
        assertEquals(1, repository.snapshot(id).getInt("schemaVersion"))
        repository.ensure(id, "Player", 99999)
        assertEquals(12345L, repository.profile(id).balance)
    }

    @Test fun `an edit never changes published cache state before save`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 100)
        val edit = repository.snapshot(id)
        edit.set("money", 999)
        assertEquals(100L, repository.profile(id).balance)
        repository.save(id, edit)
        assertEquals(999L, repository.profile(id).balance)
    }

    @Test fun `old snapshots merge unrelated changes without losing new balances`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 100)
        val abilityEdit = repository.snapshot(id)
        val balanceEdit = repository.snapshot(id)
        balanceEdit.set("money", 500L)
        repository.save(id, balanceEdit)
        abilityEdit.set("ability", "web_shooter")
        repository.save(id, abilityEdit)
        assertEquals(500L, repository.profile(id).balance)
        assertEquals("web_shooter", repository.abilityCode(id))
        assertEquals(500L, abilityEdit.getLong("money"))
    }

    @Test fun `conflicting money changes fail without publishing stale data`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 100)
        val first = repository.snapshot(id)
        val stale = repository.snapshot(id)
        first.set("money", 90L)
        repository.save(id, first)
        stale.set("money", 80L)
        assertThrows(ProfileConflictException::class.java) { repository.save(id, stale) }
        assertEquals(90L, repository.profile(id).balance)
        assertEquals(90L, YamlFiles.read(file()).getLong("money"))
    }

    @Test fun `failed writes preserve both published memory and original file`() {
        var failWrites = false
        val repository = YamlPlayerRepository(directory, write = { path, content ->
            if (failWrites) throw IOException("disk unavailable")
            AtomicFiles.write(path, content)
        })
        repository.ensure(id, "Player", 100)
        val original = Files.readString(file())
        val edit = repository.snapshot(id)
        edit.set("money", 1000L)
        failWrites = true
        assertThrows(IOException::class.java) { repository.save(id, edit) }
        assertEquals(100L, repository.profile(id).balance)
        assertEquals(original, Files.readString(file()))
    }

    @Test fun `deleted nested fields preserve unrelated data and later saves rebase`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 100)
        val edit = repository.snapshot(id)
        edit.set("quest.one", 1)
        edit.set("quest.two", 2)
        repository.save(id, edit)
        edit.set("quest.one", null)
        repository.save(id, edit)
        assertFalse(repository.snapshot(id).contains("quest.one"))
        assertEquals(2, repository.snapshot(id).getInt("quest.two"))
    }

    @Test fun `hot path stays cached and quit releases the entry`() {
        Files.writeString(file(), "name: Player\nability: web_shooter\n")
        val repository = YamlPlayerRepository(directory)
        repository.pin(id)
        assertEquals("web_shooter", repository.abilityCode(id))
        Files.writeString(file(), "name: Player\nability: alchemy\n")
        repeat(100) { assertEquals("web_shooter", repository.abilityCode(id)) }
        repository.release(id)
        assertEquals("alchemy", repository.abilityCode(id))
    }

    @Test fun `malformed YAML is rejected without resetting the file`() {
        val damaged = "money: [broken\n"
        Files.writeString(file(), damaged)
        assertThrows(Exception::class.java) { YamlPlayerRepository(directory) }
        assertEquals(damaged, Files.readString(file()))
    }

    @Test fun `snapshot UUID cannot be changed while saving`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 100)
        assertThrows(IllegalArgumentException::class.java) {
            repository.save(UUID.randomUUID(), repository.snapshot(id))
        }
    }

    @Test fun `future schema is rejected without downgrading`() {
        val original = "name: Player\nschemaVersion: 2\nmoney: 100\n"
        Files.writeString(file(), original)
        val repository = YamlPlayerRepository(directory)
        assertThrows(IllegalArgumentException::class.java) { repository.ensure(id, "Player", 10000) }
        assertEquals(original, Files.readString(file()))
    }

    @Test fun `offline cache evicts old entries while online entries stay pinned`() {
        val other = UUID.randomUUID()
        Files.writeString(file(), "name: Online\nability: web_shooter\n")
        Files.writeString(directory.resolve("$other.yml"), "name: Offline\nability: alchemy\n")
        val repository = YamlPlayerRepository(directory, offlineCacheSize = 1)
        repository.pin(id)
        repository.abilityCode(other)
        Files.writeString(file(), "name: Online\nability: titan\n")
        assertEquals("web_shooter", repository.abilityCode(id))
        repository.abilityCode(UUID.randomUUID())
        Files.writeString(directory.resolve("$other.yml"), "name: Offline\nability: titan\n")
        assertEquals("titan", repository.abilityCode(other))
    }
}
