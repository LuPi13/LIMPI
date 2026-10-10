package com.github.lupi13.limpi.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class LegacyStateMigrationTest {
    @TempDir lateinit var root: Path
    private val id = UUID.randomUUID()
    private fun source(payload: String): Path {
        val file = root.resolve("playerData/$id.yml")
        Files.createDirectories(file.parent)
        Files.writeString(file, payload)
        return file
    }

    @Test fun `migration preserves UUID assets progress unknown fields and exact backups`() {
        val payload = "name: Player\nmoney: 3000000000\nability: web_shooter\nunlockedAbilities: [web_shooter]\nquest:\n  progress: 12\nunknown: keep\ncustomEmpty: {}\n"
        val file = source(payload)
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            val report = LegacyStateMigration.import(root, store)
            assertEquals(1, report.players)
            assertEquals("3000000000", report.balanceSum)
            assertFalse(report.reused)
            assertEquals(payload, store.get(SqliteProfilePersistence.key(id)))
            assertEquals(payload, Files.readString(root.resolve("backups/sqlite-v1/playerData/$id.yml")))
            assertEquals(payload, Files.readString(file))
            val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
            repository.ensure(id, "Renamed", 10000)
            assertEquals(3_000_000_000L, repository.profile(id).balance)
            assertEquals("web_shooter", repository.abilityCode(id))
            assertEquals(12, repository.snapshot(id).getInt("quest.progress"))
            assertEquals("keep", repository.snapshot(id).getString("unknown"))
            assertTrue(repository.snapshot(id).contains("customEmpty"))
            assertEquals(payload, Files.readString(file)) // Legacy YAML is retained, not used as a second live database.
        }
    }

    @Test fun `repeat startup never reimports stale YAML over current balances`() {
        val file = source("name: Player\nmoney: 100\n")
        val database = root.resolve("limpi.sqlite3")
        SqliteStateStore(database).use { store ->
            LegacyStateMigration.import(root, store)
            store.atomic { it.put(SqliteProfilePersistence.key(id), "name: Player\nmoney: 999\n") }
        }
        Files.writeString(file, "name: Player\nmoney: 0\n")
        SqliteStateStore(database).use { store ->
            assertTrue(LegacyStateMigration.import(root, store).reused)
            assertEquals(999L, YamlFiles.parse(store.get(SqliteProfilePersistence.key(id))).getLong("money"))
        }
    }

    @Test fun `invalid source prevents the entire import and retains originals`() {
        val valid = source("name: Player\nmoney: 100\n")
        val invalid = root.resolve("playerData/${UUID.randomUUID()}.yml")
        Files.writeString(invalid, "money: [broken\n")
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            assertThrows(Exception::class.java) { LegacyStateMigration.import(root, store) }
            assertTrue(store.documents("player/").isEmpty())
            assertNull(store.get("migration/legacy_yaml_v1"))
            assertEquals("name: Player\nmoney: 100\n", Files.readString(valid))
            assertEquals("money: [broken\n", Files.readString(invalid))
        }
    }

    @Test fun `failed import commit leaves no partial profiles or completed marker`() {
        source("name: Player\nmoney: 100\n")
        SqliteStateStore(root.resolve("limpi.sqlite3"), beforeCommit = { throw IllegalStateException("fault") }).use { store ->
            assertThrows(IllegalStateException::class.java) { LegacyStateMigration.import(root, store) }
            assertTrue(store.documents("player/").isEmpty())
            assertNull(store.get("migration/legacy_yaml_v1"))
            assertTrue(Files.exists(root.resolve("backups/sqlite-v1/playerData/$id.yml")))
        }
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            assertEquals(1, LegacyStateMigration.import(root, store).players)
        }
    }

    @Test fun `nonintegral or out of range money is rejected rather than truncated`() {
        val file = source("name: Player\nmoney: 1.5\n")
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            assertThrows(IllegalArgumentException::class.java) { LegacyStateMigration.import(root, store) }
            Files.writeString(file, "name: Player\nmoney: 9223372036854775808\n")
            assertThrows(ArithmeticException::class.java) { LegacyStateMigration.import(root, store) }
            assertTrue(store.documents("player/").isEmpty())
        }
    }

    @Test fun `negative legacy money is preserved for explicit operator repair`() {
        source("name: Player\nmoney: -100\n")
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            assertEquals("-100", LegacyStateMigration.import(root, store).balanceSum)
            assertEquals(-100L, YamlFiles.parse(store.get(SqliteProfilePersistence.key(id))).getLong("money"))
        }
    }

    @Test fun `backup mismatch blocks import instead of overwriting the recovery copy`() {
        source("name: Player\nmoney: 100\n")
        val backup = root.resolve("backups/sqlite-v1/playerData/$id.yml")
        Files.createDirectories(backup.parent)
        Files.writeString(backup, "original backup")
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            assertThrows(IllegalArgumentException::class.java) { LegacyStateMigration.import(root, store) }
            assertEquals("original backup", Files.readString(backup))
            assertTrue(store.documents("player/").isEmpty())
        }
    }

    @Test fun `lost or swapped database refuses to reimport stale YAML`() {
        source("name: Player\nmoney: 100\n")
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store -> LegacyStateMigration.import(root, store) }
        assertTrue(Files.exists(root.resolve(LegacyStateMigration.COMPLETED_FILE)))
        Files.move(root.resolve("limpi.sqlite3"), root.resolve("moved.sqlite3"))
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            assertThrows(IllegalStateException::class.java) { LegacyStateMigration.import(root, store) }
            assertNull(store.get(SqliteProfilePersistence.key(id)))
        }
    }

    @Test fun `existing imported database backfills the external completion record`() {
        source("name: Player\nmoney: 100\n")
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            LegacyStateMigration.import(root, store)
            Files.delete(root.resolve(LegacyStateMigration.COMPLETED_FILE))
            assertTrue(LegacyStateMigration.import(root, store).reused)
            assertTrue(Files.exists(root.resolve(LegacyStateMigration.COMPLETED_FILE)))
        }
    }

    @Test fun `migrated profile without money is not given the initial balance`() {
        source("name: Player\nability: web_shooter\nunlockedAbilities: [web_shooter]\n")
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            LegacyStateMigration.import(root, store)
            val repository = CachedPlayerRepository(SqliteProfilePersistence(store))
            repository.ensure(id, "Player", 10000)
            assertFalse(repository.snapshot(id).contains("money")); assertNull(repository.profile(id).balance)
            val fresh = UUID.randomUUID(); repository.ensure(fresh, "New", 10000)
            assertEquals(10000L, repository.profile(fresh).balance)
        }
    }
}
