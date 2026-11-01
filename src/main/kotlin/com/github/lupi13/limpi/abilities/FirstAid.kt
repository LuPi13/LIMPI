package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.NeedHealing
import com.github.lupi13.limpi.quests.Quest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

private fun white(text: String) = Component.text(text, NamedTextColor.WHITE)
private fun item(text: String) = Component.text(text, NamedTextColor.YELLOW)
private fun value(number: Number): Component =
    Component.text(if (number is Double && number % 1.0 == 0.0) number.toInt().toString() else number.toString(), NamedTextColor.GREEN)
private fun seconds(ticks: Int): Component = value(if (ticks % 20 == 0) ticks / 20 else ticks / 20.0)

/** 응급처치: 종이로 자신이나 적이 아닌 대상을 서서히 회복합니다. */
object FirstAid : FirstAidAbility(
    grade = Grade.COMMON,
    displayName = Component.text("응급처치", NamedTextColor.YELLOW),
    codeName = "first_aid",
    description = listOf(
        white("종이를 들고 우클릭하면 자신이나"),
        white("바라보는 대상의 체력을 회복합니다.")
    ),
    acquisition = AcquisitionType.GACHA,
    throwing = false
) {
    override val countsQuest: Boolean get() = true

    override val relatedQuest: List<Quest> by lazy { listOf(NeedHealing) }

    override val details: List<Component> by settingsCached { firstAidLines(healAmount(), healSeconds(), cooldownTicks()) }

    internal fun firstAidLines(amount: Double, duration: Double, cooldownTicks: Int): List<Component> = listOf(
        item("종이").append(white("를 들고 우클릭하면 ")).append(item("종이")).append(white(" 1개를 소모해 자신의 체력을 "))
            .append(value(amount)).append(white(" 회복합니다.")),
        white("적대적이지 않은 대상을 보고 우클릭하면 그 대상의 체력을 ").append(value(amount)).append(white(" 회복합니다.")),
        white("회복은 ").append(value(duration)).append(white("초에 걸쳐 서서히 진행됩니다.")),
        white("쿨타임: ").append(seconds(cooldownTicks)).append(white("초"))
    )
}

/** 전투 의무관: 응급처치를 계승하고, 종이를 던져 멀리 있는 대상을 회복합니다. */
object CombatMedic : FirstAidAbility(
    grade = Grade.RARE,
    displayName = Component.text("전투 의무관", NamedTextColor.YELLOW),
    codeName = "combat_medic",
    description = listOf(
        Component.text("응급처치", NamedTextColor.YELLOW).append(white(" 능력을 계승합니다.")),
        white("종이를 던져 멀리 있는 대상을 회복합니다.")
    ),
    acquisition = AcquisitionType.QUEST,
    throwing = true
) {
    override val glint: Boolean get() = true

    override val howToGet: Component by lazy {
        white("퀘스트 ").append(NeedHealing.displayName).append(white(" 달성"))
    }

    override val details: List<Component> by settingsCached {
        listOf(Component.text("응급처치", NamedTextColor.YELLOW).append(white(" 능력을 계승합니다."))) +
            FirstAid.firstAidLines(healAmount(), healSeconds(), cooldownTicks()).dropLast(1) +
            listOf(
                white("쿨타임이 아닐 때 ").append(item("종이")).append(white("를 던지면 ")).append(value(config!!.getDouble("throw_speed_multiplier")))
                    .append(white("배 빠르게 날아가고,")),
                white("적대적이지 않은 대상에 맞으면 그 대상을 회복합니다. 던질 때 쿨타임이 적용됩니다."),
                white("쿨타임: ").append(seconds(cooldownTicks())).append(white("초"))
            )
    }
}
