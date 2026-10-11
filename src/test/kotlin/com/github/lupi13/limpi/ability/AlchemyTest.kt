package com.github.lupi13.limpi.ability

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class AlchemyTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID(); private val id = UUID.randomUUID()
    private val before = List<StoredStack?>(37) { if (it == 36) StoredStack("copper NBT", 23, 64) else null }
    private val after = before.toMutableList().apply { this[36] = before[36]!!.copy(count = 13) }.toList()
    private val terms = AlchemyTerms(36, "copper NBT", "diamond", ItemGrant("frozen diamond", 1, 64))
    private fun setup(store: StateStore) = store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: player\nmoney: 0\nability: alchemy\n") }
    private fun journal(store: StateStore) = RemovalJournal(store, CachedPlayerRepository(SqliteProfilePersistence(store)), mapOf(AlchemySettlement.KIND to AlchemySettlement(DeliveryJournal(store))))
    private fun prepare(store: StateStore, value: AlchemyTerms = terms, image: List<StoredStack?> = after) = journal(store).prepare(id, owner, AlchemySettlement.KIND, value.encode(), before, image)
    private class Inventory(var image: List<StoredStack?>) : DeliveryInventory {
        var checkpoint: UUID? = null; var saves = 0; var afterSave: () -> Unit = {}
        override fun snapshot() = image
        override fun persistedCheckpoint() = checkpoint
        override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
            check(image == before); image = after; checkpoint = id; saves++; afterSave()
        }
    }
    @Test fun `weighted boundaries skip zero weights and select the configured last outcome`() {
        val policy = AlchemyPolicy(linkedMapOf("coal" to 0.0, "diamond" to 0.25, "netherite_ingot" to 0.75, "gold" to 0.0))
        assertEquals("diamond", policy.choose(0.0)); assertEquals("diamond", policy.choose(0.249999))
        assertEquals("netherite_ingot", policy.choose(0.25)); assertEquals("netherite_ingot", policy.choose(Math.nextDown(1.0)))
        assertThrows(IllegalArgumentException::class.java) { policy.choose(1.0) }
        assertThrows(IllegalArgumentException::class.java) { AlchemyPolicy(mapOf("coal" to 0.5)) }
    }
    @Test fun `ten offhand copper are saved before one frozen mineral right is committed`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before)
            inventory.afterSave = { assertTrue(DeliveryJournal(store).pending(owner).isEmpty()) }
            journal(store).apply(prepare(store), inventory)
            assertEquals(after, inventory.image); assertEquals(terms.result, DeliveryJournal(store).pending(owner).single().items.single())
            assertEquals(RemovalState.DONE, journal(store).find(owner, id)!!.state)
        }
    }
    @Test fun `replay does not consume copper twice or reroll the result`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); val inventory = Inventory(before); journal(store).apply(prepare(store), inventory)
            journal(store).apply(prepare(store), inventory)
            assertEquals(1, inventory.saves); assertEquals(1, DeliveryJournal(store).pending(owner).size)
            assertThrows(TransactionIdentityConflict::class.java) { prepare(store, terms.copy(outcome = "coal")) }
        }
    }
    @Test fun `crash after physical save recovers the same mineral after restart`() {
        var fail = false; val inventory = Inventory(before)
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fail) error("crash") }).use { store ->
            setup(store); val record = prepare(store); inventory.afterSave = { fail = true }
            assertThrows(DeliveryRecoveryRequired::class.java) { journal(store).apply(record, inventory) }
        }
        inventory.afterSave = {}
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            journal(store).recover(owner, inventory, true); journal(store).recover(owner, inventory, true)
            assertEquals(1, inventory.saves); assertEquals(terms.result, DeliveryJournal(store).pending(owner).single().items.single())
        }
    }
    @Test fun `wrong cost source slot or quantity is rejected before reserving inventory`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms.copy(copper = "wrong")) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store, terms.copy(slot = 0)) }
            assertThrows(IllegalArgumentException::class.java) { prepare(store, image = after.toMutableList().apply { this[36] = before[36]!!.copy(count = 12) }) }
            assertTrue(journal(store).pending(owner).isEmpty()); assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
        }
    }
    @Test fun `full destination retains a mineral right instead of dropping it`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); journal(store).apply(prepare(store), Inventory(before))
            val delivery = DeliveryJournal(store).pending(owner).single()
            val full = List<StoredStack?>(36) { StoredStack("full", 64, 64) }
            assertThrows(InventoryFull::class.java) { InventoryPlan.grant(full, delivery.items) }
            assertEquals(delivery, DeliveryJournal(store).pending(owner).single())
        }
    }
    @Test fun `retained admin judgment gives nothing and removed judgment grants the frozen result once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store); prepare(store); journal(store).resolve(UUID.randomUUID(), "admin", owner, id, false, "retained")
            assertTrue(DeliveryJournal(store).pending(owner).isEmpty())
            val second = UUID.randomUUID(); journal(store).prepare(second, owner, AlchemySettlement.KIND, terms.encode(), before, after)
            val request = UUID.randomUUID(); journal(store).resolve(request, "admin", owner, second, true, "removed")
            journal(store).resolve(request, "admin", owner, second, true, "removed")
            assertEquals(1, DeliveryJournal(store).pending(owner).size)
        }
    }
    @Test fun `lotto evidence commits with netherite delivery and survives restart and replay`() {
        val eligible = terms.copy(outcome = "netherite_ingot", result = ItemGrant("frozen netherite", 1, 64), lottoEligible = true)
        val path = directory.resolve("state.db")
        SqliteStateStore(path).use { store ->
            setup(store)
            val record = prepare(store, eligible)
            assertNull(store.get("quest_trigger/$owner/lotto"))
            journal(store).apply(record, Inventory(before))
            assertEquals(id.toString(), store.get("quest_trigger/$owner/lotto"))
            assertEquals(eligible.result, DeliveryJournal(store).pending(owner).single().items.single())
        }
        SqliteStateStore(path).use { store ->
            assertEquals(id.toString(), store.get("quest_trigger/$owner/lotto"))
            assertEquals(RemovalState.DONE, journal(store).find(owner, id)?.state)
        }
    }
    @Test fun `noneligible netherite does not grant lotto evidence`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            val frozen = terms.copy(outcome = "netherite_ingot")
            journal(store).apply(prepare(store, frozen), Inventory(before))
            assertNull(store.get("quest_trigger/$owner/lotto"))
        }
    }
    @Test fun `lotto retained judgment gives no evidence and removed judgment unlocks once`() {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            setup(store)
            val eligible = terms.copy(outcome = "netherite_ingot", lottoEligible = true)
            prepare(store, eligible)
            journal(store).resolve(UUID.randomUUID(), "admin", owner, id, false, "retained")
            assertNull(store.get("quest_trigger/$owner/lotto"))
            val second = UUID.randomUUID()
            journal(store).prepare(second, owner, AlchemySettlement.KIND, eligible.encode(), before, after)
            val request = UUID.randomUUID()
            journal(store).resolve(request, "admin", owner, second, true, "removed")
            journal(store).resolve(request, "admin", owner, second, true, "removed")
            assertEquals(second.toString(), store.get("quest_trigger/$owner/lotto"))
            val players = CachedPlayerRepository(SqliteProfilePersistence(store))
            val rewards = com.github.lupi13.limpi.quest.QuestRewards(store, players)
            assertTrue(rewards.complete(owner, "lotto", "alchemy_plus").newUnlock)
            assertTrue(rewards.complete(owner, "lotto", "alchemy_plus").replayed)
            val profile = players.snapshot(owner)
            assertEquals(listOf("lotto"), profile.getStringList("clearedQuests"))
            assertEquals(listOf("alchemy_plus"), profile.getStringList("unlockedAbilities"))
            assertEquals(1, DeliveryJournal(store).pending(owner).size)
        }
    }
}
