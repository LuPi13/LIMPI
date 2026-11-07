package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.casino.CasinoSettings
import com.github.lupi13.limpi.casino.JackpotGame
import com.github.lupi13.limpi.casino.JackpotPolicy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.Plugin
import java.util.UUID

/** Tells the whole server about a jackpot. Call it after the pay has committed and any show has ended. */
object CasinoJackpot {
    fun message(name: String, game: JackpotGame, multiplierCenti: Long, amount: Component): Component =
        Component.text("${name}님이 ${game.label}에서 ${CasinoUi.multiplier(multiplierCenti)}배의 수익(", NamedTextColor.GREEN)
            .append(amount).append(Component.text("원)을 얻었습니다!", NamedTextColor.GREEN))

    /** By UUID, because a player who left may still win: a race ends without them and leaving a crash cashes out. */
    fun announce(plugin: Plugin, owner: UUID, game: JackpotGame, stake: Long, payout: Long, settings: CasinoSettings = CasinoUi.settings) {
        if (!JackpotPolicy.reached(game, stake, payout, settings)) return
        val name = plugin.server.getOfflinePlayer(owner).name ?: return
        plugin.server.broadcast(message(name, game, JackpotPolicy.multiplierCenti(stake, payout), moneyDisplay(payout)))
    }
}
