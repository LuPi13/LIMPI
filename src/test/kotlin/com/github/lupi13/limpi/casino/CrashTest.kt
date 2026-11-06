package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Random
import java.util.UUID

class CrashTest {
    @TempDir lateinit var directory: Path
    private val first = UUID.randomUUID(); private val second = UUID.randomUUID()
    private val id = UUID.randomUUID()

    private fun withService(balance: Long = 100_000, test: (StateStore, CrashService) -> Unit) = SqliteStateStore(directory.resolve("state.db")).use { store ->
        store.atomic { tx -> listOf(first, second).forEach { tx.put(SqliteProfilePersistence.key(it), "name: gambler\nmoney: $balance\n") } }
        test(store, CrashService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(5)))
    }
    private fun balance(store: StateStore, owner: UUID) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private fun running(store: StateStore, service: CrashService, crash: Long) {
        val started = service.start(id)
        store.atomic { it.put(CasinoKeys.table(CasinoGame.CRASH, id), CrashService.encode(started.copy(crashCenti = crash))) }
    }

    @Test fun `cashing out at any target returns the promised rate`() {
        val random = Random(77)
        val points = LongArray(3_000_000) { CrashPolicy.crashPointCenti(random, 97, 1000) }
        for (target in listOf(101L, 110L, 150L, 200L, 500L, 2000L)) {
            val paid = points.count { it > target } * CrashPolicy.payout(1_000_000, target, Long.MAX_VALUE)
            assertEquals(0.97, paid.toDouble() / (points.size * 1_000_000.0), 0.015, "cash out at $target")
        }
        assertEquals(0.03, points.count { it == 100L }.toDouble() / points.size, 0.002) // Ends before anyone can take anything.
        assertTrue(points.all { it in 100..100_000 })
    }

    @Test fun `the multiplier starts at one and doubles on schedule`() {
        assertEquals(100, CrashPolicy.multiplierCenti(0, 0.08))
        val eightSeven = CrashPolicy.multiplierCenti((8.665 * 20).toLong(), 0.08)
        assertTrue(eightSeven in 196..200, "$eightSeven")
        assertEquals(1000, CrashPolicy.payout(1000, 100, 10_000_000))
        assertEquals(1230, CrashPolicy.payout(1000, 123, 10_000_000))
        assertEquals(1, CrashPolicy.payout(1, 199, 10_000_000)) // Rounded down.
        assertEquals(10_000_000, CrashPolicy.payout(100_000, 100_000, 10_000_000))
    }

    @Test fun `the graph climbs to the top row before two times and keeps climbing after folding back`() {
        val toDouble = (StrictMath.log(2.0) / 0.08 * 20).toLong() // About 173 ticks.
        assertEquals(listOf(0), CrashPolicy.graph(0, 0.08))
        // Every step of the way the staircase gets wider, and it reaches all rows before 2x.
        val widths = (0 until toDouble).map { CrashPolicy.graph(it, 0.08).size }
        assertEquals(widths.sorted(), widths); assertEquals(CrashPolicy.GRAPH_COLUMNS, widths.last())
        val full = CrashPolicy.graph(toDouble - 1, 0.08)
        assertEquals(listOf(0, 1, 2, 2, 3, 4, 4), full); assertEquals(CrashPolicy.GRAPH_ROWS - 1, full.max())
        // Past 2x the span doubles: half as wide, still rising.
        assertEquals(4, CrashPolicy.graph(toDouble + 1, 0.08).size)
        assertEquals(CrashPolicy.GRAPH_COLUMNS, CrashPolicy.graph(toDouble * 2 - 1, 0.08).size)
        assertEquals(4, CrashPolicy.graph(toDouble * 2 + 2, 0.08).size)
    }

    @Test fun `bets freeze in the round and cannot come after the start or twice`() = withService { store, service ->
        service.bet(UUID.randomUUID(), first, id, 1000)
        assertEquals(99_000, balance(store, first))
        assertThrows(CasinoUnavailable::class.java) { service.bet(UUID.randomUUID(), first, id, 1000) }
        assertEquals(99_000, balance(store, first))
        service.start(id)
        assertThrows(CasinoUnavailable::class.java) { service.bet(UUID.randomUUID(), second, id, 1000) }
        assertEquals(100_000, balance(store, second))
        val next = UUID.randomUUID()
        service.bet(UUID.randomUUID(), second, next, 100_000)
        val emptied = CrashService.encode(service.round(next)!!.copy(bets = emptyMap()))
        store.atomic { it.put(CasinoKeys.table(CasinoGame.CRASH, next), emptied) }
        assertThrows(BankException::class.java) { service.bet(UUID.randomUUID(), second, next, 100) }
        assertEquals(0, balance(store, second))
    }

    @Test fun `cash out pays the tick multiplier once and nothing at or past the frozen end`() = withService { store, service ->
        service.bet(UUID.randomUUID(), first, id, 1000); service.bet(UUID.randomUUID(), second, id, 2000)
        running(store, service, 300)
        val request = UUID.randomUUID()
        assertEquals(1500, service.cashOut(request, first, id, 150).payout)
        assertEquals(100_500, balance(store, first))
        assertTrue(service.cashOut(request, first, id, 150).replayed)
        assertThrows(CasinoStale::class.java) { service.cashOut(UUID.randomUUID(), first, id, 151) }
        assertEquals(100_500, balance(store, first))
        assertThrows(CasinoStale::class.java) { service.cashOut(UUID.randomUUID(), second, id, 300) }
        val finished = service.finish(UUID.randomUUID(), id)
        assertEquals(mapOf(second to 2000L), finished.uncashed)
        assertEquals(98_000, balance(store, second)); assertNull(service.round(id))
    }

    @Test fun `nobody without a bet can take anything`() = withService { store, service ->
        service.bet(UUID.randomUUID(), first, id, 1000)
        running(store, service, 300)
        assertThrows(CasinoStale::class.java) { service.cashOut(UUID.randomUUID(), second, id, 150) }
        assertEquals(100_000, balance(store, second))
    }

    @Test fun `a restart refunds what was not cashed out and leaves what was`() {
        withService { store, service ->
            service.bet(UUID.randomUUID(), first, id, 1000); service.bet(UUID.randomUUID(), second, id, 2000)
            running(store, service, 300)
            service.cashOut(UUID.randomUUID(), first, id, 200)
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val service = CrashService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(9))
            service.recover { }
            assertEquals(101_000, balance(store, first)); assertEquals(100_000, balance(store, second))
            assertTrue(service.rounds().isEmpty())
        }
    }
}
