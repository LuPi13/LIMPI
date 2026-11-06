package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.casino.CasinoStale
import com.github.lupi13.limpi.casino.CasinoUnavailable
import com.github.lupi13.limpi.casino.RACE_SHEEP
import com.github.lupi13.limpi.casino.RacePhase
import com.github.lupi13.limpi.casino.RacePolicy
import com.github.lupi13.limpi.casino.RaceRound
import com.github.lupi13.limpi.casino.RaceSettlement
import com.github.lupi13.limpi.menu.MenuAction
import com.github.lupi13.limpi.menu.MenuHolder
import com.github.lupi13.limpi.menu.openMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

/**
 * Each row is a lane: a sheep starting in column 0, where it takes bets, the goal in column 7, controls in column 8.
 * The first bet starts the countdown, and the race on screen is drawn from the frozen winner and seed.
 */
class RaceScreen(private val plugin: Plugin) : CasinoScreen {
    override val code = "race"
    private val views = linkedMapOf<UUID, MenuHolder>()
    private var id: UUID = UUID.randomUUID()
    private var odds: List<Int>? = null
    private var round: RaceRound? = null
    private var path: List<List<Int>> = emptyList()
    private var settled: RaceSettlement? = null
    private var positions: List<Int> = List(RACE_SHEEP) { 0 }
    private var now = 0L
    private var deadline = 0L
    private var startedAt = 0L
    private var resultUntil = 0L

    private val settings get() = CasinoUi.settings
    private val service get() = FileManager.casino.race
    private fun shownOdds() = round?.odds ?: odds ?: service.drawOdds().also { odds = it }

    private val wool = listOf(Material.WHITE_WOOL, Material.PINK_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.ORANGE_WOOL)
    private val names = listOf("하양", "분홍", "연두", "하늘", "노랑", "주황")

    override fun open(player: Player) {
        val holder = CasinoUi.holder("양 경주")
        views[player.uniqueId] = holder
        draw(player.uniqueId, holder)
        openMenu(plugin, player, holder.inventory)
    }

    override fun closed(player: Player, holder: MenuHolder) {
        if (views[player.uniqueId] === holder) views.remove(player.uniqueId)
    }

    override fun release(id: UUID) { views.remove(id) }

