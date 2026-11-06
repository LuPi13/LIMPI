package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Random
import java.util.UUID

class RaceTest {
    @TempDir lateinit var directory: Path
    private val first = UUID.randomUUID(); private val second = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val odds = listOf(30, 25, 18, 13, 9, 5)

    private fun withService(balance: Long = 100_000, test: (StateStore, RaceService) -> Unit) = SqliteStateStore(directory.resolve("state.db")).use { store ->
        store.atomic { tx -> listOf(first, second).forEach { tx.put(SqliteProfilePersistence.key(it), "name: gambler\nmoney: $balance\n") } }
        test(store, RaceService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(4)))
    }
    private fun balance(store: StateStore, owner: UUID) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private fun started(store: StateStore, service: RaceService, winner: Int) {
        val round = service.start(id)
        store.atomic { it.put(CasinoKeys.table(CasinoGame.RACE, id), RaceService.encode(round.copy(winner = winner))) }
    }

    @Test fun `every sheep returns the same rate and wins as often as promised`() {
        val random = Random(8)
        val wins = IntArray(RACE_SHEEP)
        val races = 2_000_000
        repeat(races) { wins[RacePolicy.winner(odds, random)]++ }
        for (sheep in odds.indices) {
            assertEquals(odds[sheep] / 100.0, wins[sheep].toDouble() / races, 0.002, "sheep $sheep")
            val paid = RacePolicy.payout(1_000_000, odds[sheep], 97, Long.MAX_VALUE)
            assertEquals(0.97, odds[sheep] / 100.0 * paid / 1_000_000, 1e-5, "sheep $sheep")
        }
        assertEquals(1940, RacePolicy.multiplierCenti(5, 97)); assertEquals(19_400, RacePolicy.payout(1000, 5, 97, 10_000_000))
        assertEquals(10_000_000, RacePolicy.payout(100_000_000, 5, 97, 10_000_000))
    }

    @Test fun `only the frozen winner reaches the end and only on the last frame`() {
        for (seed in 0L until 500L) for (winner in 0 until RACE_SHEEP) {
            val path = RacePolicy.path(winner, seed)
            assertEquals(RacePolicy.FRAMES, path.size)
            assertEquals(RacePolicy.LENGTH, path.last()[winner])
            assertTrue(path.dropLast(1).all { it[winner] < RacePolicy.LENGTH })
            assertTrue(path.last().filterIndexed { sheep, _ -> sheep != winner }.all { it < RacePolicy.LENGTH })
            assertEquals(path, RacePolicy.path(winner, seed))
        }
    }

    @Test fun `bets add up per player up to the limit and stop at the start`() = withService { store, service ->
        service.bet(UUID.randomUUID(), first, id, 0, 60_000, odds)
        service.bet(UUID.randomUUID(), first, id, 0, 30_000, odds)
        service.bet(UUID.randomUUID(), first, id, 1, 10_000, odds)
        assertThrows(CasinoUnavailable::class.java) { service.bet(UUID.randomUUID(), first, id, 2, 100, odds) }
        assertEquals(0, balance(store, first))
        assertEquals(mapOf(0 to 90_000L, 1 to 10_000L), service.round(id)!!.bets[first])
        service.start(id)
        assertThrows(CasinoUnavailable::class.java) { service.bet(UUID.randomUUID(), second, id, 0, 100, odds) }
        assertEquals(100_000, balance(store, second))
    }

    @Test fun `no money refuses the bet and the first bet fixes the odds`() = withService(balance = 100) { store, service ->
        assertThrows(BankException::class.java) { service.bet(UUID.randomUUID(), first, id, 0, 200, odds) }
        assertEquals(100, balance(store, first)); assertNull(service.round(id))
        service.bet(UUID.randomUUID(), first, id, 0, 100, odds)
        service.bet(UUID.randomUUID(), second, id, 0, 100, odds.reversed())
        assertEquals(odds, service.round(id)!!.odds)
    }

    @Test fun `the end of the race pays bets on the winner once and only then`() = withService { store, service ->
        service.bet(UUID.randomUUID(), first, id, 0, 1000, odds); service.bet(UUID.randomUUID(), first, id, 1, 1000, odds)
        service.bet(UUID.randomUUID(), second, id, 2, 1000, odds)
        started(store, service, 0)
        assertEquals(98_000, balance(store, first)) // A balance gives nothing away during the race.
        val request = UUID.randomUUID()
        val settled = service.finish(request, id)
        assertEquals(0, settled.winner)
        assertEquals(mapOf(first to 3233L, second to 0L), settled.results.associate { it.owner to it.payout })
        assertEquals(101_233, balance(store, first)); assertEquals(99_000, balance(store, second))
        assertTrue(service.finish(request, id).replayed)
        assertEquals(101_233, balance(store, first)); assertNull(service.round(id))
    }

    @Test fun `a restart pays a started race by its frozen winner and refunds one still taking bets`() {
        val waiting = UUID.randomUUID()
        withService { store, service ->
            service.bet(UUID.randomUUID(), first, id, 5, 1000, odds)
            started(store, service, 5)
            service.bet(UUID.randomUUID(), second, waiting, 0, 1000, odds)
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val service = RaceService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(1))
            service.recover { }
            assertEquals(100_000 - 1000 + 19_400, balance(store, first)); assertEquals(100_000, balance(store, second))
            assertTrue(service.rounds().isEmpty())
        }
    }

    @Test fun `a damaged race stays for an administrator and discard keeps the payload in the ledger`() = withService { store, _ ->
        val key = CasinoKeys.table(CasinoGame.RACE, id)
        store.atomic { it.put(key, "phase: WHAT\n") }
        val services = CasinoServices(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings())
        services.recover { }
        assertEquals(listOf(key), services.damaged().map { it.key })
        assertThrows(CasinoUnavailable::class.java) { services.discard(UUID.randomUUID(), "admin", CasinoKeys.table(CasinoGame.RACE, UUID.randomUUID()), "no such") }
        val request = UUID.randomUUID()
        services.discard(request, "admin", key, "restored nothing")
        assertNull(store.get(key)); assertTrue(store.entry(request)!!.receipt.contains("WHAT"))
    }
}
