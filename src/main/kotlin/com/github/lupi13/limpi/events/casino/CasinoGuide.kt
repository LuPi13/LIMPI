package com.github.lupi13.limpi.events.casino

import com.github.lupi13.limpi.casino.BlackjackPolicy
import com.github.lupi13.limpi.casino.CasinoSettings
import com.github.lupi13.limpi.casino.MinesPolicy
import com.github.lupi13.limpi.casino.RacePolicy
import com.github.lupi13.limpi.casino.SlotPolicy
import com.github.lupi13.limpi.casino.SlotSymbol
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * How to play each game, sent to the chat on a right click in the casino menu. Every number that can be configured comes
 * from [CasinoSettings], so the text cannot drift from the rules. In a line, {x} is a number (green) and <x> a button or
 * item (yellow).
 */
object CasinoGuide {
    private val MARKUP = Regex("""\{([^}]*)}|<([^>]*)>""")

    fun lines(code: String, name: String, settings: CasinoSettings): List<Component> {
        val body = when (code) {
            "mines" -> mines(settings)
            "slot" -> slot(settings)
            "blackjack" -> blackjack(settings)
            "crash" -> crash(settings)
            "race" -> race(settings)
            else -> throw IllegalArgumentException("Unknown casino game $code")
        }
        return listOf(Component.text("[ $name ]", NamedTextColor.GOLD)) + body +
            line("베팅 {${money(settings.minBet)}}~{${money(settings.maxBet)}}원 / 한 판 최대 지급 {${money(settings.maxPayout)}}원")
    }

    private fun mines(settings: CasinoSettings): List<Component> {
        val firstCell = { mines: Int -> "{${CasinoUi.multiplier(MinesPolicy.multiplierCenti(mines, 1, settings.minesRtpPercent))}}배" }
        val fewest = MinesPolicy.MINE_COUNTS.first
        val most = MinesPolicy.MINE_COUNTS.last
        return listOf(
            line("베팅 금액과 지뢰 수({$fewest}~{$most}개)를 고르고 <시작>을 누릅니다."),
            line("5x5 칸을 하나씩 엽니다. 안전한 칸이면 배율이 오릅니다."),
            line("<수령>을 누르면 베팅액 x 현재 배율을 받습니다. (1칸 이상 열어야 함)"),
            line("지뢰를 밟으면 베팅액을 모두 잃습니다."),
            line("지뢰가 많을수록 배율이 높습니다. (첫 칸: 지뢰 ${fewest}개 ${firstCell(fewest)}, 3개 ${firstCell(3)}, ${most}개 ${firstCell(most)})"),
            line("안전한 칸을 모두 열거나 최대 지급액에 닿으면 자동으로 수령합니다."),
            line("창을 닫거나 접속을 끊어도 판은 유지되어 나중에 이어 할 수 있습니다."),
        )
    }

    private fun slot(settings: CasinoSettings): List<Component> {
        val coal = settings.slotSymbols.first { it.symbol == SlotSymbol.COAL }
        val netherite = settings.slotSymbols.first { it.symbol == SlotSymbol.NETHERITE }
        return listOf(
            line("베팅 금액을 고르고 <돌리기>를 누릅니다."),
            line("가로 3줄과 대각선 2줄, 총 5줄을 각각 계산해 모두 더합니다."),
            line("한 줄에 같은 광물 3개, 또는 왼쪽부터 연속 2개면 당첨입니다."),
            line("3개 배수: 석탄 {${SlotPolicy.betMultiple(coal.triple)}}배 ~ 네더라이트 {${SlotPolicy.betMultiple(netherite.triple)}}배"),
            line("2개 배수: 석탄 {${SlotPolicy.betMultiple(coal.pair)}}배 ~ 네더라이트 {${SlotPolicy.betMultiple(netherite.pair)}}배"),
            line("2개 당첨은 베팅액보다 적을 수 있습니다. 전체 배수는 <배당표>에서 확인하세요."),
            line("창을 닫아도 결과는 그대로 입금됩니다."),
        )
    }

