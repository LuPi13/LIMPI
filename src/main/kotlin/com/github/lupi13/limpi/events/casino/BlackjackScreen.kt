package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.casino.BjHand
import com.github.lupi13.limpi.casino.BjPhase
import com.github.lupi13.limpi.casino.BjResult
import com.github.lupi13.limpi.casino.BjSeat
import com.github.lupi13.limpi.casino.BjSettlement
import com.github.lupi13.limpi.casino.BjTable
import com.github.lupi13.limpi.casino.BlackjackPolicy
import com.github.lupi13.limpi.casino.CasinoStale
import com.github.lupi13.limpi.casino.Cards
import com.github.lupi13.limpi.casino.countdownBeep
import com.github.lupi13.limpi.menu.MenuAction
import com.github.lupi13.limpi.menu.MenuHolder
import com.github.lupi13.limpi.menu.openMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

/**
 * Up to four players share a room; everybody has their own window drawn from the same stored table. Row 0 is the dealer,
 * rows 1-4 the seats (head, seven cards, total), row 5 the buttons. Timers live here and die with a restart, when the
 * service finishes the round anyway.
 */
class BlackjackScreen(private val plugin: Plugin) : CasinoScreen {
    override val code = "blackjack"

    private inner class Room {
        val views = linkedMapOf<UUID, MenuHolder>()
        var round: UUID = UUID.randomUUID()
        var table: BjTable? = null
        var settled: BjSettlement? = null
        /** The dealer's final hand while it is turned over card by card; the pay commits with the last card. */
        var revealing: List<Int>? = null
        var revealed = 0
        var nextReveal = 0L
        var deadline = 0L
        var resultUntil = 0L
        val members: Set<UUID> get() = views.keys + table?.seats.orEmpty().map { it.owner }
    }
    private val rooms = mutableListOf<Room>()
    private var now = 0L

    private fun settings() = CasinoUi.settings

    override fun open(player: Player) {
        val id = player.uniqueId
        val room = rooms.firstOrNull { id in it.members } ?: rooms.firstOrNull { it.members.size < BlackjackPolicy.SEATS } ?: Room().also { rooms += it }
        val holder = CasinoUi.holder("블랙잭")
        room.views[id] = holder
        draw(room, id, holder)
        openMenu(plugin, player, holder.inventory)
    }

    override fun closed(player: Player, holder: MenuHolder) {
        val room = rooms.firstOrNull { it.views[player.uniqueId] === holder } ?: return
        room.views.remove(player.uniqueId)
        // Whoever leaves without betting must not hold the others up. If this fails the betting timer tries again.
        try { startIfEveryoneBet(room) } catch (failure: Exception) { plugin.logger.log(Level.WARNING, "Blackjack table ${room.round} could not start yet", failure) }
    }

    /** A window that never opened (dead on the next tick) gets no close event, so leaving the game clears the view too. */
    override fun release(id: UUID) { rooms.forEach { it.views.remove(id) } }

    private fun startIfEveryoneBet(room: Room) {
        val table = room.table ?: return
        if (table.phase == BjPhase.BETTING && room.members.all { table.seat(it) != null }) deal(room)
    }

    private fun deal(room: Room) {
        room.table = FileManager.casino.blackjack.deal(room.round)
        room.deadline = now + settings().blackjackTurnSeconds * 20L
        sound(room, Sound.ITEM_BOOK_PAGE_TURN)
        advance(room)
    }

    /** After any change: when no seat is left to act, the dealer starts turning cards over. A natural gets here right after the deal. */
    private fun advance(room: Room) {
        val table = room.table ?: return
        if (table.phase == BjPhase.PLAYING && table.turn == null && room.revealing == null) {
            room.revealing = BlackjackPolicy.dealerHand(table)
            room.revealed = 1
            room.nextReveal = now + REVEAL_TICKS
        }
        drawAll(room)
    }

    private fun settle(room: Room) {
        val settlement = FileManager.casino.blackjack.settle(UUID.randomUUID(), room.round)
        room.settled = settlement; room.table = null; room.revealing = null
        room.resultUntil = now + RESULT_TICKS
        settlement.results.forEach { announce(settlement, it) }
    }

