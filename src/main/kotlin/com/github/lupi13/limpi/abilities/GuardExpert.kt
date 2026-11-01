package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.MachineVsZombie
import com.github.lupi13.limpi.turret.CopperTurretKind
import com.github.lupi13.limpi.turret.GoldTurretKind
import com.github.lupi13.limpi.turret.IronTurretKind
import com.github.lupi13.limpi.turret.TurretKind
import com.github.lupi13.limpi.turret.TurretStats
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

/**
 * 경비 전문가: 포탑 설치의 상위 능력(퀘스트 [MachineVsZombie]). 철 포탑에 더해 구리 포탑(근접 범위 화염)과
 * 금 포탑(맞닿은 포탑 공격 속도 증가)을 세웁니다. 설정은 종류 id 접두사(`iron_`, `copper_`, `gold_`)로 나뉩니다.
 */
object GuardExpert : TurretAbility(
    grade = Grade.EPIC,
    displayName = Component.text("경비 전문가", NamedTextColor.GRAY),
    codeName = "guard_expert",
    material = Material.DROPPER,
    description = listOf(
        Component.text("철·구리·금 주괴를 들고 블록을 우클릭하면", NamedTextColor.WHITE),
        Component.text("서로 다른 역할의 포탑을 설치합니다.", NamedTextColor.WHITE)
    ),
    acquisition = AcquisitionType.QUEST
) {
    override val kinds: List<TurretKind> = listOf(IronTurretKind, CopperTurretKind, GoldTurretKind)

    override val relatedQuest: List<com.github.lupi13.limpi.quests.Quest> by lazy { listOf(com.github.lupi13.limpi.quests.TowerDefense) }

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(MachineVsZombie.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by settingsCached {
        val iron = stats(IronTurretKind); val copper = stats(CopperTurretKind); val gold = stats(GoldTurretKind)
        fun white(text: String) = Component.text(text, NamedTextColor.WHITE)
        fun item(text: String) = Component.text(text, NamedTextColor.YELLOW)
        fun value(number: Number): Component {
            val text = if (number is Double && number % 1.0 == 0.0) number.toInt().toString() else number.toString()
            return Component.text(text, NamedTextColor.GREEN)
        }
        fun seconds(ticks: Int) = value(if (ticks % 20 == 0) ticks / 20 else ticks / 20.0)
        fun cost(kind: TurretKind, stats: TurretStats) = white(" (").append(item(kind.itemName + " ")).append(value(stats.ingotCost))
            .append(white("개, ")).append(item("레드스톤 ")).append(value(stats.redstoneCost)).append(white("개)"))
        fun life(stats: TurretStats) = white("체력 ").append(value(stats.maxHealth)).append(white(", 1초마다 "))
            .append(value(stats.decayPerSecond)).append(white(" 감소."))
        listOf(
            Component.text("포탑 설치", NamedTextColor.GRAY).append(white(" 능력을 계승합니다.")),
            item("주괴").append(white("를 들고 블록을 우클릭하면, 그 주괴와 인벤토리의 ")).append(item("레드스톤"))
                .append(white("을 소모해 포탑을 설치합니다.")),
            Component.text("철 포탑", NamedTextColor.GRAY).append(cost(IronTurretKind, iron)).append(white(": 주변 ")).append(value(iron.range))
                .append(white("블록 안의 보이는 적에게 ")).append(seconds(iron.fireIntervalTicks)).append(white("초마다 투사체를 쏴 "))
                .append(value(iron.damage)).append(white("의 피해를 줍니다. ")).append(life(iron)),
            Component.text("구리 포탑", NamedTextColor.GOLD).append(cost(CopperTurretKind, copper)).append(white(": 주변 "))
                .append(value(copper.range)).append(white("블록 안에 적이 다가오면 ")).append(seconds(copper.fireIntervalTicks))
                .append(white("초마다 범위 안의 모든 적에게 ")).append(value(copper.damage)).append(white("의 피해를 주고 "))
                .append(value(copper.extra(CopperTurretKind.BURN))).append(white("초간 불을 붙입니다. ")).append(life(copper))
                .append(white(" 방어력 ")).append(value(copper.extra(CopperTurretKind.ARMOR))).append(white(".")),
            Component.text("금 포탑", NamedTextColor.YELLOW).append(cost(GoldTurretKind, gold)).append(white(": 공격하지 않고, 맞닿은 면의 포탑의 공격 속도를 "))
                .append(value((gold.extra(GoldTurretKind.SPEED) * 100).let { if (it % 1.0 == 0.0) it.toInt() else it }))
                .append(white("% 올립니다. 여러 금 포탑에 맞닿으면 효과가 더해집니다. ")).append(life(gold)),
            white("포탑은 회복 물약으로 회복할 수 있습니다."),
            white("포탑은 설치하고 공격 간격만큼 지난 뒤부터 공격합니다."),
            white("포탑에게 공격받은 공격적인 몹은 그 포탑이 사라질 때까지 포탑을 노립니다."),
            white("쿨타임: ").append(seconds(cooldownTicks())).append(white("초"))
        )
    }
}