    override fun click(player: Player, holder: MenuHolder, action: MenuAction.Casino, click: ClickType) {
        CasinoUi.guarded(plugin, player, { drawAll() }) {
            when (action.action) {
                "amount" -> CasinoUi.adjustBet(player, click)
                "sheep" -> {
                    if (settled != null) throw CasinoStale()
                    if (click.isRightClick) {
                        if (round == null) throw CasinoUnavailable("이 양에 베팅한 금액이 없습니다.")
                        round = service.withdraw(UUID.randomUUID(), player.uniqueId, id, action.argument, CasinoUi.betAmount(player))
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 0.8f)
                    } else {
                        val first = round == null
                        round = service.bet(UUID.randomUUID(), player.uniqueId, id, action.argument, CasinoUi.betAmount(player), shownOdds())
                        if (first) deadline = now + settings.raceBettingSeconds * 20L
                        player.playSound(player.location, Sound.ENTITY_SHEEP_AMBIENT, 0.8f, 1f)
                    }
                }
            }
        }
    }

    override fun tick() {
        now++
        try { step() } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Race $id stopped; its record is kept and finishes at the next restart", failure)
            views.keys.forEach { key -> plugin.server.getPlayer(key)?.let { CasinoUi.refuse(it, "진행 중 문제가 발생했습니다. 서버 재시작 시 정산됩니다.") } }
            reset()
        }
    }

    private fun reset() {
        id = UUID.randomUUID(); round = null; odds = null; settled = null; path = emptyList(); positions = List(RACE_SHEEP) { 0 }
    }

    private fun step() {
        if (settled != null) {
            if (now >= resultUntil) { reset(); drawAll() }
            return
        }
        val active = round ?: return
        when (active.phase) {
            RacePhase.BETTING -> {
                if (now >= deadline) {
                    val started = service.start(id)
                    round = started; startedAt = now
                    path = RacePolicy.path(started.winner, started.seed)
                    sound(Sound.ENTITY_SHEEP_AMBIENT, 1.4f)
                    drawAll()
                } else if (now % 20 == 0L) drawAll()
            }
            RacePhase.RUNNING -> {
                val elapsed = now - startedAt
                if (elapsed % FRAME_TICKS != 0L) return
                val frame = (elapsed / FRAME_TICKS).toInt() - 1
                if (frame in path.indices) { positions = path[frame]; sound(Sound.BLOCK_WOOL_STEP, 1f); drawAll() }
                if (frame >= path.lastIndex) finish()
            }
        }
    }

    private fun finish() {
        val settlement = service.finish(UUID.randomUUID(), id)
        settled = settlement
        resultUntil = now + RESULT_TICKS
        sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f)
        settlement.results.forEach { result ->
            val player = plugin.server.getPlayer(result.owner) ?: return@forEach
            player.sendMessage(if (result.payout > 0)
                Component.text("${names[settlement.winner]} 양 우승! ", NamedTextColor.GREEN).append(moneyDisplay(result.payout)).append(Component.text("원이 계좌에 입금되었습니다.", NamedTextColor.WHITE))
                else Component.text("${names[settlement.winner]} 양 우승. ", NamedTextColor.RED).append(moneyDisplay(result.staked)).append(Component.text("원을 잃었습니다.", NamedTextColor.RED)))
        }
        drawAll()
    }

    private fun sound(sound: Sound, pitch: Float) { views.keys.forEach { key -> plugin.server.getPlayer(key)?.let { it.playSound(it.location, sound, 0.6f, pitch) } } }
    private fun drawAll() { views.forEach { (key, holder) -> draw(key, holder) } }

    private fun draw(viewer: UUID, holder: MenuHolder) {
        val inventory = holder.inventory
        inventory.clear(); holder.actions.clear()
        val active = round
        val laneOdds = shownOdds()
        val canBet = settled == null && (active == null || active.phase == RacePhase.BETTING)
        val mine = active?.bets?.get(viewer).orEmpty()
        val winner = settled?.winner
        for (sheep in 0 until RACE_SHEEP) {
            val row = sheep * 9
            val multiplier = CasinoUi.multiplier(RacePolicy.multiplierCenti(laneOdds[sheep], settings.raceRtpPercent))
            val lore = mutableListOf(CasinoUi.text("우승 확률: ${laneOdds[sheep]}%"), CasinoUi.text("배당: ${multiplier}배", NamedTextColor.GREEN))
            mine[sheep]?.let { lore += CasinoUi.money("내 베팅: ", it) }
            if (canBet) {
                lore += CasinoUi.text("좌클릭: 베팅 금액만큼 걸기", NamedTextColor.GRAY)
                lore += CasinoUi.text("우클릭: 베팅 금액만큼 빼기", NamedTextColor.GRAY)
                holder.actions[row] = MenuAction.Casino(code, "sheep", sheep)
            }
            val color = if (winner == sheep) NamedTextColor.GOLD else NamedTextColor.WHITE
            inventory.setItem(row + RacePolicy.LENGTH, CasinoUi.item(Material.BLACK_CONCRETE, CasinoUi.text("결승선", NamedTextColor.YELLOW)))
            // The sheep itself is the lane's only item: it leaves the betting column when the race starts.
            inventory.setItem(row + positions[sheep], CasinoUi.item(wool[sheep], CasinoUi.text("${names[sheep]} 양" + if (winner == sheep) " 우승!" else "", color), lore))
        }
        val player = plugin.server.getPlayer(viewer)
        if (canBet && player != null) { inventory.setItem(8, CasinoUi.betItem(player)); holder.actions[8] = MenuAction.Casino(code, "amount") }
        if (active?.phase == RacePhase.BETTING) {
            val seconds = ((deadline - now).coerceAtLeast(0) + 19) / 20
            inventory.setItem(17, CasinoUi.item(Material.CLOCK, CasinoUi.text("출발까지 ${seconds}초", NamedTextColor.YELLOW), amount = seconds.toInt()))
        }
        if (mine.isNotEmpty()) inventory.setItem(26, CasinoUi.item(Material.GOLD_NUGGET, CasinoUi.money("내 베팅 합계: ", mine.values.sum())))
        CasinoUi.back(holder, 53)
    }

    private companion object {
        const val FRAME_TICKS = 6L
        const val RESULT_TICKS = 100L
    }
}
