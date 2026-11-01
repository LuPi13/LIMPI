package com.github.lupi13.limpi.ability

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 완벽한 재장전의 게이지 판정·표시와 석궁 장전 시간 계산(Bukkit 없이 시험).
 *
 * 게이지는 [length]칸이고 사격 뒤 1틱마다 한 칸씩 채워집니다. 가운데 [perfect]칸이 성공 구간이며,
 * 장전을 시작한 순간 마지막으로 채워진 칸이 그 구간에 있으면 성공입니다.
 */
object PerfectReloadPolicy {
    /** 바닐라 석궁 장전 시간: 1.25초에서 빠른 장전 단계마다 0.25초 빠름(0 미만 없음). */
    const val BASE_CHARGE_TICKS = 25
    const val TICKS_PER_QUICK_CHARGE = 5

    enum class State { RUNNING, PERFECT, MISSED }

    /** 성공 구간(0부터 센 칸 번호)의 시작. 성공 칸 수는 게이지 길이를 넘지 않습니다. */
    fun windowStart(length: Int, perfect: Int): Int {
        require(length > 0 && perfect > 0)
        return (length - perfect.coerceAtMost(length)) / 2
    }

    fun inWindow(length: Int, perfect: Int, index: Int): Boolean {
        val start = windowStart(length, perfect)
        return index >= start && index < start + perfect.coerceAtMost(length)
    }

    /** 채워진 칸이 [shown]개일 때 장전을 시작하면 성공인지(마지막 채워진 칸이 성공 구간). */
    fun perfect(length: Int, perfect: Int, shown: Int): Boolean = shown in 1..length && inWindow(length, perfect, shown - 1)

    /** 칸마다의 색: 진행 중이면 채워진 칸 노랑(성공 구간은 초록)·빈 칸 회색, 성공이면 전부 하늘색, 실패면 전부 빨강. */
    fun colors(length: Int, perfect: Int, shown: Int, state: State): List<NamedTextColor> = List(length) { index ->
        when (state) {
            State.PERFECT -> NamedTextColor.AQUA
            State.MISSED -> NamedTextColor.RED
            State.RUNNING -> when {
                index >= shown -> NamedTextColor.GRAY
                inWindow(length, perfect, index) -> NamedTextColor.GREEN
                else -> NamedTextColor.YELLOW
            }
        }
    }

    /** 액션바 문구: 흰색 능력 이름 뒤에 게이지. 같은 색이 이어지면 한 조각으로 묶습니다. */
    fun render(title: Component, length: Int, perfect: Int, shown: Int, state: State): Component {
        var text = title.append(Component.text(" "))
        val colors = colors(length, perfect, shown, state)
        var from = 0
        while (from < colors.size) {
            var to = from
            while (to < colors.size && colors[to] == colors[from]) to++
            text = text.append(Component.text("|".repeat(to - from), colors[from]))
            from = to
        }
        return text
    }

    /** 빠른 장전 [level]단계 석궁의 장전 시간(틱). */
    fun chargeTicks(level: Int): Int =
        (BASE_CHARGE_TICKS.toLong() - TICKS_PER_QUICK_CHARGE.toLong() * level.coerceAtLeast(0)).coerceAtLeast(0).toInt()

    /**
     * 빠른 장전이 [bonus]단계 더 붙은 것처럼 장전할 때, 실제로 [elapsed]틱 지난 시점에 바닐라 석궁이 셀 사용 시간.
     * 바닐라는 사용 시간 / 원래 장전 시간으로 장전 정도를 정하므로 그 비율을 맞추면 장전 소리·완료가 같은 비율로 당겨집니다.
     * 원래 장전 시간을 넘기지 않습니다(그때 이미 장전됨).
     */
    fun boostedUsedTicks(elapsed: Int, level: Int, bonus: Int): Int {
        val normal = chargeTicks(level)
        val boosted = chargeTicks((level.toLong() + bonus.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val time = elapsed.coerceAtLeast(0)
        if (normal == 0 || boosted == normal) return time
        if (boosted == 0) return normal
        return Math.ceilDiv(time.toLong() * normal, boosted.toLong()).coerceAtMost(normal.toLong()).toInt()
    }
}
