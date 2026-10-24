package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class StockTradingServiceTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private fun withTrading(action: (SqliteStateStore, CachedPlayerRepository, StockTradingService) -> Unit) {
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val players = CachedPlayerRepository(SqliteProfilePersistence(store))
            players.ensure(owner, "Player", 3_000_000_000)
            val service = StockTradingService(store, players) { Money(100) }
            action(store, players, service)
        }
    }
    private fun StockTradingService.buy(price: Long = 100, quantity: Long = 2, request: UUID = UUID.randomUUID()) =
        trade(request, owner, "ABCD", Money(price), quantity, StockTrade.BUY)
    private fun service(store: StateStore, players: CachedPlayerRepository, price: Long = 150) =
        StockTradingService(store, players) { Money(price) }

    @Test fun `buy and sell update cash and portfolio in the same profile commit`() = withTrading { store, players, trading ->
        trading.buy(); service(store, players).trade(UUID.randomUUID(), owner, "ABCD", Money(150), 1, StockTrade.SELL)
        assertEquals(2_999_999_950L, players.profile(owner).balance)
        val holding = trading.holding(players.snapshot(owner), "ABCD")
        assertEquals(1L, holding.count); assertEquals(100L, holding.averagePrice)
        assertEquals(50L, players.snapshot(owner).getLong("stock.ABCD.realizedProfit"))
    }

    @Test fun `unaffordable purchase does not grant shares or write a ledger receipt`() = withTrading { store, players, trading ->
        val request = UUID.randomUUID()
        assertThrows(IllegalArgumentException::class.java) { trading.buy(quantity = 30_000_001, request = request) }
        assertEquals(3_000_000_000L, players.profile(owner).balance); assertFalse(players.snapshot(owner).contains("stock.ABCD"))
        assertNull(store.entry(request))
    }

    @Test fun `overselling does not credit cash`() = withTrading { store, players, _ ->
        assertThrows(IllegalArgumentException::class.java) {
            service(store, players).trade(UUID.randomUUID(), owner, "ABCD", Money(150), 1, StockTrade.SELL)
        }
        assertEquals(3_000_000_000L, players.profile(owner).balance)
    }

    @Test fun `sale overflow does not discard shares`() = withTrading { store, players, _ ->
        val data = players.snapshot(owner); data.set("money", Long.MAX_VALUE); data.set("stock.ABCD.count", 1)
        data.set("stock.ABCD.costBasis", 1); players.save(owner, data)
        assertThrows(ArithmeticException::class.java) {
            service(store, players, 1).trade(UUID.randomUUID(), owner, "ABCD", Money(1), 1, StockTrade.SELL)
        }
        assertEquals(1L, players.snapshot(owner).getLong("stock.ABCD.count")); assertEquals(Long.MAX_VALUE, players.profile(owner).balance)
    }

    @Test fun `failed trade commit preserves both cash and portfolio including cached state`() {
        var fail = false
        SqliteStateStore(directory.resolve("state.db"), beforeCommit = { if (fail) error("disk unavailable") }).use { store ->
            val players = CachedPlayerRepository(SqliteProfilePersistence(store)); players.ensure(owner, "Player", 1000)
            val trading = StockTradingService(store, players) { Money(100) }; val request = UUID.randomUUID()
            players.snapshot(owner); fail = true
            assertThrows(IllegalStateException::class.java) { trading.buy(request = request) }
            assertEquals(1000L, players.profile(owner).balance); assertFalse(players.snapshot(owner).contains("stock.ABCD"))
            assertNull(store.entry(request)); fail = false
            assertFalse(trading.buy(request = request).replayed); assertEquals(800L, players.profile(owner).balance)
        }
    }

    @Test fun `full sale clears acquisition basis and cash stays exact beyond Int`() = withTrading { store, players, _ ->
        val data = players.snapshot(owner); data.set("money", 6_000_000_000); players.save(owner, data)
        val trading = service(store, players, 3_000_000_000)
        trading.buy(3_000_000_000, 2); trading.trade(UUID.randomUUID(), owner, "ABCD", Money(3_000_000_000), 2, StockTrade.SELL)
        assertEquals(6_000_000_000L, players.profile(owner).balance)
        assertEquals(StockHolding(0, Money(0)), trading.holding(players.snapshot(owner), "ABCD"))
    }

    @Test fun `invalid holdings fail without replacing user assets with zero`() = withTrading { store, players, _ ->
        val data = players.snapshot(owner); data.set("stock.ABCD.count", 1); data.set("stock.ABCD.costBasis", -100)
        players.save(owner, data)
        assertThrows(IllegalArgumentException::class.java) {
            service(store, players).trade(UUID.randomUUID(), owner, "ABCD", Money(150), 1, StockTrade.SELL)
        }
        assertEquals(3_000_000_000L, players.profile(owner).balance); assertEquals(-100L, players.snapshot(owner).getLong("stock.ABCD.costBasis"))
    }

    @Test fun `same request replays original receipt after restart even if quote is gone`() {
        val request = UUID.randomUUID()
        withTrading { _, players, trading ->
            val original = trading.buy(request = request); assertFalse(original.replayed)
            assertTrue(trading.buy(request = request).replayed); assertEquals(2L, trading.holding(players.snapshot(owner), "ABCD").count)
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val players = CachedPlayerRepository(SqliteProfilePersistence(store))
            val trading = StockTradingService(store, players) { error("replay must not query changed catalog") }
            val replay = trading.buy(request = request); assertTrue(replay.replayed)
            assertEquals(200L, replay.total.units); assertEquals(2L, replay.holding.count); assertEquals(2_999_999_800L, players.profile(owner).balance)
            assertThrows(TransactionIdentityConflict::class.java) { trading.buy(quantity = 3, request = request) }
        }
    }

    @Test fun `deleted or stale quote rejects a new request without touching holdings`() = withTrading { store, players, trading ->
        val request = UUID.randomUUID()
        assertThrows(IllegalArgumentException::class.java) { trading.buy(price = 150, request = request) }
        assertThrows(IllegalArgumentException::class.java) { StockTradingService(store, players) { null }.buy(request = request) }
        assertNull(store.entry(request)); assertEquals(3_000_000_000L, players.profile(owner).balance)
    }

    @Test fun `canonical transaction ignores stale cached holdings and preserves another field`() = withTrading { store, players, trading ->
        players.snapshot(owner)
        store.atomic { tx ->
            val data = YamlFiles.parse(tx.get(SqliteProfilePersistence.key(owner))!!)
            data.set("stock.ABCD.count", 5); data.set("stock.ABCD.costBasis", 500); data.set("unknown", "external commit")
            tx.put(SqliteProfilePersistence.key(owner), data.saveToString())
        }
        trading.buy(quantity = 1)
        assertEquals(6L, players.snapshot(owner).getLong("stock.ABCD.count")); assertEquals("external commit", players.snapshot(owner).getString("unknown"))
    }

    @Test fun `malformed numeric or section holdings and quotes are not coerced to zero`() = withTrading { store, players, _ ->
        for (value in listOf(1.5, "1", true, -1)) {
            val data = players.snapshot(owner); data.set("stock.ABCD.count", value); players.save(owner, data)
            assertThrows(IllegalArgumentException::class.java) { service(store, players, 100).buy() }
            assertEquals(value, players.snapshot(owner).get("stock.ABCD.count"))
        }
        val data = players.snapshot(owner); data.set("stock.ABCD", "damaged section"); players.save(owner, data)
        assertThrows(IllegalArgumentException::class.java) { service(store, players, 100).buy() }
        assertEquals("damaged section", players.snapshot(owner).get("stock.ABCD"))
        val catalog = org.bukkit.configuration.file.YamlConfiguration()
        for (value in listOf(1.5, "100", true, 0, -1)) {
            catalog.set("ABCD.currentPrice", value)
            assertThrows(IllegalArgumentException::class.java) { StockCatalog.price(catalog, "ABCD") }
        }
    }

    @Test fun `fees are charged on both sides, rounded down, and counted in cost basis and profit`() = withTrading { store, players, _ ->
        val data = players.snapshot(owner); data.set("money", 1000); players.save(owner, data)
        val buying = StockTradingService(store, players, { 3 }) { Money(101) }
        assertEquals(Money(208), buying.purchaseCost(Money(101), 2))
        // 202원어치 매수: 수수료 floor(6.06) = 6원.
        val bought = buying.trade(UUID.randomUUID(), owner, "ABCD", Money(101), 2, StockTrade.BUY)
        assertEquals(6L, bought.fee.units); assertEquals(792L, players.profile(owner).balance)
        assertEquals(208L, bought.holding.costBasis.units)
        // 300원어치 매도: 수수료 9원, 입금 291원, 손익 = 291 - 208.
        val sold = StockTradingService(store, players, { 3 }) { Money(150) }.trade(UUID.randomUUID(), owner, "ABCD", Money(150), 2, StockTrade.SELL)
        assertEquals(9L, sold.fee.units); assertEquals(1083L, players.profile(owner).balance)
        assertEquals(83L, players.snapshot(owner).getLong("stock.ABCD.realizedProfit"))
    }

    @Test fun `the fee must fit in the balance and an invalid fee rate changes nothing`() = withTrading { store, players, _ ->
        val data = players.snapshot(owner); data.set("money", 205); players.save(owner, data)
        assertThrows(IllegalArgumentException::class.java) {
            StockTradingService(store, players, { 3 }) { Money(100) }.trade(UUID.randomUUID(), owner, "ABCD", Money(100), 2, StockTrade.BUY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockTradingService(store, players, { 101 }) { Money(100) }.trade(UUID.randomUUID(), owner, "ABCD", Money(100), 1, StockTrade.BUY)
        }
        assertEquals(205L, players.profile(owner).balance); assertFalse(players.snapshot(owner).contains("stock.ABCD"))
    }

    @Test fun `signed realized losses and profit overflow remain atomic`() = withTrading { store, players, trading ->
        trading.buy()
        service(store, players, 50).trade(UUID.randomUUID(), owner, "ABCD", Money(50), 1, StockTrade.SELL)
        assertEquals(-50L, players.snapshot(owner).getLong("stock.ABCD.realizedProfit"))
        val data = players.snapshot(owner); data.set("stock.ABCD.realizedProfit", Long.MAX_VALUE); players.save(owner, data)
        val balance = players.profile(owner).balance
        assertThrows(ArithmeticException::class.java) {
            service(store, players).trade(UUID.randomUUID(), owner, "ABCD", Money(150), 1, StockTrade.SELL)
        }
        assertEquals(balance, players.profile(owner).balance); assertEquals(1L, players.snapshot(owner).getLong("stock.ABCD.count"))
    }
}
