package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.casino.CasinoDamaged
import com.github.lupi13.limpi.casino.MinesOutcome
import com.github.lupi13.limpi.casino.MinesPolicy
import com.github.lupi13.limpi.casino.MinesRound
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

/** A window five rows high: left panel and a 5x5 board. The stored round is the truth; only the last finished board is kept in memory, to show it. */
class MinesScreen(private val plugin: Plugin) : CasinoScreen {
    override val code = "mines"
    private val mineCounts = mutableMapOf<UUID, Int>()
    private val finished = mutableMapOf<UUID, Finished>()
    private data class Finished(val round: MinesRound, val cell: Int?)

    private fun slot(cell: Int) = (cell / 5) * 9 + cell % 5 + 2
    private fun count(player: Player) = mineCounts[player.uniqueId] ?: 3

    override fun open(player: Player) {
        val holder = CasinoUi.holder("마인즈", 45)
        draw(player, holder)
        openMenu(plugin, player, holder.inventory)
    }

    private fun draw(player: Player, holder: MenuHolder) {
        val inventory = holder.inventory
        inventory.clear(); holder.actions.clear()
        val round = try { FileManager.casino.mines.current(player.uniqueId) } catch (failure: CasinoDamaged) {
            plugin.logger.warning("Casino record ${failure.key} cannot be read; it was kept (see /limpi review)")
            CasinoUi.back(holder, 44)
            inventory.setItem(22, CasinoUi.item(Material.BARRIER, CasinoUi.text("이 게임의 진행 기록에 문제가 있습니다.", NamedTextColor.RED), listOf(CasinoUi.text("관리자에게 확인을 요청하세요."))))
            return
        }
        CasinoUi.back(holder, 44)
        if (round == null) drawIdle(player, holder) else drawRound(holder, round)
    }

    private fun drawIdle(player: Player, holder: MenuHolder) {
        val inventory = holder.inventory
        val last = finished[player.uniqueId]
        for (cell in 0 until MinesPolicy.CELLS) inventory.setItem(slot(cell), when {
            last == null -> CasinoUi.item(Material.GRAY_STAINED_GLASS_PANE, CasinoUi.text("?", NamedTextColor.GRAY))
            cell == last.cell -> CasinoUi.item(Material.TNT, CasinoUi.text("밟은 지뢰!", NamedTextColor.RED))
            cell in last.round.mines -> CasinoUi.item(Material.COAL_BLOCK, CasinoUi.text("지뢰", NamedTextColor.RED))
            cell in last.round.revealed -> CasinoUi.item(Material.EMERALD, CasinoUi.text("안전", NamedTextColor.GREEN))
            else -> CasinoUi.item(Material.GRAY_STAINED_GLASS_PANE, CasinoUi.text("안전", NamedTextColor.GRAY))
        })
        inventory.setItem(9, CasinoUi.betItem(player)); holder.actions[9] = MenuAction.Casino(code, "bet")
        val mines = count(player)
        inventory.setItem(18, CasinoUi.item(Material.TNT, CasinoUi.text("지뢰 ${mines}개", NamedTextColor.RED), listOf(
            CasinoUi.text("첫 칸 배율: ${CasinoUi.multiplier(MinesPolicy.multiplierCenti(mines, 1, CasinoUi.settings.minesRtpPercent))}배"),
            CasinoUi.text("좌클릭: +1 / 우클릭: -1 / Shift: 5개씩", NamedTextColor.GRAY)), mines))
        holder.actions[18] = MenuAction.Casino(code, "count")
        inventory.setItem(27, CasinoUi.item(Material.LIME_CONCRETE, CasinoUi.text("시작", NamedTextColor.GREEN), listOf(CasinoUi.money("베팅: ", CasinoUi.betAmount(player)))))
        holder.actions[27] = MenuAction.Casino(code, "start")
    }

