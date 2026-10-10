package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class AbilityTransitionsTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID(); private val id = UUID.randomUUID()
    private val terms = TransitionTerms("old", "new", 36, "old active", 0, StoredStack("new active", 1, 1))
    private val before = List<StoredStack?>(37) { when (it) { 0 -> StoredStack("ordinary sword metadata", 1, 1); 36 -> StoredStack("old active", 1, 1); else -> null } }
    private fun setup(store: StateStore) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: player\nmoney: 777\nability: old\nquest:\n  unknown: 9\nunlockedAbilities: [old, new, unknown]\n") }
    private fun service(store: StateStore) = AbilityTransitions(store, CachedPlayerRepository(SqliteProfilePersistence(store)))
    private fun profile(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner)))
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null; var saves = 0; var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; checkpoint = id; saves++; afterSave()
        }
    }
    @Test fun `profile commits only after saved relocation and all unrelated state survives`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val svc = service(store); val inventory = Inventory(before)
            val record = svc.prepare(id, owner, terms, before)
            assertEquals("old", profile(store).getString("ability"))
            inventory.afterSave = { assertEquals("old", profile(store).getString("ability")); assertEquals(TransitionState.APPLYING, svc.pending(owner).single().state) }
            svc.apply(record, inventory)
            assertEquals(terms.plan(before), inventory.image); assertEquals("new", profile(store).getString("ability"))
            assertEquals(777, profile(store).getInt("money")); assertEquals(9, profile(store).getInt("quest.unknown"))
            assertEquals(listOf("old", "new", "unknown"), profile(store).getStringList("unlockedAbilities"))
            assertEquals("ordinary sword metadata", inventory.image[1]!!.payload); assertNull(inventory.image[36])
            assertTrue(svc.pending(owner).isEmpty()); assertEquals(TransitionState.DONE, svc.find(owner, id)!!.state)
        }
    }
    @Test fun `transition replay cannot replace a later inventory and changed request conflicts`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val svc = service(store); val inventory = Inventory(before)
            svc.apply(svc.prepare(id, owner, terms, before), inventory)
            inventory.image = before; svc.apply(svc.prepare(id, owner, terms, before), inventory)
            assertEquals(1, inventory.saves); assertEquals(before, inventory.image)
            assertThrows(TransactionIdentityConflict::class.java) { svc.prepare(id, owner, terms.copy(next = "another"), before) }
            assertNull(svc.find(UUID.randomUUID(), id))
        }
    }
    @Test fun `restart before physical change applies the frozen plan once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store -> setup(store); service(store).prepare(id, owner, terms, before) }
        val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            service(store).recover(owner, inventory, true); service(store).recover(owner, inventory, true)
            assertEquals(1, inventory.saves); assertEquals("new", profile(store).getString("ability"))
        }
    }
    @Test fun `restart after physical save and failed DB commit settles without relocating twice`() {
        var fail = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fail) error("crash") }).use { store ->
            setup(store); val svc = service(store); val record = svc.prepare(id, owner, terms, before)
            inventory.afterSave = { fail = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { svc.apply(record, inventory) }
            assertEquals("old", profile(store).getString("ability")); assertEquals(1, svc.pending(owner).size)
        }
        inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            service(store).recover(owner, inventory, true)
            assertEquals(1, inventory.saves); assertEquals("new", profile(store).getString("ability"))
        }
    }
    @Test fun `unrecognized saved inventory enters review without changing it or profile`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val svc = service(store); svc.prepare(id, owner, terms, before)
            val unknown = before.toMutableList().apply { this[3] = StoredStack("other item", 1, 1) }.toList(); val inventory = Inventory(unknown)
            assertThrows(DeliveryRecoveryRequired::class.java) { svc.recover(owner, inventory, true) }
            assertEquals(unknown, inventory.image); assertEquals(0, inventory.saves)
            assertEquals("old", profile(store).getString("ability")); assertEquals(TransitionState.REVIEW, svc.pending(owner).single().state)
        }
    }
    @Test fun `space shortage and stale ability never reserve or destroy items`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val svc = service(store)
            assertThrows(IllegalArgumentException::class.java) { svc.prepare(id, owner, terms.copy(previous = "stale"), before) }
            val full = List<StoredStack?>(37) { if (it == 36) null else StoredStack("full $it", 1, 1) }
            assertThrows(InventoryFull::class.java) { svc.prepare(id, owner, terms.copy(previousSlot = null, previousItem = null), full) }
            assertTrue(svc.pending(owner).isEmpty()); assertNull(store.entry(id)); assertEquals("old", profile(store).getString("ability"))
        }
    }
    @Test fun `active item restoration uses the same checkpoint protocol without changing ability`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val svc = service(store); val restore = terms.copy(next = "old", nextSlot = 36, nextItem = StoredStack("old active", 1, 1))
            val lost = before.toMutableList().apply { this[36] = StoredStack("ordinary offhand", 1, 1) }.toList()
            val inventory = Inventory(lost); svc.apply(svc.prepare(id, owner, restore, lost), inventory)
            assertEquals("old", profile(store).getString("ability")); assertEquals("old active", inventory.image[36]!!.payload)
            assertEquals(1L, InventoryPlan.count(inventory.image, "ordinary offhand")); assertEquals(1L, InventoryPlan.count(inventory.image, "ordinary sword metadata"))
        }
    }
    @Test fun `operator retained keeps old profile while applied settlement is audited and idempotent`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val svc = service(store); svc.prepare(id, owner, terms, before)
            svc.resolve(UUID.randomUUID(), "operator", owner, id, false, "saved old inventory")
            assertEquals("old", profile(store).getString("ability")); assertTrue(svc.pending(owner).isEmpty())
            val second = UUID.randomUUID(); svc.prepare(second, owner, terms, before)
            val request = UUID.randomUUID(); svc.resolve(request, "operator", owner, second, true, "saved relocated inventory")
            svc.resolve(request, "operator", owner, second, true, "saved relocated inventory")
            assertEquals("new", profile(store).getString("ability")); assertNotNull(store.entry(request)); assertTrue(svc.pending(owner).isEmpty())
        }
    }
    @Test fun `transition and common removal share one physical reservation`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val svc = service(store); svc.prepare(id, owner, terms, before)
            val removals = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(AbilityItemUse.KIND to AbilityItemUse(DeliveryJournal(store))))
            val after = before.toMutableList().apply { this[0] = null }
            assertThrows(IllegalStateException::class.java) { removals.prepare(UUID.randomUUID(), owner, AbilityItemUse.KIND, AbilityItemTerms("old", 0, "ordinary sword metadata", null).encode(), before, after) }
            assertThrows(IllegalStateException::class.java) { svc.prepare(UUID.randomUUID(), owner, terms, before) }
            assertTrue(removals.pending(owner).isEmpty())
        }
    }
}
