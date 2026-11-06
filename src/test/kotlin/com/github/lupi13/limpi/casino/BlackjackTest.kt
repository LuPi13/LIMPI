package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Random
import java.util.UUID

class BlackjackTest {
    @TempDir lateinit var directory: Path
    private val first = UUID.randomUUID(); private val second = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val maxPayout = 10_000_000L

    // Spades: rank n is card n - 1.
    private fun card(rank: Int) = rank - 1

    private fun withService(balance: Long = 100_000, test: (StateStore, BlackjackService) -> Unit) = SqliteStateStore(directory.resolve("state.db")).use { store ->
        store.atomic { tx -> listOf(first, second).forEach { tx.put(SqliteProfilePersistence.key(it), "name: gambler\nmoney: $balance\n") } }
        test(store, BlackjackService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(5)))
    }
    private fun balance(store: StateStore, owner: UUID) = YamlFiles.parse(store.get(SqliteProfilePersistence.key(owner))).getLong("money")
    private fun betting(vararg owners: UUID) = BjTable(id, BjPhase.BETTING, owners.map { BjSeat(it, 100) }, emptyList(), emptyList(), 0)
    private fun put(store: StateStore, table: BjTable) = store.atomic { it.put(CasinoKeys.table(CasinoGame.BLACKJACK, table.id), BlackjackService.encode(table)) }

    @Test fun `aces count high only while they fit`() {
        assertEquals(21, BlackjackPolicy.total(listOf(card(1), card(13))))
        assertTrue(BlackjackPolicy.natural(listOf(card(1), card(13))))
        assertEquals(12, BlackjackPolicy.total(listOf(card(1), card(1))))
        assertEquals(13, BlackjackPolicy.total(listOf(card(1), card(9), card(3))))
        assertFalse(BlackjackPolicy.natural(listOf(card(7), card(7), card(7))))
        assertTrue(BlackjackPolicy.soft(listOf(card(1), card(6)))); assertFalse(BlackjackPolicy.soft(listOf(card(1), card(6), card(10))))
    }

    @Test fun `the deal goes seat by seat and the dealer stands on soft seventeen`() {
        // First: 10, 9. Second: 5, 6. Dealer: A, 6 (soft 17 stands).
        val dealt = BlackjackPolicy.deal(betting(first, second), listOf(card(10), card(5), card(1), card(9), card(6), card(6), card(2)))
        assertEquals(listOf(card(10), card(9)), dealt.seat(first)!!.cards); assertEquals(listOf(card(5), card(6)), dealt.seat(second)!!.cards)
        assertEquals(first, dealt.turn!!.owner)
        val done = BlackjackPolicy.stand(BlackjackPolicy.stand(dealt, first), second)
        assertNull(done.turn)
        val settled = BlackjackPolicy.settle(done, maxPayout)
        assertEquals(listOf(card(1), card(6)), settled.dealer)
        assertEquals(listOf(200L, 0L), settled.results.map { it.payout }) // 19 beats 17, 11 loses.
    }

    @Test fun `a natural pays three to two rounded down and a dealer natural beats everything else`() {
        val odd = BjSeat(first, 101, listOf(card(1), card(13)), BjHand.BLACKJACK)
        assertEquals(252, BlackjackPolicy.payout(odd, listOf(card(10), card(7)), maxPayout))
        assertEquals(101, BlackjackPolicy.payout(odd, listOf(card(1), card(10)), maxPayout))
        val dealt = BlackjackPolicy.deal(betting(first), listOf(card(10), card(1), card(9), card(13)))
        assertNull(dealt.turn) // Nobody acts into a dealer natural.
        assertEquals(0L, BlackjackPolicy.settle(dealt, maxPayout).results.single().payout)
    }

    @Test fun `a hit past twenty one busts and the dealer does not draw for nobody`() {
        val dealt = BlackjackPolicy.deal(betting(first), listOf(card(10), card(5), card(9), card(8), card(5), card(2)))
        val bust = BlackjackPolicy.hit(dealt, first)
        assertEquals(BjHand.BUST, bust.seats.single().state); assertNull(bust.turn)
        val settled = BlackjackPolicy.settle(bust, maxPayout)
        assertEquals(listOf(card(5), card(8)), settled.dealer); assertEquals(0L, settled.results.single().payout)
    }

    @Test fun `only the seat in turn acts`() {
        val dealt = BlackjackPolicy.deal(betting(first, second), listOf(card(10), card(5), card(5), card(9), card(6), card(6), card(2)))
        assertThrows(CasinoStale::class.java) { BlackjackPolicy.hit(dealt, second) }
        assertThrows(CasinoStale::class.java) { BlackjackPolicy.stand(dealt, UUID.randomUUID()) }
    }

    /** Hit, stand and double only, with the usual chart for this rule set. */
    private fun chart(cards: List<Int>, up: Int, canDouble: Boolean): Char {
        val total = BlackjackPolicy.total(cards); val dealer = minOf(Cards.rank(up), 10).let { if (it == 1) 11 else it }
        fun double(fallback: Char) = if (canDouble) 'D' else fallback
        if (BlackjackPolicy.soft(cards)) return when (total) {
            13, 14 -> if (dealer in 5..6) double('H') else 'H'
            15, 16 -> if (dealer in 4..6) double('H') else 'H'
            17 -> if (dealer in 3..6) double('H') else 'H'
            18 -> when (dealer) { in 3..6 -> double('S'); 2, 7, 8 -> 'S'; else -> 'H' }
            else -> if (total >= 19) 'S' else 'H'
        }
        return when {
            total <= 8 -> 'H'
            total == 9 -> if (dealer in 3..6) double('H') else 'H'
            total == 10 -> if (dealer in 2..9) double('H') else 'H'
            total == 11 -> if (dealer in 2..10) double('H') else 'H'
            total == 12 -> if (dealer in 4..6) 'S' else 'H'
            total in 13..16 -> if (dealer in 2..6) 'S' else 'H'
            else -> 'S'
        }
    }

    @Test fun `good play returns about ninety nine percent without splits`() {
        val random = Random(21)
        var staked = 0L; var paid = 0L
        val hands = 300_000
        repeat(hands) {
            var table = BlackjackPolicy.deal(betting(first), Cards.shoe(BlackjackPolicy.DECKS, random))
            while (table.turn != null) {
                val seat = table.turn!!
                table = when (chart(seat.cards, table.dealer[0], seat.cards.size == 2)) {
                    'H' -> BlackjackPolicy.hit(table, first)
                    'D' -> BlackjackPolicy.double(table, first)
                    else -> BlackjackPolicy.stand(table, first)
                }
            }
            val result = BlackjackPolicy.settle(table, maxPayout).results.single()
            staked += result.seat.bet; paid += result.payout
        }
        val rate = paid.toDouble() / staked
        println("Blackjack return with basic strategy, no split: $rate")
        assertTrue(rate in 0.975..1.005, "$rate")
    }

    @Test fun `bets debit and freeze a seat per player and a full round settles everyone at once`() = withService { store, service ->
        service.bet(UUID.randomUUID(), first, id, 1000)
        service.bet(UUID.randomUUID(), second, id, 2000)
        assertEquals(99_000, balance(store, first)); assertEquals(98_000, balance(store, second))
        assertThrows(CasinoUnavailable::class.java) { service.bet(UUID.randomUUID(), first, UUID.randomUUID(), 1000) }
        assertEquals(99_000, balance(store, first))
        put(store, BlackjackPolicy.deal(service.table(id)!!, listOf(card(10), card(10), card(9), card(9), card(9), card(9), card(5))))
        val version = service.table(id)!!.version
        assertThrows(CasinoStale::class.java) { service.stand(first, id, version - 1) }
        val after = service.stand(second, id, service.stand(first, id, version).version)
        assertNull(after.turn)
        val request = UUID.randomUUID()
        val settled = service.settle(request, id) // Both 19 against dealer 9 + 9 = 18.
        assertEquals(listOf(2000L, 4000L), settled.results.map { it.payout })
        assertEquals(101_000, balance(store, first)); assertEquals(102_000, balance(store, second))
        assertNull(service.table(id))
        val replay = service.settle(request, id)
        assertTrue(replay.replayed); assertEquals(settled.results.map { it.payout }, replay.results.map { it.payout })
        assertEquals(101_000, balance(store, first))
    }

    @Test fun `double takes the stake again or refuses without money`() = withService(balance = 1500) { store, service ->
        service.bet(UUID.randomUUID(), first, id, 1000)
        put(store, BlackjackPolicy.deal(service.table(id)!!, listOf(card(5), card(9), card(6), card(9), card(10))))
        assertThrows(BankException::class.java) { service.double(UUID.randomUUID(), first, id, service.table(id)!!.version) }
        assertEquals(500, balance(store, first)); assertEquals(1000, service.table(id)!!.seat(first)!!.bet)
        store.atomic { it.put(SqliteProfilePersistence.key(first), "name: gambler\nmoney: 1000\n") }
        val doubled = service.double(UUID.randomUUID(), first, id, service.table(id)!!.version)
        assertEquals(2000, doubled.seat(first)!!.bet); assertEquals(0, balance(store, first))
        assertEquals(BjHand.STOOD, doubled.seat(first)!!.state)
    }

    @Test fun `a restart refunds an undealt round and finishes a dealt one with the frozen cards`() {
        withService { store, service ->
            service.bet(UUID.randomUUID(), first, id, 1000)
            val other = UUID.randomUUID()
            service.bet(UUID.randomUUID(), second, other, 2000)
            put(store, BlackjackPolicy.deal(service.table(other)!!, listOf(card(10), card(10), card(9), card(7), card(5))))
        }
        SqliteStateStore(directory.resolve("state.db")).use { store ->
            val service = BlackjackService(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings(), Random(99))
            val log = mutableListOf<String>()
            service.recover(log::add)
            assertEquals(2, log.size); assertTrue(service.tables().isEmpty())
            assertEquals(100_000, balance(store, first)) // Refunded.
            assertEquals(102_000, balance(store, second)) // 19 stood against 17: won.
        }
    }

    @Test fun `a damaged table is kept while others recover`() = withService { store, service ->
        val damaged = CasinoKeys.table(CasinoGame.BLACKJACK, UUID.randomUUID())
        store.atomic { it.put(damaged, "id: nonsense\n") }
        service.bet(UUID.randomUUID(), first, id, 1000)
        service.recover { }
        assertEquals(100_000, balance(store, first)); assertNotNull(store.get(damaged))
        assertTrue(CasinoServices(store, CachedPlayerRepository(SqliteProfilePersistence(store)), CasinoSettings()).damaged().any { it.key == damaged })
    }
}
