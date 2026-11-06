package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.casino.CasinoStale
import com.github.lupi13.limpi.casino.CrashPhase
import com.github.lupi13.limpi.casino.CrashPolicy
import com.github.lupi13.limpi.casino.CrashRound
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
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

/**
 * One round shared by everyone. Nothing runs until the first bet. The multiplier comes from the server's tick count, and
 * rows 0-4 draw it as a graph. Leaving the game takes the bet at the current multiplier; closing the window does not.
 */
class CrashScreen(private val plugin: Plugin) : CasinoScreen {
    override val code = "crash"
    private val views = linkedMapOf<UUID, MenuHolder>()
    private var id: UUID = UUID.randomUUID()
    private var round: CrashRound? = null
    private var now = 0L
    private var deadline = 0L
    private var startedAt = 0L
    private var crashedCenti: Long? = null
    private var resultUntil = 0L
    private var crashedAt = 0L

    private val settings get() = CasinoUi.settings
    private val service get() = FileManager.casino.crash
    private fun current() = CrashPolicy.multiplierCenti(now - startedAt, settings.crashGrowth)

    override fun open(player: Player) {
        val holder = CasinoUi.holder("크래시")
        views[player.uniqueId] = holder
        draw(player.uniqueId, holder)
        openMenu(plugin, player, holder.inventory)
    }

    override fun closed(player: Player, holder: MenuHolder) {
        if (views[player.uniqueId] === holder) views.remove(player.uniqueId)
    }

    override fun release(id: UUID) { views.remove(id) }

