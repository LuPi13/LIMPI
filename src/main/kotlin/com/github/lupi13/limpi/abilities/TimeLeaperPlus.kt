package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.LightTime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

object TimeLeaperPlus : TimeLeapAbility(
    grade = Grade.EPIC,
    displayName = Component.text("시간 역행+", NamedTextColor.AQUA),
    codeName = "time_leaper_plus",
    material = Material.CLOCK,
    description = listOf(
        Component.text("시간 역행", NamedTextColor.AQUA)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("시간 역행 시 회복하는 체력이 증가하고", NamedTextColor.WHITE),
        Component.text("쿨타임이 감소합니다.", NamedTextColor.WHITE)
    ),
    acquisition = AcquisitionType.QUEST
) {
    override val activeName: Component = Component.text("시간여행장치+", NamedTextColor.AQUA)

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(LightTime.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("시간 역행", NamedTextColor.AQUA)
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE))
        ) + rewindLines() + listOf(
            Component.text("시간 역행으로 회복하는 체력이 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("heal_multiplier")}", NamedTextColor.GREEN))
                .append(Component.text("배가 됩니다.", NamedTextColor.WHITE)),
            cooldownLine()
        )
    }
}
