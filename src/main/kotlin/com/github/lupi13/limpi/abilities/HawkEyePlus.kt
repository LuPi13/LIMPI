package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.GiveAll
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound

object HawkEyePlus : HawkEyeAbility(
    grade = Grade.EPIC,
    displayName = Component.text("매의 눈+", NamedTextColor.YELLOW),
    codeName = "hawk_eye_plus",
    description = listOf(
        Component.text("매의 눈", NamedTextColor.YELLOW)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("쿨타임이 10초로 감소합니다. 추가 아이템을", NamedTextColor.WHITE),
        Component.text("하나 더 떨어뜨립니다.", NamedTextColor.WHITE)
    ),
    acquisition = AcquisitionType.QUEST,
    bonusSound = { Sound.ITEM_DYE_USE }
) {
    override val glint: Boolean get() = true

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(GiveAll.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("매의 눈", NamedTextColor.YELLOW)
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE))
        ) + effectLines()
    }
}
