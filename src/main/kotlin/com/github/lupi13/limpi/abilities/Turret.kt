package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.turret.IronTurretKind
import com.github.lupi13.limpi.turret.TurretKind
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

/** 포탑 설치: 철 주괴와 레드스톤으로 주변의 적을 쏘는 포탑을 세웁니다. 상위 능력은 [TurretAbility]에 종류를 더합니다. */
object Turret : TurretAbility(
    grade = Grade.RARE,
    displayName = Component.text("포탑 설치", NamedTextColor.WHITE),
    codeName = "turret",
    material = Material.DISPENSER,
    description = listOf(
        Component.text("철 주괴를 들고 블록을 우클릭하면", NamedTextColor.WHITE),
        Component.text("주변의 적을 공격하는 포탑을 설치합니다.", NamedTextColor.WHITE)
    )
) {
    override val kinds: List<TurretKind> = listOf(IronTurretKind)

    override val details: List<Component> by settingsCached {
        val stats = stats(IronTurretKind)
        fun white(text: String) = Component.text(text, NamedTextColor.WHITE)
        fun item(text: String) = Component.text(text, NamedTextColor.YELLOW)
        fun value(text: Any) = Component.text(text.toString(), NamedTextColor.GREEN)
        fun seconds(ticks: Int) = if (ticks % 20 == 0) "${ticks / 20}" else "${ticks / 20.0}"
        listOf(
            item("철 주괴").append(white("를 들고 블록을 우클릭하면, 손에 든 ")).append(item("철 주괴 ")).append(value(stats.ingotCost))
                .append(white("개와")),
            white("인벤토리의 ").append(item("레드스톤 ")).append(value(stats.redstoneCost)).append(white("개를 소모해 그 자리에 포탑을 설치합니다.")),
            white("포탑은 주변 ").append(value(IronTurretKind.range.toInt())).append(white("블록 안의 보이는 적에게 "))
                .append(value(seconds(stats.fireIntervalTicks))).append(white("초마다 투사체를 쏴 ")).append(value(stats.damage))
                .append(white("의 피해를 줍니다.")),
            white("포탑의 체력은 ").append(value(stats.maxHealth)).append(white("이며 1초마다 ")).append(value(stats.decayPerSecond))
                .append(white("씩 줄어듭니다. 회복 물약으로 회복할 수 있습니다.")),
            white("포탑에게 공격받은 공격적인 몹은 그 포탑이 사라질 때까지 포탑을 노립니다."),
            white("쿨타임: ").append(value(seconds(cooldownTicks()))).append(white("초"))
        )
    }
}
