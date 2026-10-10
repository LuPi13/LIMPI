package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.AtomicFiles
import com.github.lupi13.limpi.storage.YamlPlayerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.UUID

class StockTradingServiceTest {
    @TempDir lateinit var directory: Path
    private val id = UUID.randomUUID()

    @Test fun `buy and sell update cash and portfolio in the same profile save`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 3_000_000_000)
        val trading = StockTradingService(repository)
        trading.trade(id, "ABCD", Money(100), 2, StockTrade.BUY)
        trading.trade(id, "ABCD", Money(150), 1, StockTrade.SELL)
        assertEquals(2_999_999_950L, repository.profile(id).balance)
        val holding = trading.holding(repository.snapshot(id), "ABCD")
        assertEquals(1L, holding.count)
        assertEquals(100L, holding.averagePrice)
    }

    @Test fun `legacy integer average is preserved when adding cost basis`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 1000)
        val legacy = repository.snapshot(id)
        legacy.set("stock.ABCD.count", 2)
        legacy.set("stock.ABCD.averagePrice", 100)
        repository.save(id, legacy)
        StockTradingService(repository).trade(id, "ABCD", Money(150), 1, StockTrade.SELL)
        val data = repository.snapshot(id)
        assertEquals(100L, data.getLong("stock.ABCD.costBasis"))
        assertEquals(100L, data.getLong("stock.ABCD.averagePrice"))
        assertEquals(1150L, data.getLong("money"))
    }

    @Test fun `unaffordable purchase does not grant shares`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 100)
        assertThrows(IllegalArgumentException::class.java) {
            StockTradingService(repository).trade(id, "ABCD", Money(101), 1, StockTrade.BUY)
        }
        assertEquals(100L, repository.profile(id).balance)
        assertFalse(repository.snapshot(id).contains("stock.ABCD"))
    }

    @Test fun `overselling does not credit cash`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 100)
        assertThrows(IllegalArgumentException::class.java) {
            StockTradingService(repository).trade(id, "ABCD", Money(10), 1, StockTrade.SELL)
        }
        assertEquals(100L, repository.profile(id).balance)
    }

    @Test fun `sale overflow does not discard shares`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", Long.MAX_VALUE)
        val data = repository.snapshot(id)
        data.set("stock.ABCD.count", 1)
        data.set("stock.ABCD.averagePrice", 1)
        repository.save(id, data)
        assertThrows(ArithmeticException::class.java) {
            StockTradingService(repository).trade(id, "ABCD", Money(1), 1, StockTrade.SELL)
        }
        assertEquals(1L, repository.snapshot(id).getLong("stock.ABCD.count"))
        assertEquals(Long.MAX_VALUE, repository.profile(id).balance)
    }

    @Test fun `failed trade write preserves both cash and portfolio`() {
        var fail = false
        val repository = YamlPlayerRepository(directory, write = { path, content ->
            if (fail) throw IOException("disk unavailable")
            AtomicFiles.write(path, content)
        })
        repository.ensure(id, "Player", 1000)
        fail = true
        assertThrows(IOException::class.java) {
            StockTradingService(repository).trade(id, "ABCD", Money(100), 2, StockTrade.BUY)
        }
        assertEquals(1000L, repository.profile(id).balance)
        assertFalse(repository.snapshot(id).contains("stock.ABCD"))
    }

    @Test fun `full sale clears acquisition basis and cash stays exact beyond Int`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 6_000_000_000)
        val trading = StockTradingService(repository)
        trading.trade(id, "ABCD", Money(3_000_000_000), 2, StockTrade.BUY)
        trading.trade(id, "ABCD", Money(3_000_000_000), 2, StockTrade.SELL)
        assertEquals(6_000_000_000L, repository.profile(id).balance)
        assertEquals(0L, trading.holding(repository.snapshot(id), "ABCD").count)
        assertEquals(0L, trading.holding(repository.snapshot(id), "ABCD").costBasis.units)
    }

    @Test fun `invalid legacy holdings fail without replacing user assets with zero`() {
        val repository = YamlPlayerRepository(directory)
        repository.ensure(id, "Player", 1000)
        val data = repository.snapshot(id)
        data.set("stock.ABCD.count", 1)
        data.set("stock.ABCD.averagePrice", -100)
        repository.save(id, data)
        assertThrows(IllegalArgumentException::class.java) {
            StockTradingService(repository).trade(id, "ABCD", Money(150), 1, StockTrade.SELL)
        }
        assertEquals(1000L, repository.profile(id).balance)
        assertEquals(1L, repository.snapshot(id).getLong("stock.ABCD.count"))
        assertEquals(-100L, repository.snapshot(id).getLong("stock.ABCD.averagePrice"))
    }
}
