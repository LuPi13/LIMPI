package com.github.lupi13.limpi.casino

import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.StateStore
import com.github.lupi13.limpi.storage.StateTransaction
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.util.Random
import java.util.UUID

/** A card is 0..51: suit * 13 + rank - 1. */
object Cards {
    fun rank(card: Int) = card % 13 + 1
    fun suit(card: Int) = card / 13
    fun red(card: Int) = suit(card) == 1 || suit(card) == 2
    fun label(card: Int): String = "♠♥♦♣"[suit(card)] + " " + when (val rank = rank(card)) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString() }
    fun shoe(decks: Int, random: Random): List<Int> = List(decks * 52) { it % 52 }.shuffled(random)
}

enum class BjPhase { BETTING, PLAYING }
enum class BjHand { WAITING, STOOD, BUST, BLACKJACK }
data class BjSeat(val owner: UUID, val bet: Long, val cards: List<Int> = emptyList(), val state: BjHand = BjHand.WAITING, val doubled: Boolean = false)
data class BjTable(val id: UUID, val phase: BjPhase, val seats: List<BjSeat>, val dealer: List<Int>, val shoe: List<Int>, val version: Long) {
    /** The seat to act; null when betting or when the dealer is next. */
    val turn: BjSeat? get() = if (phase == BjPhase.PLAYING) seats.firstOrNull { it.state == BjHand.WAITING } else null
    fun seat(owner: UUID) = seats.firstOrNull { it.owner == owner }
}
data class BjResult(val seat: BjSeat, val payout: Long)
data class BjSettlement(val tableId: UUID, val dealer: List<Int>, val results: List<BjResult>, val replayed: Boolean = false)

object BlackjackPolicy {
    const val SEATS = 4
    const val DECKS = 6

    private fun value(card: Int) = minOf(Cards.rank(card), 10)
    fun total(cards: List<Int>): Int {
        val hard = cards.sumOf(::value)
        return if (cards.any { Cards.rank(it) == 1 } && hard + 10 <= 21) hard + 10 else hard
    }
    fun soft(cards: List<Int>): Boolean = cards.any { Cards.rank(it) == 1 } && cards.sumOf(::value) + 10 <= 21
    fun natural(cards: List<Int>) = cards.size == 2 && total(cards) == 21

    /** Two cards each. A dealer natural stays hidden and every hand still plays; only the player's own natural skips the turn. */
    fun deal(table: BjTable, shoe: List<Int>): BjTable {
        require(table.phase == BjPhase.BETTING && table.seats.isNotEmpty())
        val rest = shoe.toMutableList()
        fun draw() = rest.removeFirst()
        val first = table.seats.map { it.copy(cards = listOf(draw())) }
        val dealerFirst = draw()
        val second = first.map { it.copy(cards = it.cards + draw()) }
        val dealer = listOf(dealerFirst, draw())
        val seats = second.map { seat -> if (natural(seat.cards)) seat.copy(state = BjHand.BLACKJACK) else seat }
        return table.copy(phase = BjPhase.PLAYING, seats = seats, dealer = dealer, shoe = rest, version = table.version + 1)
    }

    private fun replace(table: BjTable, seat: BjSeat, shoe: List<Int> = table.shoe) =
        table.copy(seats = table.seats.map { if (it.owner == seat.owner) seat else it }, shoe = shoe, version = table.version + 1)

    private fun acting(table: BjTable, owner: UUID): BjSeat {
        val seat = table.turn ?: throw CasinoStale()
        if (seat.owner != owner) throw CasinoStale()
        return seat
    }

    fun hit(table: BjTable, owner: UUID): BjTable {
        val seat = acting(table, owner)
        val cards = seat.cards + table.shoe.first()
        val state = when { total(cards) > 21 -> BjHand.BUST; total(cards) == 21 -> BjHand.STOOD; else -> BjHand.WAITING }
        return replace(table, seat.copy(cards = cards, state = state), table.shoe.drop(1))
    }

    fun stand(table: BjTable, owner: UUID): BjTable = replace(table, acting(table, owner).copy(state = BjHand.STOOD))

    /** One more card and the stake doubles; only on the first two cards. */
    fun double(table: BjTable, owner: UUID): BjTable {
        val seat = acting(table, owner)
        if (seat.cards.size != 2) throw CasinoUnavailable("더블은 처음 두 장일 때만 할 수 있습니다.")
        val cards = seat.cards + table.shoe.first()
        return replace(table, seat.copy(bet = seat.bet * 2, cards = cards, doubled = true,
            state = if (total(cards) > 21) BjHand.BUST else BjHand.STOOD), table.shoe.drop(1))
    }

    /** The dealer stands on every 17 and draws only if a standing hand needs beating. */
    fun dealerHand(table: BjTable): List<Int> {
        var dealer = table.dealer
        if (natural(dealer) || table.seats.none { it.state == BjHand.STOOD }) return dealer
        val shoe = table.shoe.iterator()
        while (total(dealer) < 17) dealer = dealer + shoe.next()
        return dealer
    }