    private fun announce(settlement: BjSettlement, result: BjResult) {
        val player = plugin.server.getPlayer(result.seat.owner) ?: return
        val dealerNatural = BlackjackPolicy.natural(settlement.dealer)
        val dealer = if (dealerNatural) "딜러 블랙잭" else "딜러 ${BlackjackPolicy.total(settlement.dealer)}"
        val won = result.payout > result.seat.bet
        val message = when {
            won -> Component.text(if (result.seat.state == BjHand.BLACKJACK) "블랙잭! " else "블랙잭 승리! ", NamedTextColor.GREEN).append(moneyDisplay(result.payout)).append(Component.text("원이 계좌에 입금되었습니다.", NamedTextColor.WHITE))
            result.payout == result.seat.bet -> Component.text("블랙잭 무승부. ($dealer) ", NamedTextColor.YELLOW).append(moneyDisplay(result.payout)).append(Component.text("원을 돌려받았습니다.", NamedTextColor.WHITE))
            else -> Component.text("블랙잭 패배. ($dealer) ", NamedTextColor.RED).append(moneyDisplay(result.seat.bet)).append(Component.text("원을 잃었습니다.", NamedTextColor.RED))
        }
        player.sendMessage(message)
        when {
            won -> player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f)
            result.payout == result.seat.bet -> player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1f)
            else -> player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f)
        }
    }

    private fun sound(room: Room, sound: Sound) {
        room.views.keys.forEach { id -> plugin.server.getPlayer(id)?.let { it.playSound(it.location, sound, 0.8f, 1f) } }
    }

    override fun tick() {
        now++
        for (room in rooms.toList()) try { tick(room) } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Blackjack table ${room.round} stopped; its record is kept and finishes at the next restart", failure)
            room.views.keys.forEach { id -> plugin.server.getPlayer(id)?.let { CasinoUi.refuse(it, "정산 중 문제가 발생했습니다. 서버 재시작 시 정산됩니다.") } }
            room.table = null; room.settled = null; room.revealing = null; room.round = UUID.randomUUID()
        }
        rooms.removeAll { it.members.isEmpty() && it.table == null && it.settled == null }
    }

    private fun tick(room: Room) {
        room.settled?.let {
            if (now >= room.resultUntil) { room.settled = null; room.round = UUID.randomUUID(); drawAll(room) }
            return
        }
        room.revealing?.let { hand ->
            if (now >= room.nextReveal) {
                room.revealed++
                room.nextReveal = now + REVEAL_TICKS
                sound(room, Sound.ITEM_BOOK_PAGE_TURN)
                if (room.revealed >= hand.size) settle(room)
                drawAll(room)
            }
            return
        }
        val table = room.table ?: return
        val turn = table.turn
        if ((table.phase == BjPhase.BETTING || turn != null) && countdownBeep(room.deadline - now)) CasinoUi.beep(room.views.keys, plugin)
        when {
            table.phase == BjPhase.BETTING -> if (now >= room.deadline) deal(room)
            turn != null && (now >= room.deadline || plugin.server.getPlayer(turn.owner) == null) -> {
                room.table = FileManager.casino.blackjack.stand(turn.owner, room.round, table.version)
                room.deadline = now + settings().blackjackTurnSeconds * 20L
                advance(room)
            }
        }
        if (now % 20 == 0L && room.table != null) drawAll(room)
    }

    override fun click(player: Player, holder: MenuHolder, action: MenuAction.Casino, click: ClickType) {
        val id = player.uniqueId
        val room = rooms.firstOrNull { it.views[id] === holder } ?: return
        val service = FileManager.casino.blackjack
        CasinoUi.guarded(plugin, player, { drawAll(room) }) {
            when (action.action) {
                "amount" -> CasinoUi.adjustBet(player, click)
                "place" -> {
                    if (room.settled != null) throw CasinoStale()
                    val first = room.table == null
                    room.table = service.bet(UUID.randomUUID(), id, room.round, CasinoUi.betAmount(player))
                    if (first) room.deadline = now + settings().blackjackBettingSeconds * 20L
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    startIfEveryoneBet(room)
                }
                "hit", "stand", "double" -> {
                    room.table = when (action.action) {
                        "hit" -> service.hit(id, room.round, action.version)
                        "stand" -> service.stand(id, room.round, action.version)
                        else -> service.double(UUID.randomUUID(), id, room.round, action.version)
                    }
                    room.deadline = now + settings().blackjackTurnSeconds * 20L
                    player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
                    advance(room)
                }
            }
        }
    }

    private fun drawAll(room: Room) { room.views.forEach { (id, holder) -> draw(room, id, holder) } }

    private val suitDyes = listOf(Material.BLACK_DYE, Material.RED_DYE, Material.PINK_DYE, Material.GRAY_DYE) // ♠♥♦♣

    private fun cardItem(card: Int): ItemStack = CasinoUi.item(suitDyes[Cards.suit(card)],
        CasinoUi.text(Cards.label(card), if (Cards.red(card)) NamedTextColor.RED else NamedTextColor.WHITE), amount = minOf(Cards.rank(card), 10)) // J, Q and K score 10, so they show 10.

    /** Seven card slots. A longer hand folds its earliest cards into the first slot so the total stays honest. */
    private fun drawCards(holder: MenuHolder, row: Int, cards: List<Int>, hidden: Boolean = false) {
        val shown = if (cards.size > 7) cards.takeLast(6) else cards
        var slot = row * 9 + 1
        if (cards.size > 7) {
            val folded = cards.dropLast(6)
            holder.inventory.setItem(slot++, CasinoUi.item(Material.BOOK, CasinoUi.text("카드 ${folded.size}장", NamedTextColor.YELLOW), folded.map { CasinoUi.text(Cards.label(it)) }))
        }
        shown.forEachIndexed { index, card ->
            holder.inventory.setItem(slot + index, if (hidden && index > 0) CasinoUi.item(Material.BLUE_STAINED_GLASS_PANE, CasinoUi.text("?", NamedTextColor.GRAY)) else cardItem(card))
        }
    }

    private fun head(owner: UUID, marked: Boolean): ItemStack = ItemStack(Material.PLAYER_HEAD).apply {
        val meta = itemMeta as SkullMeta
        val offline = plugin.server.getOfflinePlayer(owner)
        meta.owningPlayer = offline
        meta.displayName(CasinoUi.text(offline.name ?: "?", if (marked) NamedTextColor.GREEN else NamedTextColor.WHITE))
        itemMeta = meta
    }

    private fun stateLabel(seat: BjSeat) = when (seat.state) {
        BjHand.WAITING -> "대기"; BjHand.STOOD -> "스탠드"; BjHand.BUST -> "버스트"; BjHand.BLACKJACK -> "블랙잭"
    }

    private fun draw(room: Room, viewer: UUID, holder: MenuHolder) {
        val inventory = holder.inventory
        inventory.clear(); holder.actions.clear()
        val table = room.table; val settled = room.settled
        // Dealer row.
        inventory.setItem(0, CasinoUi.item(Material.WITHER_SKELETON_SKULL, CasinoUi.text("딜러", NamedTextColor.GOLD)))
        val revealing = room.revealing
        val dealer = settled?.dealer ?: revealing?.take(room.revealed) ?: table?.dealer.orEmpty()
        val open = settled != null || revealing != null
        if (dealer.isNotEmpty()) {
            drawCards(holder, 0, dealer, hidden = !open)
            if (open) inventory.setItem(8, CasinoUi.item(Material.CLOCK, CasinoUi.text("딜러 합계: ${BlackjackPolicy.total(dealer)}", NamedTextColor.GOLD)))
        }
        // Seats.
        val seats = settled?.results?.map { it.seat } ?: table?.seats.orEmpty()
        val turn = table?.turn
        seats.forEachIndexed { index, seat ->
            val row = index + 1
            inventory.setItem(row * 9, head(seat.owner, turn?.owner == seat.owner))
            drawCards(holder, row, seat.cards)
            val payout = settled?.results?.get(index)?.payout
            val lore = mutableListOf(CasinoUi.money("베팅: ", seat.bet))
            if (seat.cards.isNotEmpty()) lore += CasinoUi.text(stateLabel(seat), NamedTextColor.GRAY)
            if (payout != null) lore += CasinoUi.money("지급: ", payout)
            inventory.setItem(row * 9 + 8, CasinoUi.item(Material.GOLD_NUGGET,
                CasinoUi.text(if (seat.cards.isEmpty()) "베팅 완료" else "합계: ${BlackjackPolicy.total(seat.cards)}", NamedTextColor.YELLOW), lore))
        }
        // Buttons.
        CasinoUi.back(holder, 53)
        plugin.server.getPlayer(viewer)?.let { player ->
            val canBet = settled == null && (table == null || (table.phase == BjPhase.BETTING && table.seat(viewer) == null))
            if (canBet) {
                inventory.setItem(45, CasinoUi.betItem(player)); holder.actions[45] = MenuAction.Casino(code, "amount")
                inventory.setItem(46, CasinoUi.item(Material.LIME_CONCRETE, CasinoUi.text("베팅", NamedTextColor.GREEN), listOf(CasinoUi.money("베팅: ", CasinoUi.betAmount(player)))))
                holder.actions[46] = MenuAction.Casino(code, "place")
            }
        }
        if (table != null && turn?.owner == viewer) {
            inventory.setItem(48, CasinoUi.item(Material.LIME_DYE, CasinoUi.text("히트", NamedTextColor.GREEN), listOf(CasinoUi.text("한 장 더 받습니다."))))
            holder.actions[48] = MenuAction.Casino(code, "hit", 0, table.version)
            inventory.setItem(49, CasinoUi.item(Material.RED_CONCRETE, CasinoUi.text("스탠드", NamedTextColor.RED), listOf(CasinoUi.text("지금 합계로 승부합니다."))))
            holder.actions[49] = MenuAction.Casino(code, "stand", 0, table.version)
            if (turn.cards.size == 2) {
                inventory.setItem(50, CasinoUi.item(Material.GOLD_INGOT, CasinoUi.text("더블", NamedTextColor.GOLD), listOf(CasinoUi.money("추가 베팅: ", turn.bet), CasinoUi.text("한 장만 더 받고 스탠드합니다."))))
                holder.actions[50] = MenuAction.Casino(code, "double", 0, table.version)
            }
        }
        if (table != null && revealing == null) {
            val seconds = ((room.deadline - now).coerceAtLeast(0) + 19) / 20
            val label = if (table.phase == BjPhase.BETTING) "베팅 마감까지 ${seconds}초" else "${turn?.let { plugin.server.getOfflinePlayer(it.owner).name } ?: ""} 차례: ${seconds}초"
            inventory.setItem(52, CasinoUi.item(Material.CLOCK, CasinoUi.text(label, NamedTextColor.YELLOW), amount = seconds.toInt()))
        }
    }

    private companion object {
        const val RESULT_TICKS = 100L
        const val REVEAL_TICKS = 20L
    }
}
