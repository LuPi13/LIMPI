package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.casino.BetSelector
import com.github.lupi13.limpi.casino.CasinoDamaged
import com.github.lupi13.limpi.casino.CasinoSettings
import com.github.lupi13.limpi.casino.CasinoStale
import com.github.lupi13.limpi.casino.CasinoUnavailable
import com.github.lupi13.limpi.commands.EconomyText
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.menu.MenuAction
import com.github.lupi13.limpi.menu.MenuHolder
import com.github.lupi13.limpi.menu.MenuKind
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

/** What every casino screen shares: items, the bet control and the handling of a refused click. */
object CasinoUi {
    private val bets = mutableMapOf<UUID, Long>()
    val settings: CasinoSettings get() = FileManager.casino.settings

    fun title(name: String): Component = Component.text("LIMPI: $name", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
    fun holder(name: String, size: Int = 54) = MenuHolder(MenuKind.CASINO, 0, title(name), size)

    fun text(value: String, color: NamedTextColor = NamedTextColor.WHITE): Component = Component.text(value, color).decoration(TextDecoration.ITALIC, false)
    fun money(prefix: String, amount: Long, suffix: String = "원"): Component = text(prefix)
        .append(moneyDisplay(amount).decoration(TextDecoration.ITALIC, false)).append(text(suffix))
    fun balance(id: UUID): Component = text("현재 보유 금액: ").append(EconomyText.balanceDisplay(id).decoration(TextDecoration.ITALIC, false)).append(text("원"))

    fun item(material: Material, name: Component, lore: List<Component> = emptyList(), amount: Int = 1): ItemStack = ItemStack(material, amount.coerceIn(1, 99)).apply {
        val meta = itemMeta
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))
        if (lore.isNotEmpty()) meta.lore(lore)
        itemMeta = meta
    }
    fun filler(material: Material = Material.BLACK_STAINED_GLASS_PANE) = item(material, Component.empty())

    fun multiplier(centi: Long) = "${centi / 100}.${(centi % 100).toString().padStart(2, '0')}"

    fun betAmount(player: Player): Long = (bets[player.uniqueId] ?: settings.minBet).coerceIn(settings.minBet, settings.maxBet)

    fun betItem(player: Player): ItemStack = item(Material.GOLD_NUGGET, money("베팅 금액: ", betAmount(player)), listOf(
        balance(player.uniqueId), text("좌클릭: 올리기 / 우클릭: 내리기", NamedTextColor.GRAY), text("Shift+좌클릭: 10배 / Shift+우클릭: 1/10", NamedTextColor.GRAY)))

    /** True when the click changed the amount. */
    fun adjustBet(player: Player, click: ClickType): Boolean {
        val current = betAmount(player)
        val next = when (click) {
            ClickType.LEFT -> BetSelector.up(current, settings)
            ClickType.RIGHT -> BetSelector.down(current, settings)
            ClickType.SHIFT_LEFT -> BetSelector.tenTimes(current, settings)
            ClickType.SHIFT_RIGHT -> BetSelector.tenth(current, settings)
            else -> return false
        }
        bets[player.uniqueId] = next
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.5f)
        return true
    }

    fun back(holder: MenuHolder, slot: Int) {
        holder.inventory.setItem(slot, item(Material.DARK_OAK_DOOR, text("카지노 메뉴로", NamedTextColor.YELLOW)))
        holder.actions[slot] = MenuAction.Casino("menu", "open")
    }

    fun release(id: UUID) { bets.remove(id) }

    /** Runs a click. Whatever is refused was not committed, so [redraw] always shows the truth. */
    fun guarded(plugin: Plugin, player: Player, redraw: () -> Unit, action: () -> Unit) {
        try {
            action()
        } catch (_: CasinoStale) {
            // A double click or a click on an old frame: nothing to report.
        } catch (failure: CasinoUnavailable) {
            refuse(player, failure.message ?: "지금은 할 수 없습니다.")
        } catch (failure: BankException) {
            EconomyText.bankFailure(player, failure); EconomyText.deny(player)
        } catch (failure: CasinoDamaged) {
            plugin.logger.log(Level.SEVERE, "Casino record ${failure.key} cannot be read; it was kept (see /limpi review)", failure)
            refuse(player, "이 게임의 진행 기록에 문제가 있습니다. 관리자에게 확인을 요청하세요.")
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Casino action by ${player.name} failed", failure)
            refuse(player, "처리 중 오류가 발생했습니다. 잠시 후 다시 시도하세요.")
        }
        redraw()
    }

    fun refuse(player: Player, message: String) {
        player.sendMessage(Component.text(message, NamedTextColor.RED)); EconomyText.deny(player)
    }
}