    /** Stake back included; a natural pays 3:2 rounded down. */
    fun payout(seat: BjSeat, dealer: List<Int>, maxPayout: Long): Long {
        val paid = when (seat.state) {
            BjHand.BUST, BjHand.WAITING -> 0L
            BjHand.BLACKJACK -> if (natural(dealer)) seat.bet else seat.bet * 5 / 2
            BjHand.STOOD -> {
                val mine = total(seat.cards); val theirs = total(dealer)
                when {
                    natural(dealer) -> 0L
                    theirs > 21 || mine > theirs -> seat.bet * 2
                    mine == theirs -> seat.bet
                    else -> 0L
                }
            }
        }
        return minOf(paid, maxPayout)
    }

    fun settle(table: BjTable, maxPayout: Long): BjSettlement {
        require(table.turn == null && table.phase == BjPhase.PLAYING)
        val dealer = dealerHand(table)
        return BjSettlement(table.id, dealer, table.seats.map { BjResult(it, payout(it, dealer, maxPayout)) })
    }
}

/**
 * The stored table is the truth; the screen keeps only timers. Every hand is frozen in the shoe saved at the deal, so a
 * restart finishes the round with the same cards. Each player sits at one table at a time.
 */
class BlackjackService(private val store: StateStore, private val players: PlayerRepository, private val settings: CasinoSettings, private val random: Random) {
    private fun key(id: UUID) = CasinoKeys.table(CasinoGame.BLACKJACK, id)
    private val prefix = CasinoKeys.TABLE_PREFIX + CasinoGame.BLACKJACK.code + "/"

    fun table(id: UUID): BjTable? = store.get(key(id))?.let { decode(key(id), it) }

    /** Tables a damaged neighbour cannot hide: unreadable ones are skipped here and reported by [CasinoServices.damaged]. */
    fun tables(): List<BjTable> = store.documents(prefix).mapNotNull { (key, payload) -> runCatching { decode(key, payload) }.getOrNull() }

    fun bet(request: UUID, owner: UUID, tableId: UUID, amount: Long): BjTable {
        settings.checkBet(amount)
        val committed = store.execute(request, BankService.fingerprint("CASINO_BLACKJACK_BET", owner.toString(), tableId.toString(), amount.toString())) { tx ->
            val stored = tx.get(key(tableId))?.let { decode(key(tableId), it) }
            val table = stored ?: BjTable(tableId, BjPhase.BETTING, emptyList(), emptyList(), emptyList(), 0)
            if (table.phase != BjPhase.BETTING) throw CasinoUnavailable("이미 진행 중인 판입니다. 다음 판에 베팅하세요.")
            if (table.seats.size >= BlackjackPolicy.SEATS) throw CasinoUnavailable("좌석이 모두 찼습니다.")
            if (seated(tx, owner)) throw CasinoUnavailable("이미 베팅한 판이 있습니다.")
            debit(tx, owner, amount)
            tx.put(key(tableId), encode(table.copy(seats = table.seats + BjSeat(owner, amount), version = table.version + 1)))
            YamlConfiguration().apply { set("actor", owner.toString()); set("game", "blackjack"); set("table", tableId.toString()); set("bet", amount) }.saveToString()
        }
        players.invalidateChanged(committed)
        return table(tableId) ?: throw CasinoStale()
    }

    private fun seated(tx: StateTransaction, owner: UUID) = tx.documents(prefix).any { (documentKey, payload) ->
        runCatching { decode(documentKey, payload) }.getOrNull()?.seat(owner) != null
    }

    fun deal(id: UUID): BjTable = update(id) { table ->
        if (table.phase != BjPhase.BETTING) throw CasinoStale()
        BlackjackPolicy.deal(table, Cards.shoe(BlackjackPolicy.DECKS, random))
    }

    fun hit(owner: UUID, id: UUID, version: Long): BjTable = update(id, version) { BlackjackPolicy.hit(it, owner) }
    fun stand(owner: UUID, id: UUID, version: Long): BjTable = update(id, version) { BlackjackPolicy.stand(it, owner) }

    fun double(request: UUID, owner: UUID, id: UUID, version: Long): BjTable {
        val committed = store.execute(request, BankService.fingerprint("CASINO_BLACKJACK_DOUBLE", owner.toString(), id.toString(), version.toString())) { tx ->
            val table = read(tx, id, version)
            val stake = table.seat(owner)?.bet ?: throw CasinoStale()
            val next = BlackjackPolicy.double(table, owner)
            debit(tx, owner, stake)
            tx.put(key(id), encode(next))
            YamlConfiguration().apply { set("actor", owner.toString()); set("game", "blackjack"); set("table", id.toString()); set("double", stake) }.saveToString()
        }
        players.invalidateChanged(committed)
        return table(id) ?: throw CasinoStale()
    }