    /** Leaving counts as a click on the cash button at this tick. Past the frozen end it pays nothing, like a late click. */
    override fun quit(player: Player) {
        val running = round ?: return
        if (player.uniqueId !in running.uncashed || running.phase != CrashPhase.RUNNING) return
        try { cash(player.uniqueId, current()) } catch (_: CasinoStale) { } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Crash cash out for ${player.name} on leaving failed", failure)
        }
    }

    private fun cash(owner: UUID, multiplier: Long) {
        val result = service.cashOut(UUID.randomUUID(), owner, id, multiplier)
        round = service.round(id)
        plugin.server.getPlayer(owner)?.let { player ->
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f)
            player.sendMessage(Component.text("크래시 수령! (${CasinoUi.multiplier(result.multiplierCenti)}배) ", NamedTextColor.GREEN)
                .append(moneyDisplay(result.payout)).append(Component.text("원이 계좌에 입금되었습니다.", NamedTextColor.WHITE)))
        }
    }

    override fun click(player: Player, holder: MenuHolder, action: MenuAction.Casino, click: ClickType) {
        CasinoUi.guarded(plugin, player, { drawAll() }) {
            when (action.action) {
                "amount" -> CasinoUi.adjustBet(player, click)
                "place" -> {
                    if (crashedCenti != null) throw CasinoStale()
                    val first = round == null
                    round = service.bet(UUID.randomUUID(), player.uniqueId, id, CasinoUi.betAmount(player))
                    if (first) deadline = now + settings.crashBettingSeconds * 20L
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                }
                "cash" -> cash(player.uniqueId, current())
            }
        }
    }

    override fun tick() {
        now++
        try { step() } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Crash round $id stopped; its record is kept and refunds at the next restart", failure)
            views.keys.forEach { key -> plugin.server.getPlayer(key)?.let { CasinoUi.refuse(it, "진행 중 문제가 발생했습니다. 서버 재시작 시 환불됩니다.") } }
            reset()
        }
    }

    private fun reset() { round = null; crashedCenti = null; id = UUID.randomUUID() }

    private fun step() {
        if (crashedCenti != null) {
            if (now >= resultUntil) { reset(); drawAll() }
            return
        }
        val active = round ?: return
        when (active.phase) {
            CrashPhase.BETTING -> {
                if (countdownBeep(deadline - now)) CasinoUi.beep(views.keys, plugin)
                if (now >= deadline) {
                    round = service.start(id); startedAt = now
                    sound(Sound.BLOCK_NOTE_BLOCK_PLING)
                    // Left before the start: their bet comes back at 1.00, unless the round ends right there.
                    round!!.uncashed.keys.filter { plugin.server.getPlayer(it) == null }.forEach { owner -> try { cash(owner, 100) } catch (_: CasinoStale) { } }
                    drawAll()
                } else if (now % 20 == 0L) drawAll()
            }
            CrashPhase.RUNNING -> {
                val multiplier = current()
                if (multiplier >= active.crashCenti) { crash(active); return }
                active.uncashed.filter { (_, bet) -> CrashPolicy.payout(bet, multiplier, settings.maxPayout) >= settings.maxPayout }.keys.forEach { cash(it, multiplier) }
                if ((now - startedAt) % 4 == 0L) drawAll()
            }
        }
    }

    private fun crash(active: CrashRound) {
        val finished = service.finish(UUID.randomUUID(), id)
        round = finished; crashedCenti = active.crashCenti; crashedAt = now
        resultUntil = now + RESULT_TICKS
        sound(Sound.ENTITY_GENERIC_EXPLODE)
        finished.uncashed.forEach { (owner, bet) ->
            plugin.server.getPlayer(owner)?.sendMessage(Component.text("크래시! (${CasinoUi.multiplier(active.crashCenti)}배) ", NamedTextColor.RED)
                .append(moneyDisplay(bet)).append(Component.text("원을 잃었습니다.", NamedTextColor.RED)))
        }
        drawAll()
    }

    private fun sound(sound: Sound) { views.keys.forEach { key -> plugin.server.getPlayer(key)?.let { it.playSound(it.location, sound, 0.8f, 1f) } } }
    private fun drawAll() { views.forEach { (key, holder) -> draw(key, holder) } }

    private fun draw(viewer: UUID, holder: MenuHolder) {
        val inventory = holder.inventory
        inventory.clear(); holder.actions.clear()
        val active = round
        val crashed = crashedCenti
        val running = active != null && active.phase == CrashPhase.RUNNING && crashed == null
        val shown = crashed ?: if (running) current() else 100
        // Graph: columns 1-7 of the top five rows, colored by how far the multiplier has come.
        if (running || crashed != null) {
            val pane = when {
                crashed != null -> Material.RED_STAINED_GLASS_PANE
                shown >= 500 -> Material.ORANGE_STAINED_GLASS_PANE
                shown >= 200 -> Material.YELLOW_STAINED_GLASS_PANE
                else -> Material.LIME_STAINED_GLASS_PANE
            }
            CrashPolicy.graph((if (crashed != null) crashedAt else now) - startedAt, settings.crashGrowth).forEachIndexed { index, height ->
                for (row in 0..height) inventory.setItem((CrashPolicy.GRAPH_ROWS - 1 - row) * 9 + index + 1, CasinoUi.item(pane, Component.empty()))
            }
        }
        inventory.setItem(8, CasinoUi.item(if (crashed != null) Material.TNT else Material.FIREWORK_ROCKET,
            CasinoUi.text("${if (crashed != null) "크래시! " else ""}${CasinoUi.multiplier(shown)}배", if (crashed != null) NamedTextColor.RED else NamedTextColor.GOLD), players(active)))
        CasinoUi.back(holder, 53)
        val player = plugin.server.getPlayer(viewer) ?: return
        val mine = active?.bets?.get(viewer)
        when {
            crashed == null && (active == null || (active.phase == CrashPhase.BETTING && mine == null)) -> {
                inventory.setItem(47, CasinoUi.betItem(player)); holder.actions[47] = MenuAction.Casino(code, "amount")
                inventory.setItem(49, CasinoUi.item(Material.LIME_CONCRETE, CasinoUi.text("베팅", NamedTextColor.GREEN), listOf(CasinoUi.money("베팅: ", CasinoUi.betAmount(player)))))
                holder.actions[49] = MenuAction.Casino(code, "place")
            }
            running && mine != null && viewer !in active!!.cashed -> {
                inventory.setItem(49, CasinoUi.item(Material.GOLD_INGOT, CasinoUi.text("수령 (${CasinoUi.multiplier(shown)}배)", NamedTextColor.GOLD),
                    listOf(CasinoUi.money("수령액: ", CrashPolicy.payout(mine, shown, settings.maxPayout)))))
                holder.actions[49] = MenuAction.Casino(code, "cash")
            }
            mine != null -> inventory.setItem(49, CasinoUi.item(Material.GOLD_NUGGET, CasinoUi.money("베팅: ", mine)))
        }
        if (active?.phase == CrashPhase.BETTING) {
            val seconds = ((deadline - now).coerceAtLeast(0) + 19) / 20
            inventory.setItem(51, CasinoUi.item(Material.CLOCK, CasinoUi.text("시작까지 ${seconds}초", NamedTextColor.YELLOW), amount = seconds.toInt()))
        }
    }

    private fun players(active: CrashRound?): List<Component> = active?.bets?.map { (owner, bet) ->
        val name = plugin.server.getOfflinePlayer(owner).name ?: "?"
        val cashed = active.cashed[owner]
        CasinoUi.text("$name: ${bet}원" + if (cashed != null) " → ${CasinoUi.multiplier(cashed)}배 수령" else "", if (cashed != null) NamedTextColor.GREEN else NamedTextColor.WHITE)
    }.orEmpty()

    private companion object { const val RESULT_TICKS = 100L }
}
