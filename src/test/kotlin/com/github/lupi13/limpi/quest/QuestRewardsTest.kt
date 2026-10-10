package com.github.lupi13.limpi.quest

import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class QuestRewardsTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private fun service(store: StateStore) = QuestRewards(store, CachedPlayerRepository(SqliteProfilePersistence(store)))
    @Test fun `completion unlock and receipt commit once while preserving unknown legacy data`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 123\nunlockedAbilities: [unknown]\nclearedQuests: [old]\nquest:\n  custom: 77\n") }
            assertEquals(QuestRewardResult(true, true, false), service(store).complete(owner, "quest", "ability"))
            assertTrue(service(store).complete(owner, "quest", "ability").replayed)
            val profile = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(listOf("old", "quest"), profile.getStringList("clearedQuests"))
            assertEquals(listOf("unknown", "ability"), profile.getStringList("unlockedAbilities"))
            assertEquals(77, profile.getInt("quest.custom")); assertEquals(123, profile.getLong("money"))
            assertNotNull(store.get("quest_reward/$owner/quest"))
            assertThrows(TransactionIdentityConflict::class.java) { service(store).complete(owner, "quest", "changed_reward") }
        }
    }
    @Test fun `legacy completed quest missing its separately saved unlock is repaired atomically`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nclearedQuests: [quest]\n") }
            assertEquals(QuestRewardResult(false, true, false), service(store).complete(owner, "quest", "ability"))
            assertEquals(listOf("quest"), YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getStringList("clearedQuests"))
        }
    }
    @Test fun `failed commit leaves neither completed quest nor unlocked reward and can retry`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("quest fault") }).use { store ->
            val payload = "money: 0\nunlockedAbilities: [old]\n"
            store.atomic { it.put(SqliteProfilePersistence.key(owner), payload) }; fault = true
            assertThrows(IllegalStateException::class.java) { service(store).complete(owner, "quest", "ability") }
            assertEquals(payload, store.get(SqliteProfilePersistence.key(owner))); assertNull(store.get("quest_reward/$owner/quest"))
            fault = false; assertTrue(service(store).complete(owner, "quest", "ability").newCompletion)
        }
    }
    @Test fun `malformed completion lists cannot be silently replaced`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val payload = "money: 0\nclearedQuests: [17]\n"
            store.atomic { it.put(SqliteProfilePersistence.key(owner), payload) }
            assertThrows(IllegalArgumentException::class.java) { service(store).complete(owner, "quest", "ability") }
            assertEquals(payload, store.get(SqliteProfilePersistence.key(owner)))
        }
    }
    @Test fun `final confirmed harmony hit commits progress completion and reward together`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nquest:\n  harmony_sword: 10\n  harmony_arrow: 9\n") }
            val request = UUID.randomUUID()
            assertEquals(QuestRewardResult(true, true, false), service(store).harmony(request, owner, "harmony_arrow"))
            assertTrue(service(store).harmony(request, owner, "harmony_arrow").replayed)
            val data = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(10, data.getLong("quest.harmony_arrow"))
            assertEquals(listOf(QuestRewards.HARMONY_QUEST), data.getStringList("clearedQuests"))
            assertEquals(listOf(QuestRewards.HARMONY_REWARD), data.getStringList("unlockedAbilities"))
            assertFalse(service(store).harmony(UUID.randomUUID(), owner, "harmony_sword").newCompletion)
        }
    }
    @Test fun `progress below threshold does not prematurely unlock either quest reward`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\n") }
            repeat(10) { assertFalse(service(store).harmony(UUID.randomUUID(), owner, "harmony_sword").newUnlock) }
            val data = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(10, data.getLong("quest.harmony_sword")); assertTrue(data.getStringList("clearedQuests").isEmpty())
        }
    }
    @Test fun `progress commit failure cannot leave a completed threshold without its reward`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("progress fault") }).use { store ->
            val payload = "money: 0\nquest:\n  harmony_sword: 10\n  harmony_arrow: 9\n"
            store.atomic { it.put(SqliteProfilePersistence.key(owner), payload) }; val request = UUID.randomUUID(); fault = true
            assertThrows(IllegalStateException::class.java) { service(store).harmony(request, owner, "harmony_arrow") }
            assertEquals(payload, store.get(SqliteProfilePersistence.key(owner))); assertNull(store.entry(request))
            assertNull(store.get("quest_reward/$owner/${QuestRewards.HARMONY_QUEST}"))
            fault = false; assertTrue(service(store).harmony(request, owner, "harmony_arrow").newUnlock)
        }
    }
    @Test fun `legacy large progress is preserved and malformed numbers are refused`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nquest:\n  harmony_sword: 9223372036854775807\n  harmony_arrow: 0\n") }
            service(store).harmony(UUID.randomUUID(), owner, "harmony_sword")
            assertEquals(Long.MAX_VALUE, YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("quest.harmony_sword"))
            for (raw in listOf("1.5", "-1", "'17'")) {
                store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nquest:\n  harmony_arrow: $raw\n") }
                assertThrows(IllegalArgumentException::class.java) { service(store).harmony(UUID.randomUUID(), owner, "harmony_arrow") }
            }
        }
    }
    @Test fun `a recorded reward is not regranted after an explicit later profile reset`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nquest:\n  harmony_sword: 10\n  harmony_arrow: 10\n") }
            service(store).harmony(UUID.randomUUID(), owner, "harmony_arrow")
            store.atomic { tx -> val data = YamlFiles.parse(tx.get(SqliteProfilePersistence.key(owner))); data.set("unlockedAbilities", emptyList<String>()); tx.put(SqliteProfilePersistence.key(owner), data.saveToString()) }
            assertFalse(service(store).harmony(UUID.randomUUID(), owner, "harmony_arrow").newUnlock)
            assertFalse(service(store).complete(owner, QuestRewards.HARMONY_QUEST, QuestRewards.HARMONY_REWARD).newUnlock)
            assertTrue(YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getStringList("unlockedAbilities").isEmpty())
        }
    }
}
