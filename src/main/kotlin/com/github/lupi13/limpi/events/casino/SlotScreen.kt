package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.casino.SlotPolicy
import com.github.lupi13.limpi.casino.SlotSpin
import com.github.lupi13.limpi.casino.SlotSymbol
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
import kotlin.random.Random

/** The spin is already settled when the reels start, so closing the window only skips to the result. */
class SlotScreen(private val plugin: Plugin) : CasinoScreen {
    override val code = "slot"
    private class Animation(val holder: MenuHolder, val player: Player, val spin: SlotSpin) { var ticks = 0 }
    private val spinning = mutableMapOf<UUID, Animation>()
    private val last = mutableMapOf<UUID, List<SlotSymbol>>()

    private val reels = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32)

    private fun material(symbol: SlotSymbol) = when (symbol) {
        SlotSymbol.COAL -> Material.COAL; SlotSymbol.IRON -> Material.IRON_INGOT; SlotSymbol.GOLD -> Material.GOLD_INGOT
        SlotSymbol.EMERALD -> Material.EMERALD; SlotSymbol.DIAMOND -> Material.DIAMOND; SlotSymbol.NETHERITE -> Material.NETHERITE_INGOT
    }
    private fun label(symbol: SlotSymbol) = when (symbol) {
        SlotSymbol.COAL -> "석탄"; SlotSymbol.IRON -> "철"; SlotSymbol.GOLD -> "금"
        SlotSymbol.EMERALD -> "에메랄드"; SlotSymbol.DIAMOND -> "다이아몬드"; SlotSymbol.NETHERITE -> "네더라이트"
    }
    private fun cell(symbol: SlotSymbol) = CasinoUi.item(material(symbol), CasinoUi.text(label(symbol)))

    override fun open(player: Player) {
        val holder = CasinoUi.holder("슬롯머신")
        draw(player, holder)
        openMenu(plugin, player, holder.inventory)
    }

    private fun draw(player: Player, holder: MenuHolder) {
        val inventory = holder.inventory
        inventory.clear(); holder.actions.clear()
        val shown = last[player.uniqueId]
        reels.forEachIndexed { index, slot -> inventory.setItem(slot, shown?.let { cell(it[index]) } ?: CasinoUi.item(Material.GRAY_STAINED_GLASS_PANE, CasinoUi.text("?", NamedTextColor.GRAY))) }
        inventory.setItem(48, CasinoUi.betItem(player)); holder.actions[48] = MenuAction.Casino(code, "bet")
        inventory.setItem(49, CasinoUi.item(Material.LEVER, CasinoUi.text("돌리기", NamedTextColor.GREEN), listOf(CasinoUi.money("베팅: ", CasinoUi.betAmount(player)))))
        holder.actions[49] = MenuAction.Casino(code, "spin")
        inventory.setItem(50, CasinoUi.item(Material.BOOK, CasinoUi.text("배당표", NamedTextColor.YELLOW), payTable()))
        CasinoUi.back(holder, 53)
    }

    private fun payTable(): List<Component> {
        val lines = CasinoUi.settings.slotSymbols.map {
            CasinoUi.text("${label(it.symbol)}: 3개 ${SlotPolicy.betMultiple(it.triple)}배 / 2개 ${SlotPolicy.betMultiple(it.pair)}배")
        }
        return listOf(CasinoUi.text("베팅액에 곱할 배수입니다.", NamedTextColor.GRAY)) + lines + listOf(
            CasinoUi.text("가로 3줄과 대각선 2줄을 각각 계산해 모두 더합니다.", NamedTextColor.GRAY),
            CasinoUi.text("2개는 줄의 첫 칸부터 연속으로 같을 때입니다.", NamedTextColor.GRAY))
    }

    override fun click(player: Player, holder: MenuHolder, action: MenuAction.Casino, click: ClickType) {
        if (player.uniqueId in spinning) return
        var started = false
        CasinoUi.guarded(plugin, player, { if (!started) draw(player, holder) }) {
            when (action.action) {
                "bet" -> CasinoUi.adjustBet(player, click)
                "spin" -> {
                    val spin = FileManager.casino.slot.spin(UUID.randomUUID(), player.uniqueId, CasinoUi.betAmount(player))
                    last[player.uniqueId] = spin.grid
                    spinning[player.uniqueId] = Animation(holder, player, spin)
                    started = true
                }
            }
        }
    }

    override fun tick() {
        val iterator = spinning.values.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            animation.ticks++
            if (animation.ticks % 2 != 0) continue
            val stopped = when { animation.ticks >= STOP * 3 -> 3; animation.ticks >= STOP * 2 -> 2; animation.ticks >= STOP -> 1; else -> 0 }
            reels.forEachIndexed { index, slot ->
                val column = index % 3
                animation.holder.inventory.setItem(slot, cell(if (column < stopped) animation.spin.grid[index] else SlotSymbol.entries[Random.nextInt(SlotSymbol.entries.size)]))
            }
            val player = animation.player
            if (animation.ticks == STOP || animation.ticks == STOP * 2) player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 1f)
            else player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.4f, 2f)
            if (stopped == 3) {
                iterator.remove()
                announce(player, animation.spin)
                draw(player, animation.holder)
            }
        }
    }

    private fun announce(player: Player, spin: SlotSpin) {
        if (spin.payout == 0L) { player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f); return }
        val big = spin.payout >= spin.bet * 10
        player.playSound(player.location, if (big) Sound.UI_TOAST_CHALLENGE_COMPLETE else Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f)
        player.sendMessage(Component.text("슬롯 당첨! ", NamedTextColor.GREEN).append(moneyDisplay(spin.payout)).append(Component.text("원이 계좌에 입금되었습니다.", NamedTextColor.WHITE)))
    }

    /** Skipping the animation still tells the result, already in the account. */
    override fun closed(player: Player, holder: MenuHolder) {
        val animation = spinning[player.uniqueId] ?: return
        if (animation.holder !== holder) return
        spinning.remove(player.uniqueId)
        announce(player, animation.spin)
    }

    override fun release(id: UUID) { spinning.remove(id); last.remove(id) }

    private companion object { const val STOP = 16 }
}
