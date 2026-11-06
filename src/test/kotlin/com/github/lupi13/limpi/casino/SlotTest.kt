package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Random
import java.util.UUID

class SlotTest {
    @TempDir lateinit var directory: Path
    private val owner = UUID.randomUUID()
    private val symbols = CasinoSettings().slotSymbols

    private fun withService(balance: Long = 100_000, test: (StateStore, SlotService) -> Unit) = SqliteStateStore(directory.resolve("state.db")).use { store ->
        store.atomic { it.put(SqliteProfilePersistence.key(owner), "name: gambler\nmoney: $balance\n") }
        test(store, SlotService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(3)))
    }
    private fun balance(store: StateStore) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")

    @Test fun `every three by three grid adds up to the promised return`() {
        // All 6^9 grids, weighted: the real payout function at a bet that divides evenly, without the cap.
        val weights = symbols.map { it.weight / 100.0 }
        val all = SlotSymbol.entries
        val grid = MutableList(SlotPolicy.CELLS) { all[0] }
        var expected = 0.0
        fun walk(cell: Int, chance: Double) {
            if (cell == SlotPolicy.CELLS) { expected += chance * SlotPolicy.payout(500, grid, symbols, Long.MAX_VALUE); return }
            for (index in all.indices) { grid[cell] = all[index]; walk(cell + 1, chance * weights[index]) }
        }
        walk(0, 1.0)
        assertEquals(SlotPolicy.returnRate(symbols), expected / 500, 1e-9)
        assertEquals(0.970487, SlotPolicy.returnRate(symbols), 1e-6)
    }

    @Test fun `the common win rounds down once at the smallest bet and the return stays close`() {
        val random = Random(11)
        var paid = 0L; val spins = 2_000_000
        repeat(spins) { paid += SlotPolicy.payout(100, SlotPolicy.draw(symbols, random), symbols, 10_000_000) }
        assertEquals(0.97, paid.toDouble() / (spins * 100), 0.01)
    }

    @Test fun `lines pay triple and leading pair and five netherite lines stop at the limit`() {
        val coal = SlotSymbol.COAL; val iron = SlotSymbol.IRON; val gold = SlotSymbol.GOLD
        val grid = listOf(coal, coal, coal, iron, iron, gold, gold, coal, iron) // Row 1 triple, row 2 pair, diagonals nothing.
        assertEquals(listOf(0, 1), SlotPolicy.winningLines(grid, symbols))
        assertEquals(1000L * (6 + 1) / 5, SlotPolicy.payout(1000, grid, symbols, Long.MAX_VALUE))
        assertEquals(10_000_000, SlotPolicy.payout(100_000, List(9) { SlotSymbol.NETHERITE }, symbols, 10_000_000))
    }

    @Test fun `a spin settles bet and pay in one commit and a replay does not pay twice`() = withService { store, service ->
        val request = UUID.randomUUID()
        val spin = service.spin(request, owner, 1000)
        assertEquals(100_000 - 1000 + spin.payout, balance(store)); assertFalse(spin.replayed)
        val again = service.spin(request, owner, 1000)
        assertTrue(again.replayed); assertEquals(spin.grid, again.grid); assertEquals(spin.payout, again.payout)
        assertEquals(100_000 - 1000 + spin.payout, balance(store))
    }

    @Test fun `no money or bad bet changes nothing`() = withService(balance = 50) { store, service ->
        assertThrows(CasinoUnavailable::class.java) { service.spin(UUID.randomUUID(), owner, 10) }
        assertThrows(BankException::class.java) { service.spin(UUID.randomUUID(), owner, 100) }
        assertEquals(50, balance(store))
    }
}
