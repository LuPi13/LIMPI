package com.github.lupi13.limpi.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.UUID

class CachedPlayerRepositoryTest {
    @TempDir lateinit var directory: Path
    private val id = UUID.randomUUID()
    private fun withStore(action: (SqliteStateStore) -> Unit) = SqliteStateStore(directory.resolve("state.db")).use(action)
    private fun put(store: StateStore, owner: UUID, payload: String) = store.atomic { it.put(SqliteProfilePersistence.key(owner), payload) }
    private fun stored(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(id)))

    @Test fun `names shared by two accounts never resolve to the wrong account`() = withStore { store ->
        val other = UUID.randomUUID()
        put(store, id, "name: SameName\nmoney: 100\n"); put(store, other, "name: SameName\nmoney: 200\n")
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        assertNull(repository.findByName("SameName"))
        repository.ensure(id, "Renamed", 10000)
        assertEquals(other, repository.findByName("SameName")); assertEquals(id, repository.findByName("RENAMED"))
    }

    @Test fun `new profiles get defaults once and existing values are kept`() = withStore { store ->
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 12345)
        assertEquals(12345L, repository.profile(id).balance)
        assertEquals(1, repository.snapshot(id).getInt("AbilityEnemy.NEUTRAL")); assertEquals(1, repository.snapshot(id).getInt("schemaVersion"))
        val edit = repository.snapshot(id); edit.set("quest.progress", 12); edit.set("custom", "keep")
        repository.save(id, edit)
        repository.ensure(id, "Player", 99999)
        assertEquals(12345L, repository.profile(id).balance); assertEquals(12, repository.snapshot(id).getInt("quest.progress"))
        assertEquals("keep", repository.snapshot(id).getString("custom"))
    }

    @Test fun `existing profile without money is not given the initial balance`() = withStore { store ->
        put(store, id, "name: Player\nability: web_shooter\n")
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 10000)
        assertFalse(repository.snapshot(id).contains("money")); assertNull(repository.profile(id).balance)
    }

    @Test fun `an edit never changes published cache state before save`() = withStore { store ->
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 100)
        val edit = repository.snapshot(id); edit.set("money", 999)
        assertEquals(100L, repository.profile(id).balance)
        repository.save(id, edit)
        assertEquals(999L, repository.profile(id).balance)
    }

    @Test fun `old snapshots merge unrelated changes without losing new balances`() = withStore { store ->
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 100)
        val abilityEdit = repository.snapshot(id); val balanceEdit = repository.snapshot(id)
        balanceEdit.set("money", 500L); repository.save(id, balanceEdit)
        abilityEdit.set("ability", "web_shooter"); repository.save(id, abilityEdit)
        assertEquals(500L, repository.profile(id).balance); assertEquals("web_shooter", repository.abilityCode(id))
        assertEquals(500L, abilityEdit.getLong("money"))
    }

    @Test fun `conflicting money changes fail without publishing stale data`() = withStore { store ->
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 100)
        val first = repository.snapshot(id); val stale = repository.snapshot(id)
        first.set("money", 90L); repository.save(id, first)
        stale.set("money", 80L)
        assertThrows(ProfileConflictException::class.java) { repository.save(id, stale) }
        assertEquals(90L, repository.profile(id).balance); assertEquals(90L, stored(store).getLong("money"))
    }

    @Test fun `cache re-read from the database does not create a false integral conflict`() = withStore { store ->
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 100)
        val edit = repository.snapshot(id)
        repository.invalidate(listOf(id))
        edit.set("ability", "web_shooter"); repository.save(id, edit)
        assertEquals("web_shooter", repository.abilityCode(id)); assertEquals(100L, repository.profile(id).balance)
    }

    @Test fun `failed writes preserve both published memory and stored payload`() = withStore { store ->
        var failWrites = false
        val sqlite = SqliteProfilePersistence(store)
        val repository = CachedPlayerRepository(object : ProfilePersistence by sqlite {
            override fun write(id: UUID, payload: String) { if (failWrites) throw IOException("disk unavailable"); sqlite.write(id, payload) }
        })
        repository.ensure(id, "Player", 100)
        val original = store.get(SqliteProfilePersistence.key(id))
        val edit = repository.snapshot(id); edit.set("money", 1000L)
        failWrites = true
        assertThrows(IOException::class.java) { repository.save(id, edit) }
        assertEquals(100L, repository.profile(id).balance); assertEquals(original, store.get(SqliteProfilePersistence.key(id)))
    }

    @Test fun `deleted nested fields preserve unrelated data and later saves rebase`() = withStore { store ->
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 100)
        val edit = repository.snapshot(id)
        edit.set("quest.one", 1); edit.set("quest.two", 2); repository.save(id, edit)
        edit.set("quest.one", null); repository.save(id, edit)
        assertFalse(repository.snapshot(id).contains("quest.one")); assertEquals(2, repository.snapshot(id).getInt("quest.two"))
    }

    @Test fun `hot path stays cached and quit releases the entry`() = withStore { store ->
        put(store, id, "name: Player\nability: web_shooter\n")
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.pin(id)
        assertEquals("web_shooter", repository.abilityCode(id))
        put(store, id, "name: Player\nability: alchemy\n")
        repeat(100) { assertEquals("web_shooter", repository.abilityCode(id)) }
        repository.release(id)
        assertEquals("alchemy", repository.abilityCode(id))
    }

    @Test fun `malformed payload is rejected without being reset`() = withStore { store ->
        val damaged = "money: [broken\n"
        put(store, id, damaged)
        // The name index reads every profile at startup, so a damaged payload is reported before any use.
        assertThrows(Exception::class.java) { CachedPlayerRepository(SqliteProfilePersistence(store)).snapshot(id) }
        assertEquals(damaged, store.get(SqliteProfilePersistence.key(id)))
    }

    @Test fun `snapshot UUID cannot be changed while saving`() = withStore { store ->
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        repository.ensure(id, "Player", 100)
        assertThrows(IllegalArgumentException::class.java) { repository.save(UUID.randomUUID(), repository.snapshot(id)) }
    }

    @Test fun `future schema is rejected without downgrading`() = withStore { store ->
        val original = "name: Player\nschemaVersion: 2\nmoney: 100\n"
        put(store, id, original)
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
        assertThrows(IllegalArgumentException::class.java) { repository.ensure(id, "Player", 10000) }
        assertEquals(original, store.get(SqliteProfilePersistence.key(id)))
    }

    @Test fun `offline cache evicts old entries while online entries stay pinned`() = withStore { store ->
        val other = UUID.randomUUID()
        put(store, id, "name: Online\nability: web_shooter\n"); put(store, other, "name: Offline\nability: alchemy\n")
        val repository = CachedPlayerRepository(SqliteProfilePersistence(store), offlineCacheSize = 1)
        repository.pin(id); repository.abilityCode(other)
        put(store, id, "name: Online\nability: titan\n")
        assertEquals("web_shooter", repository.abilityCode(id))
        repository.abilityCode(UUID.randomUUID())
        put(store, other, "name: Offline\nability: titan\n")
        assertEquals("titan", repository.abilityCode(other))
    }
}
