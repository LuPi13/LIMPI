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
    @Test fun `completion unlock and receipt commit once while preserving unknown data`() {
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
    @Test fun `already completed quest without its unlock still grants the unlock once`() {
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
    @Test fun `large progress is preserved and malformed numbers are refused`() {
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

    @Test fun `counted quest completes exactly at its target and replays never count twice`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\n") }
            repeat(2) { assertFalse(service(store).progress(UUID.randomUUID(), owner, "dodge_count", 3, "dodge_master", "dodge_plus").newCompletion) }
            val request = UUID.randomUUID()
            assertEquals(QuestRewardResult(true, true, false), service(store).progress(request, owner, "dodge_count", 3, "dodge_master", "dodge_plus"))
            assertTrue(service(store).progress(request, owner, "dodge_count", 3, "dodge_master", "dodge_plus").replayed)
            assertFalse(service(store).progress(UUID.randomUUID(), owner, "dodge_count", 3, "dodge_master", "dodge_plus").newCompletion)
            val data = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(3, data.getLong("quest.dodge_count")); assertEquals(listOf("dodge_plus"), data.getStringList("unlockedAbilities"))
        }
    }
    @Test fun `accumulated quest adds amounts, caps at its target and replays never add twice`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\n") }
            assertFalse(service(store).progress(UUID.randomUUID(), owner, "healed", 1200, 2000, "light_time", "time_leaper_plus").newCompletion)
            assertFalse(service(store).progress(UUID.randomUUID(), owner, "healed", 0, 2000, "light_time", "time_leaper_plus").newCompletion)
            val request = UUID.randomUUID()
            assertEquals(QuestRewardResult(true, true, false), service(store).progress(request, owner, "healed", 900, 2000, "light_time", "time_leaper_plus"))
            assertTrue(service(store).progress(request, owner, "healed", 900, 2000, "light_time", "time_leaper_plus").replayed)
            assertThrows(TransactionIdentityConflict::class.java) { service(store).progress(request, owner, "healed", 901, 2000, "light_time", "time_leaper_plus") }
            assertFalse(service(store).progress(UUID.randomUUID(), owner, "healed", Long.MAX_VALUE, 2000, "light_time", "time_leaper_plus").newCompletion)
            val data = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(2000, data.getLong("quest.healed")); assertEquals(listOf("time_leaper_plus"), data.getStringList("unlockedAbilities"))
            assertThrows(IllegalArgumentException::class.java) { service(store).progress(UUID.randomUUID(), owner, "healed", -1, 2000, "light_time", "time_leaper_plus") }
        }
    }
    @Test fun `accumulated quest near Long range never overflows`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nquest:\n  lost: 9223372036854775800\n") }
            assertTrue(service(store).progress(UUID.randomUUID(), owner, "lost", Long.MAX_VALUE, Long.MAX_VALUE, "dark_time", "chronobreak").newCompletion)
            assertEquals(Long.MAX_VALUE, YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("quest.lost"))
        }
    }
    @Test fun `usage counter counts once per request, caps and never completes a quest`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 5\nquest:\n  custom: 9\n") }
            val request = UUID.randomUUID()
            assertEquals(1, service(store).count(request, owner, "cloak_uses", 2))
            assertEquals(1, service(store).count(request, owner, "cloak_uses", 2))
            assertEquals(2, service(store).count(UUID.randomUUID(), owner, "cloak_uses", 2))
            assertEquals(2, service(store).count(UUID.randomUUID(), owner, "cloak_uses", 2))
            val data = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(2, data.getLong("quest.cloak_uses")); assertEquals(9, data.getLong("quest.custom")); assertEquals(5, data.getLong("money"))
            assertTrue(data.getStringList("clearedQuests").isEmpty()); assertTrue(data.getStringList("unlockedAbilities").isEmpty())
        }
    }
    @Test fun `usage counter keeps values above its cap and refuses malformed values without overwriting`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nquest:\n  time_leaper_uses: 9223372036854775807\n") }
            assertEquals(Long.MAX_VALUE, service(store).count(UUID.randomUUID(), owner, "time_leaper_uses", 5))
            for (raw in listOf("1.5", "-1", "'3'")) {
                val payload = "money: 0\nquest:\n  time_leaper_uses: $raw\n"
                store.atomic { it.put(SqliteProfilePersistence.key(owner), payload) }
                assertThrows(IllegalArgumentException::class.java) { service(store).count(UUID.randomUUID(), owner, "time_leaper_uses", 5) }
                assertEquals(payload, store.get(SqliteProfilePersistence.key(owner)))
            }
        }
    }
    @Test fun `stored progress above a lowered target completes on the next counted action`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "money: 0\nquest:\n  hand_cannon_shots: 2500\n  hawk_eye_extra_items: 80\n") }
            assertEquals(QuestRewardResult(true, true, false), service(store).progress(UUID.randomUUID(), owner, "hand_cannon_shots", 1000, "no_hurt", "charge_shot"))
            assertEquals(QuestRewardResult(true, true, false), service(store).progress(UUID.randomUUID(), owner, "hawk_eye_extra_items", 1, 50, "give_all", "hawk_eye_plus"))
            val data = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(2500, data.getLong("quest.hand_cannon_shots")); assertEquals(80, data.getLong("quest.hawk_eye_extra_items"))
        }
    }
}
