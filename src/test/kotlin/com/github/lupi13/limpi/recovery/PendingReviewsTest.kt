package com.github.lupi13.limpi.recovery

import com.github.lupi13.limpi.storage.SqliteStateStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class PendingReviewsTest {
    @TempDir lateinit var root: Path
    @Test fun `all pending kinds and malformed records are visible without changing storage`() {
        val owner = UUID.randomUUID()
        val id = UUID.randomUUID()
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            store.atomic { tx ->
                tx.put("player/$owner", "name: 사용자\nunknown: keep")
                tx.put("delivery_pending/$owner/$id", "state: REVIEW\nreason: purpose\nreviewReason: checkpoint mismatch")
                tx.put("removal_pending/$owner/$id", "state: APPLYING")
                tx.put("ability_transition_pending/$owner/$id", "state: REVIEW\nreason: transition mismatch")
                tx.put("market_escrow_pending/$owner/$id", "broken: [")
                tx.put("sugar_pending/$owner/$id", "effect: speed")
                tx.put("delivery_done/$owner/$id", "state: DONE")
            }
            val prefixes = listOf("player/", "delivery_pending/", "removal_pending/", "ability_transition_pending/", "market_escrow_pending/", "sugar_pending/", "delivery_done/")
            val before = prefixes.associateWith(store::documents)
            val records = PendingReviews.list(store)
            assertEquals(5, records.size)
            assertEquals(setOf("delivery", "removal", "transition", "market", "sugar"), records.map { it.kind }.toSet())
            assertTrue(records.all { it.name == "사용자" && it.owner == owner && it.id == id.toString() })
            assertEquals("checkpoint mismatch", records.single { it.kind == "delivery" }.reason)
            assertEquals("MALFORMED", records.single { it.kind == "market" }.state)
            assertTrue(records.single { it.kind == "sugar" }.command.startsWith("/financial sugar void"))
            assertEquals(before, prefixes.associateWith(store::documents))
        }
    }
    @Test fun `empty pending set yields empty list`() {
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { assertTrue(PendingReviews.list(it).isEmpty()) }
    }
    @Test fun `mismatched owner stays visible but cannot suggest an unsafe resolution`() {
        val owner = UUID.randomUUID(); val id = UUID.randomUUID()
        SqliteStateStore(root.resolve("limpi.sqlite3")).use { store ->
            store.atomic { it.put("removal_pending/$owner/$id", "owner: ${UUID.randomUUID()}\nstate: REVIEW") }
            val record = PendingReviews.list(store).single()
            assertEquals("MALFORMED", record.state)
            assertFalse(record.command.contains(" resolve "))
            assertNotNull(store.get("removal_pending/$owner/$id"))
        }
    }
}