    /** Dealer's cards and every seat's pay commit together, the seats of offline players included. */
    fun settle(request: UUID, id: UUID): BjSettlement {
        val committed = store.execute(request, BankService.fingerprint("CASINO_BLACKJACK_SETTLE", id.toString())) { tx ->
            val table = tx.get(key(id))?.let { decode(key(id), it) } ?: throw CasinoStale()
            val settlement = BlackjackPolicy.settle(table, settings.maxPayout)
            settlement.results.forEach { credit(tx, it.seat.owner, it.payout) }
            tx.put(key(id), null)
            encodeSettlement(settlement)
        }
        players.invalidateChanged(committed)
        return decodeSettlement(id, committed.entry.receipt).copy(replayed = committed.replayed)
    }

    /** Server start: stakes of a round that was never dealt go back; a dealt round finishes with its frozen cards, everyone standing. */
    fun recover(log: (String) -> Unit) {
        for (table in tables()) {
            try {
                if (table.phase == BjPhase.BETTING) refund(UUID.randomUUID(), table)
                else {
                    store.atomic { tx -> tx.put(key(table.id), encode(table.copy(seats = table.seats.map { if (it.state == BjHand.WAITING) it.copy(state = BjHand.STOOD) else it }, version = table.version + 1))) }
                    settle(UUID.randomUUID(), table.id)
                }
                log("Blackjack table ${table.id} was finished after the restart")
            } catch (failure: Exception) {
                log("Blackjack table ${table.id} could not be finished, it was kept: $failure")
            }
        }
    }

    private fun refund(request: UUID, table: BjTable) {
        val committed = store.execute(request, BankService.fingerprint("CASINO_BLACKJACK_REFUND", table.id.toString())) { tx ->
            table.seats.forEach { credit(tx, it.owner, it.bet) }
            tx.put(key(table.id), null)
            YamlConfiguration().apply { set("game", "blackjack"); set("table", table.id.toString()); set("refund", table.seats.sumOf { it.bet }) }.saveToString()
        }
        players.invalidateChanged(committed)
    }

    private fun read(tx: StateTransaction, id: UUID, version: Long): BjTable {
        val table = tx.get(key(id))?.let { decode(key(id), it) } ?: throw CasinoStale()
        if (table.version != version) throw CasinoStale()
        return table
    }

    private fun update(id: UUID, version: Long? = null, change: (BjTable) -> BjTable): BjTable {
        lateinit var next: BjTable
        store.atomic { tx ->
            val table = tx.get(key(id))?.let { decode(key(id), it) } ?: throw CasinoStale()
            if (version != null && table.version != version) throw CasinoStale()
            next = change(table)
            tx.put(key(id), encode(next))
        }
        return next
    }

    companion object {
        private fun writeSeat(section: ConfigurationSection, seat: BjSeat) {
            section.set("owner", seat.owner.toString()); section.set("bet", seat.bet); section.set("cards", seat.cards)
            section.set("state", seat.state.name); section.set("doubled", seat.doubled)
        }
        private fun readSeat(section: ConfigurationSection): BjSeat {
            require(section.getLong("bet") > 0)
            return BjSeat(UUID.fromString(section.getString("owner")), section.getLong("bet"), cards(section, "cards"),
                BjHand.valueOf(requireNotNull(section.getString("state"))), section.getBoolean("doubled"))
        }
        private fun cards(section: ConfigurationSection, path: String) = section.getIntegerList(path).onEach { require(it in 0 until 52) }

        fun encode(table: BjTable): String = YamlConfiguration().apply {
            set("id", table.id.toString()); set("phase", table.phase.name); set("dealer", table.dealer); set("shoe", table.shoe); set("version", table.version)
            table.seats.forEachIndexed { index, seat -> writeSeat(createSection("seats.$index"), seat) }
        }.saveToString()

        fun decode(key: String, payload: String): BjTable = decoding(key) {
            val data = YamlFiles.parse(payload)
            val seats = data.getConfigurationSection("seats")?.let { section -> section.getKeys(false).sortedBy(String::toInt).map { readSeat(section.getConfigurationSection(it)!!) } } ?: emptyList()
            BjTable(UUID.fromString(data.getString("id")), BjPhase.valueOf(requireNotNull(data.getString("phase"))), seats,
                cards(data, "dealer"), cards(data, "shoe"), data.getLong("version"))
        }

        private fun encodeSettlement(settlement: BjSettlement) = YamlConfiguration().apply {
            set("game", "blackjack"); set("table", settlement.tableId.toString()); set("dealer", settlement.dealer)
            settlement.results.forEachIndexed { index, result ->
                writeSeat(createSection("results.$index"), result.seat); set("results.$index.payout", result.payout)
            }
        }.saveToString()

        private fun decodeSettlement(id: UUID, receipt: String): BjSettlement {
            val data = YamlFiles.parse(receipt)
            val section = requireNotNull(data.getConfigurationSection("results"))
            return BjSettlement(id, data.getIntegerList("dealer"), section.getKeys(false).sortedBy(String::toInt).map {
                val seat = section.getConfigurationSection(it)!!
                BjResult(readSeat(seat), seat.getLong("payout"))
            })
        }
    }
}