    private fun blackjack(settings: CasinoSettings) = listOf(
        line("최대 {${BlackjackPolicy.SEATS}}명이 각자 딜러와 승부합니다. 합계가 21에 가까운 쪽이 이깁니다."),
        line("A는 1 또는 11, J·Q·K는 10입니다. 21을 넘으면 버스트로 바로 패배합니다."),
        line("<히트>: 한 장 더 받기 / <스탠드>: 멈추기"),
        line("<더블>: 처음 두 장일 때만, 베팅을 2배로 올리고 한 장만 더 받기"),
        line("딜러는 합계 {17} 이상이 될 때까지 카드를 받습니다."),
        line("승리 시 베팅액의 {2}배, 처음 두 장이 21인 블랙잭은 {2.5}배, 무승부는 베팅액을 돌려받습니다."),
        warning("주의: 딜러 블랙잭은 모든 차례가 끝난 뒤 공개됩니다. 내 블랙잭은 무승부, 나머지는 더블한 금액까지 모두 잃습니다."),
        line("창을 연 사람이 모두 베팅하면 바로 시작하고, 아니면 첫 베팅 후 {${settings.blackjackBettingSeconds}}초에 시작합니다."),
        line("차례마다 {${settings.blackjackTurnSeconds}}초 안에 고르지 않으면 자동으로 스탠드합니다."),
    )

    private fun crash(settings: CasinoSettings): List<Component> {
        val doubling = (ln(2.0) / settings.crashGrowth).roundToLong().coerceAtLeast(1)
        val instant = 100 - settings.crashRtpPercent
        return listOfNotNull(
            line("첫 베팅 후 {${settings.crashBettingSeconds}}초가 지나면 모두 함께 시작합니다. 베팅은 한 판에 한 번입니다."),
            line("배율은 {1.00}배부터 점점 빠르게 오릅니다. (약 {$doubling}초에 2배)"),
            line("터지기 전에 <수령>을 누르면 베팅액 x 현재 배율을 받고, 터지면 모두 잃습니다."),
            if (instant > 0) line("터지는 지점은 무작위이며, 약 {$instant}%는 시작하자마자 터집니다.") else line("터지는 지점은 무작위입니다."),
            warning("주의: 창을 닫아도 수령되지 않습니다! 다시 들어와서 수령하세요."),
            line("접속을 끊으면 그 순간의 배율로 수령합니다. 최대 지급액에 닿아도 자동으로 수령합니다."),
        )
    }

    private fun race(settings: CasinoSettings): List<Component> {
        val likely = settings.raceOddsPercent.max()
        val unlikely = settings.raceOddsPercent.min()
        val pays = { odds: Int -> CasinoUi.multiplier(RacePolicy.multiplierCenti(odds, settings.raceRtpPercent)) }
        return listOf(
            line("양 6마리의 우승 확률은 경주마다 새로 정해지며, 양 아이콘에서 확인할 수 있습니다."),
            line("양을 좌클릭하면 베팅 금액만큼 걸고, 우클릭하면 같은 금액만큼 뺍니다."),
            line("여러 양에 나눠 걸 수 있고, 한 경주에 합계 {${money(settings.maxBet)}}원까지 걸 수 있습니다."),
            line("첫 베팅 후 {${settings.raceBettingSeconds}}초가 지나면 출발하며, 그 뒤에는 걸거나 뺄 수 없습니다."),
            line("우승 양에 건 금액 x 배당을 받습니다. (확률 {$likely}%면 {${pays(likely)}}배, {$unlikely}%면 {${pays(unlikely)}}배)"),
            line("다른 양에 건 금액은 잃습니다. 창을 닫거나 접속을 끊어도 경주가 끝나면 정산됩니다."),
        )
    }

    private fun money(amount: Long) = String.format(Locale.ROOT, "%,d", amount)

    private fun warning(text: String): Component = Component.text(text, NamedTextColor.RED)

    private fun line(text: String): Component {
        var result: Component = Component.empty().color(NamedTextColor.WHITE)
        var from = 0
        for (match in MARKUP.findAll(text)) {
            if (match.range.first > from) result = result.append(Component.text(text.substring(from, match.range.first)))
            val number = match.groups[1]?.value
            result = result.append(if (number != null) Component.text(number, NamedTextColor.GREEN) else Component.text(match.groupValues[2], NamedTextColor.YELLOW))
            from = match.range.last + 1
        }
        if (from < text.length) result = result.append(Component.text(text.substring(from)))
        return result
    }
}
