package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Random
import java.util.UUID

class MinesTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()

    private fun <T> withService(balance: Long = 1_000_000, settings: CasinoSettings = CasinoSettings(), test: (StateStore, MinesService) -> T): T =
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: gambler\nmoney: $balance\ncustom: retained\n") }
            test(store, MinesService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), settings, Random(7)))
        }
    private fun balance(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private fun safe(round: MinesRound) = (0 until MinesPolicy.CELLS).first { it !in round.mines && it !in round.revealed }

    @Test fun `every chance of surviving is paid exactly back at the configured return`() {
        // Expected return of cashing out after k picks = P(survive k) * multiplier = rtp, for every board.
        for (mines in MinesPolicy.MINE_COUNTS) for (picks in 1..MinesPolicy.CELLS - mines) {
            var survive = 1.0
            for (pick in 0 until picks) survive *= (MinesPolicy.CELLS - mines - pick).toDouble() / (MinesPolicy.CELLS - pick)
            val paid = MinesPolicy.payout(1_000_000_000, mines, picks, 97, Long.MAX_VALUE)
            assertEquals(0.97, survive * paid / 1_000_000_000, 1e-6, "$mines mines, $picks picks")
        }
    }

    @Test fun `the exact fraction does not overflow on the widest board and the limit caps it`() {
        val longest = MinesPolicy.payout(100_000, 12, 13, 97, Long.MAX_VALUE)
        assertTrue(longest > 100_000L * 5_000_000, "$longest")
        assertEquals(10_000_000, MinesPolicy.payout(100_000, 12, 13, 97, 10_000_000))
        assertEquals(120, MinesPolicy.payout(100, 5, 1, 96, 10_000_000))
        assertEquals(121, MinesPolicy.payout(100, 5, 1, 97, 10_000_000)) // 121.25 rounded down
    }

    @Test fun `start debits and freezes the board in one commit and refuses a second round`() = withService { store, service ->
        val round = service.start(UUID.randomUUID(), owner, 1000, 5)
        assertEquals(999_000, balance(store)); assertEquals(5, round.mines.size)
        assertEquals(round, MinesService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(99)).current(owner))
        assertThrows(CasinoUnavailable::class.java) { service.start(UUID.randomUUID(), owner, 1000, 5) }
        assertEquals(999_000, balance(store))
        assertEquals("retained", YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getString("custom"))
    }

    @Test fun `bets outside the range and more than the balance change nothing`() = withService(balance = 500) { store, service ->
        assertThrows(CasinoUnavailable::class.java) { service.start(UUID.randomUUID(), owner, 99, 5) }
        assertThrows(CasinoUnavailable::class.java) { service.start(UUID.randomUUID(), owner, 100_001, 5) }
        assertThrows(CasinoUnavailable::class.java) { service.start(UUID.randomUUID(), owner, 100, 25) }
        assertThrows(BankException::class.java) { service.start(UUID.randomUUID(), owner, 501, 5) }
        assertEquals(500, balance(store)); assertNull(service.current(owner))
    }

    @Test fun `cash out pays once and a stale or repeated click does not pay again`() = withService { store, service ->
        var round = service.start(UUID.randomUUID(), owner, 1000, 5)
        assertThrows(CasinoUnavailable::class.java) { service.cashOut(UUID.randomUUID(), owner, round.version) }
        round = service.reveal(UUID.randomUUID(), owner, safe(round), round.version).round
        assertThrows(CasinoStale::class.java) { service.reveal(UUID.randomUUID(), owner, safe(round), 0) }
        val request = UUID.randomUUID()
        val paid = service.cashOut(request, owner, round.version).payout
        assertEquals(1212, paid) // 1000 * 0.97 * 25/20
        assertEquals(999_000 + paid, balance(store))
        assertThrows(CasinoStale::class.java) { service.cashOut(UUID.randomUUID(), owner, round.version) }
        assertEquals(999_000 + paid, balance(store)); assertNull(service.current(owner))
    }

    @Test fun `a mine ends the round without pay and the frozen board survives a restart`() {
        val round = withService { _, service -> service.start(UUID.randomUUID(), owner, 1000, 24) }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val service = MinesService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(1234))
            assertEquals(round, service.current(owner))
            val outcome = service.reveal(UUID.randomUUID(), owner, round.mines.first(), 0)
            assertTrue(outcome is MinesOutcome.Exploded)
            assertEquals(999_000, balance(store)); assertNull(service.current(owner))
        }
    }

    @Test fun `opening the last safe cell or reaching the limit pays automatically`() {
        withService { store, service ->
            val round = service.start(UUID.randomUUID(), owner, 1000, 24)
            val outcome = service.reveal(UUID.randomUUID(), owner, safe(round), 0) as MinesOutcome.Cashed
            assertTrue(outcome.automatic); assertEquals(24_250, outcome.payout)
            assertEquals(999_000 + 24_250, balance(store)); assertNull(service.current(owner))
        }
    }

    @Test fun `the payout limit ends a long round at the limit`() = withService(settings = CasinoSettings(maxBet = 1000, maxPayout = 1200)) { store, service ->
        val round = service.start(UUID.randomUUID(), owner, 1000, 5)
        val outcome = service.reveal(UUID.randomUUID(), owner, safe(round), 0) as MinesOutcome.Cashed
        assertTrue(outcome.automatic); assertEquals(1200, outcome.payout)
        assertEquals(1_000_200, balance(store))
    }

    @Test fun `a damaged round is kept and blocks a new one`() = withService { store, service ->
        store.atomic { it.put(CasinoKeys.round(owner, CasinoGame.MINES), "bet: -5\nmines: [99]\n") }
        assertThrows(CasinoDamaged::class.java) { service.current(owner) }
        assertThrows(CasinoUnavailable::class.java) { service.start(UUID.randomUUID(), owner, 100, 5) }
        assertNotNull(store.get(CasinoKeys.round(owner, CasinoGame.MINES)))
        assertEquals(1_000_000, balance(store))
    }

    @Test fun `bet selector steps by the leading digit and stays in range`() {
        val settings = CasinoSettings()
        assertEquals(200, BetSelector.up(100, settings)); assertEquals(1000, BetSelector.up(900, settings)); assertEquals(2000, BetSelector.up(1000, settings))
        assertEquals(900, BetSelector.down(1000, settings)); assertEquals(100, BetSelector.down(100, settings)); assertEquals(100, BetSelector.down(200, settings))
        assertEquals(100_000, BetSelector.up(100_000, settings)); assertEquals(100_000, BetSelector.tenTimes(20_000, settings))
        assertEquals(1000, BetSelector.tenTimes(100, settings)); assertEquals(100, BetSelector.tenth(500, settings))
    }

    @Test fun `settings read the packaged defaults and refuse a typo instead of paying it`() {
        val config = javaClass.classLoader.getResourceAsStream("config.yml")!!.use { stream ->
            org.bukkit.configuration.file.YamlConfiguration().apply { loadFromString(stream.readBytes().toString(Charsets.UTF_8)) }
        }
        assertEquals(CasinoSettings(), CasinoSettings.read(config))
        config.set("CasinoMaxBet", 50)
        assertThrows(IllegalArgumentException::class.java) { CasinoSettings.read(config) }
        config.set("CasinoMaxBet", "lots")
        assertThrows(IllegalArgumentException::class.java) { CasinoSettings.read(config) }
    }
}