    private fun drawRound(holder: MenuHolder, round: MinesRound) {
        val inventory = holder.inventory
        for (cell in 0 until MinesPolicy.CELLS) {
            if (cell in round.revealed) inventory.setItem(slot(cell), CasinoUi.item(Material.EMERALD, CasinoUi.text("안전", NamedTextColor.GREEN)))
            else {
                inventory.setItem(slot(cell), CasinoUi.item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, CasinoUi.text("클릭하여 열기", NamedTextColor.YELLOW)))
                holder.actions[slot(cell)] = MenuAction.Casino(code, "reveal", cell, round.version)
            }
        }
        val rtp = CasinoUi.settings.minesRtpPercent
        val current = FileManager.casino.mines.payout(round)
        inventory.setItem(9, CasinoUi.item(Material.GOLD_NUGGET, CasinoUi.money("베팅: ", round.bet)))
        inventory.setItem(18, CasinoUi.item(Material.TNT, CasinoUi.text("지뢰 ${round.mines.size}개", NamedTextColor.RED), amount = round.mines.size))
        val lore = mutableListOf(CasinoUi.money("수령액: ", current))
        if (round.safeLeft > 0) lore += CasinoUi.text("다음 칸 배율: ${CasinoUi.multiplier(MinesPolicy.multiplierCenti(round.mines.size, round.revealed.size + 1, rtp))}배", NamedTextColor.GRAY)
        if (round.revealed.isEmpty()) lore += CasinoUi.text("칸을 하나 이상 열어야 수령할 수 있습니다.", NamedTextColor.GRAY)
        inventory.setItem(27, CasinoUi.item(Material.GOLD_INGOT, CasinoUi.text("수령 (${CasinoUi.multiplier(MinesPolicy.multiplierCenti(round.mines.size, round.revealed.size, rtp))}배)", NamedTextColor.GOLD), lore))
        holder.actions[27] = MenuAction.Casino(code, "cash", 0, round.version)
    }

    override fun click(player: Player, holder: MenuHolder, action: MenuAction.Casino, click: ClickType) {
        CasinoUi.guarded(plugin, player, { draw(player, holder) }) {
            val service = FileManager.casino.mines
            when (action.action) {
                "bet" -> CasinoUi.adjustBet(player, click)
                "count" -> {
                    val step = if (click.isShiftClick) 5 else 1
                    val next = count(player) + if (click.isLeftClick) step else -step
                    mineCounts[player.uniqueId] = next.coerceIn(MinesPolicy.MINE_COUNTS.first, MinesPolicy.MINE_COUNTS.last)
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.5f)
                }
                "start" -> {
                    service.start(UUID.randomUUID(), player.uniqueId, CasinoUi.betAmount(player), count(player))
                    finished.remove(player.uniqueId)
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                }
                "reveal" -> show(player, service.reveal(UUID.randomUUID(), player.uniqueId, action.argument, action.version))
                "cash" -> show(player, service.cashOut(UUID.randomUUID(), player.uniqueId, action.version))
            }
        }
    }

    private fun show(player: Player, outcome: MinesOutcome) {
        when (outcome) {
            is MinesOutcome.Safe -> player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f + outcome.round.revealed.size * 0.05f)
            is MinesOutcome.Exploded -> {
                finished[player.uniqueId] = Finished(outcome.round, outcome.cell)
                player.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1f)
                player.sendMessage(Component.text("지뢰를 밟았습니다! ", NamedTextColor.RED).append(moneyDisplay(outcome.round.bet)).append(Component.text("원을 잃었습니다.", NamedTextColor.RED)))
            }
            is MinesOutcome.Cashed -> {
                finished[player.uniqueId] = Finished(outcome.round, null)
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f)
                player.sendMessage(Component.text("마인즈 수령! ", NamedTextColor.GREEN).append(moneyDisplay(outcome.payout)).append(Component.text("원이 계좌에 입금되었습니다.", NamedTextColor.WHITE)))
            }
        }
    }

    override fun release(id: UUID) { mineCounts.remove(id); finished.remove(id) }
}
