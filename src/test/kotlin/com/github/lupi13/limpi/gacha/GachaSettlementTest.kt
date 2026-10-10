package com.github.lupi13.limpi.gacha

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class GachaSettlementTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val state = GachaState(7, 3, 1, 2)
    private val before = listOf(StoredStack("ticket", 2, 64), StoredStack("other", 5, 64))
    private val after = listOf(before[0].copy(count = 1), before[1])
    private fun terms(grade: DrawGrade = DrawGrade.COMMON, force: Boolean = false, ability: String = "new_ability") =
        GachaTerms("ticket", 0, state, DrawOutcome(grade, ability, false, state.advance(grade, false)), 1000, force)
    private fun setup(store: StateStore) = store.atomic { tx ->
        val data = YamlFiles.parse("name: player\nmoney: 123\nunlockedAbilities: [existing_code]\nquests:\n  progress: 17\n")
        GachaConfiguration.write(data, state)
        tx.put(SqliteProfilePersistence.key(owner), data.saveToString())
    }
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(GachaSettlement.KIND to GachaSettlement(store)))
    private fun prepare(store: StateStore, terms: GachaTerms = terms(), request: UUID = id) =
        journal(store).prepare(request, owner, GachaSettlement.KIND, terms.encode(), before, after)
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null
        var changes = 0
        var saved = image
        var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; saved = after; checkpoint = id; changes++; afterSave()
        }
    }
    @Test fun `consumption confirms before unlock pity result and completion commit together`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val record = prepare(store); val inventory = Inventory(before)
            inventory.afterSave = { assertNull(GachaSettlement(store).find(owner, id)); assertEquals(state, GachaConfiguration.state(YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))))) }
            journal(store).apply(record, inventory)
            val profile = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
            assertEquals(listOf("existing_code", "new_ability"), profile.getStringList("unlockedAbilities"))
            assertEquals(terms().outcome.after, GachaConfiguration.state(profile))
            assertEquals(123, profile.getLong("money")); assertEquals(17, profile.getInt("quests.progress"))
            assertEquals(terms(), GachaSettlement(store).find(owner, id)!!.terms)
            assertEquals(RemovalState.DONE, journal(store).find(owner, id)!!.state)
            assertNull(GachaSettlement(store).find(UUID.randomUUID(), id))
        }
    }
    @Test fun `replaying completed draw cannot consume a fresh ticket or advance counters twice`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before); journal(store).apply(prepare(store), inventory)
            inventory.image = before; journal(store).apply(prepare(store), inventory)
            assertEquals(before, inventory.image); assertEquals(1, inventory.changes)
            assertEquals(terms().outcome.after, GachaConfiguration.state(YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))))
            assertThrows(TransactionIdentityConflict::class.java) { prepare(store, terms(ability = "changed")) }
        }
    }
    @Test fun `settlement failure leaves saved ticket recoverable and restart uses frozen outcome once`() {
        var fault = false; val inventory = Inventory(before)
        val frozen = terms(DrawGrade.TROLL, true)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("commit failure") }).use { store ->
            setup(store); val record = prepare(store, frozen); inventory.afterSave = { fault = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
            assertNull(GachaSettlement(store).find(owner, id))
            assertTrue(GachaSettlement(store).pendingApplications(owner).isEmpty())
            assertEquals(state, GachaConfiguration.state(YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))))
            assertEquals(listOf("existing_code"), YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getStringList("unlockedAbilities"))
        }
        inventory.image = inventory.saved; inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); journal(store).recover(owner, inventory, true)
            assertEquals(1, inventory.changes); assertEquals(frozen, GachaSettlement(store).find(owner, id)!!.terms)
            assertEquals(frozen, GachaSettlement(store).pendingApplications(owner).single().terms)
        }
    }
    @Test fun `duplicate unlock preserves old list while still recording paid draw`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).apply(prepare(store, terms(ability = "existing_code")), Inventory(before))
            assertEquals(listOf("existing_code"), YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getStringList("unlockedAbilities"))
            assertNotNull(GachaSettlement(store).find(owner, id))
        }
    }
    @Test fun `forced application persists separately and cannot be acknowledged before profile changes`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val service = GachaSettlement(store); val frozen = terms(DrawGrade.TROLL, true)
            journal(store).apply(prepare(store, frozen), Inventory(before))
            assertEquals(frozen, service.pendingApplications(owner).single().terms)
            assertNull(YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getString("ability"))
            assertThrows(IllegalArgumentException::class.java) { prepare(store, frozen.copy(before = frozen.outcome.after, outcome = frozen.outcome.copy(after = frozen.outcome.after.advance(DrawGrade.TROLL, false))), UUID.randomUUID()) }
            val request = UUID.randomUUID()
            assertThrows(IllegalArgumentException::class.java) { service.acknowledgeApplication(request, owner, id, "new_ability") }
            assertNull(store.entry(request)); assertEquals(1, service.pendingApplications(owner).size)
            store.atomic { tx -> val data = YamlFiles.parse(tx.get(SqliteProfilePersistence.key(owner))); data.set("ability", "new_ability"); tx.put(SqliteProfilePersistence.key(owner), data.saveToString()) }
            assertFalse(service.acknowledgeApplication(request, owner, id, "new_ability"))
            assertTrue(service.acknowledgeApplication(request, owner, id, "new_ability")); assertTrue(service.pendingApplications(owner).isEmpty())
        }
    }
    @Test fun `retained operator resolution grants nothing while removed resolution commits frozen result`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); prepare(store); journal(store).resolve(UUID.randomUUID(), "operator", owner, id, false, "ticket retained")
            assertNull(GachaSettlement(store).find(owner, id))
            assertEquals(state, GachaConfiguration.state(YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))))
            val second = UUID.randomUUID(); prepare(store, request = second)
            val request = UUID.randomUUID()
            journal(store).resolve(request, "operator", owner, second, true, "saved ticket removal confirmed")
            journal(store).resolve(request, "operator", owner, second, true, "saved ticket removal confirmed")
            assertEquals(terms(), GachaSettlement(store).find(owner, second)!!.terms)
        }
    }
    @Test fun `invalid removal malformed unlocks and stale counters are refused before consuming`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(id, owner, GachaSettlement.KIND, terms().encode(), before, listOf(null, before[1])) }
            assertThrows(IllegalArgumentException::class.java) { journal(store).prepare(id, owner, GachaSettlement.KIND, terms().encode(), before, listOf(after[0], before[1].copy(count = 4))) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms().copy(ticket = "forged")) }
            store.atomic { tx -> val data = YamlFiles.parse(tx.get(SqliteProfilePersistence.key(owner))); data.set("unlockedAbilities", listOf(17)); tx.put(SqliteProfilePersistence.key(owner), data.saveToString()) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store) }
            setup(store)
            store.atomic { tx -> val data = YamlFiles.parse(tx.get(SqliteProfilePersistence.key(owner))); data.set("EPIC.Count", 4); tx.put(SqliteProfilePersistence.key(owner), data.saveToString()) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store) }
            assertTrue(journal(store).pending(owner).isEmpty()); assertNull(store.entry(id))
        }
    }
    @Test fun `frozen terms reject altered counters noninteger schema values and impossible pickup`() {
        val valid = terms(); assertEquals(valid, GachaTerms.decode(valid.encode()))
        assertThrows(IllegalArgumentException::class.java) { valid.copy(outcome = valid.outcome.copy(after = state)) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(outcome = valid.outcome.copy(pickup = true)) }
        for (path in listOf("slot", "pickupTime", "before.EPIC.Count", "after.LEGENDARY.Count")) {
            val data = YamlFiles.parse(valid.encode()); data.set(path, 1.5)
            assertThrows(IllegalArgumentException::class.java) { GachaTerms.decode(data.saveToString()) }
        }
    }
    @Test fun `offhand ticket consumption recovers without changing any storage slot`() {
        val image = List<StoredStack?>(36) { StoredStack("storage $it", 64, 64) } + StoredStack("ticket", 1, 64)
        val result = image.dropLast(1) + null
        val frozen = terms().copy(slot = 36)
        val inventory = Inventory(image)
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).prepare(id, owner, GachaSettlement.KIND, frozen.encode(), image, result)
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true)
            assertEquals(image.take(36), inventory.image.take(36)); assertNull(inventory.image.last())
            assertEquals(frozen, GachaSettlement(store).find(owner, id)!!.terms)
        }
    }
    @Test fun `failed acknowledgement retains forced application until a successful retry`() {
        var fault = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fault) error("ack failure") }).use { store ->
            setup(store); journal(store).apply(prepare(store, terms(DrawGrade.TROLL, true)), Inventory(before))
            store.atomic { tx -> val data = YamlFiles.parse(tx.get(SqliteProfilePersistence.key(owner))); data.set("ability", "new_ability"); tx.put(SqliteProfilePersistence.key(owner), data.saveToString()) }
            val request = UUID.randomUUID(); fault = true
            assertThrows(IllegalStateException::class.java) { GachaSettlement(store).acknowledgeApplication(request, owner, id, "new_ability") }
            assertEquals(1, GachaSettlement(store).pendingApplications(owner).size); assertNull(store.entry(request))
            fault = false; GachaSettlement(store).acknowledgeApplication(request, owner, id, "new_ability")
            assertTrue(GachaSettlement(store).pendingApplications(owner).isEmpty()); assertNotNull(GachaSettlement(store).find(owner, id))
        }
    }
}
