package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.config.specs.TurretSpecs
import com.github.lupi13.limpi.quests.TowerDefense
import com.github.lupi13.limpi.turret.AmethystTurretKind
import com.github.lupi13.limpi.turret.CopperTurretKind
import com.github.lupi13.limpi.turret.DiamondTurretKind
import com.github.lupi13.limpi.turret.EchoTurretKind
import com.github.lupi13.limpi.turret.EmeraldTurretKind
import com.github.lupi13.limpi.turret.GoldTurretKind
import com.github.lupi13.limpi.turret.IronTurretKind
import com.github.lupi13.limpi.turret.ShulkerTurretKind
import com.github.lupi13.limpi.turret.TurretKind
import com.github.lupi13.limpi.turret.TurretStats
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

/**
 * 경비 탑 챔피언: 경비 전문가의 상위 능력(퀘스트 [TowerDefense]). 철·구리·금에 다이아몬드·메아리·자수정·셜커·에메랄드 포탑을 더합니다.
 * 설정은 종류 id 접두사로 나뉩니다(`turret_champion.yml`).
 */
object TurretChampion : TurretAbility(
    grade = Grade.LEGENDARY,
    displayName = Component.text("경비 탑 챔피언", NamedTextColor.GRAY),
    codeName = "turret_champion",
    material = Material.OBSERVER,
    description = listOf(
        Component.text("여러 재료를 들고 블록을 우클릭하면", NamedTextColor.WHITE),
        Component.text("저마다 다른 능력을 가진 포탑을 설치합니다.", NamedTextColor.WHITE)
    ),
    acquisition = AcquisitionType.QUEST
) {
    override val kinds: List<TurretKind> = TurretSpecs.CHAMPION_KINDS

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(TowerDefense.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by settingsCached {
        fun white(text: String) = Component.text(text, NamedTextColor.WHITE)
        fun value(number: Number): Component {
            val text = if (number is Double && number % 1.0 == 0.0) number.toInt().toString() else number.toString()
            return Component.text(text, NamedTextColor.GREEN)
        }
        fun seconds(ticks: Int) = value(if (ticks % 20 == 0) ticks / 20 else ticks / 20.0)
        fun name(text: String, color: NamedTextColor) = Component.text(text, color)
        fun cost(kind: TurretKind, stats: TurretStats) = white(" (").append(Component.text(kind.itemName + " ", NamedTextColor.YELLOW))
            .append(value(stats.ingotCost)).append(white(", ")).append(Component.text("레드스톤 ", NamedTextColor.YELLOW))
            .append(value(stats.redstoneCost)).append(white("): "))
        fun attack(stats: TurretStats) = white("주변 ").append(value(stats.range)).append(white("블록, ")).append(seconds(stats.fireIntervalTicks))
            .append(white("초마다 ")).append(value(stats.damage)).append(white(" 피해"))
        fun life(stats: TurretStats) = white(". 체력 ").append(value(stats.maxHealth)).append(white(", 1초마다 ")).append(value(stats.decayPerSecond))
            .append(white(" 감소."))

        val iron = stats(IronTurretKind); val copper = stats(CopperTurretKind); val gold = stats(GoldTurretKind)
        val diamond = stats(DiamondTurretKind); val echo = stats(EchoTurretKind); val amethyst = stats(AmethystTurretKind)
        val shulker = stats(ShulkerTurretKind); val emerald = stats(EmeraldTurretKind)
        listOf(
            Component.text("경비 전문가", NamedTextColor.GRAY).append(white(" 능력을 계승합니다.")),
            white("포탑은 설치하고 공격 간격만큼 지난 뒤부터 공격합니다."),
            name("철 포탑", NamedTextColor.GRAY).append(cost(IronTurretKind, iron)).append(attack(iron)).append(life(iron)),
            name("구리 포탑", NamedTextColor.GOLD).append(cost(CopperTurretKind, copper)).append(attack(copper))
                .append(white(", 범위 안의 모든 적에게 ")).append(value(copper.extra(CopperTurretKind.BURN))).append(white("초 화염"))
                .append(life(copper)),
            name("금 포탑", NamedTextColor.YELLOW).append(cost(GoldTurretKind, gold)).append(white("맞닿은 포탑의 속도 +"))
                .append(value((gold.extra(GoldTurretKind.SPEED) * 100).let { if (it % 1.0 == 0.0) it.toInt() else it })).append(white("%"))
                .append(life(gold)),
            name("다이아몬드 포탑", NamedTextColor.AQUA).append(cost(DiamondTurretKind, diamond)).append(attack(diamond))
                .append(white(", 적 ")).append(value(DiamondTurretKind.pierce(diamond))).append(white("명 관통")).append(life(diamond)),
            name("메아리 포탑", NamedTextColor.DARK_AQUA).append(cost(EchoTurretKind, echo)).append(attack(echo))
                .append(white(", 벽을 무시하고 방어구를 무시하는 음파")).append(life(echo)),
            name("자수정 포탑", NamedTextColor.LIGHT_PURPLE).append(cost(AmethystTurretKind, amethyst)).append(attack(amethyst))
                .append(white(", 구속 ")).append(value(amethyst.extra(AmethystTurretKind.SLOW_LEVEL).toInt())).append(white("단계 "))
                .append(value(amethyst.extra(AmethystTurretKind.SLOW_SECONDS))).append(white("초, 구속이 없는 적을 먼저 노림")).append(life(amethyst)),
            name("셜커 포탑", NamedTextColor.DARK_PURPLE).append(cost(ShulkerTurretKind, shulker)).append(attack(shulker))
                .append(white(", 따라가는 탄환, 공중부양 ")).append(value(shulker.extra(ShulkerTurretKind.LEVITATION))).append(white("초"))
                .append(life(shulker)),
            name("에메랄드 포탑", NamedTextColor.GREEN).append(cost(EmeraldTurretKind, emerald)).append(white("1초마다 맞닿은 포탑 체력 "))
                .append(value(emerald.extra(EmeraldTurretKind.HEAL))).append(white(" 회복(에메랄드 포탑 제외)")).append(life(emerald)),
            white("포탑은 회복 물약으로 회복할 수 있습니다."),
            white("포탑에게 공격받은 공격적인 몹은 그 포탑이 사라질 때까지 포탑을 노립니다."),
            white("쿨타임: ").append(seconds(cooldownTicks())).append(white("초"))
        )
    }
}
