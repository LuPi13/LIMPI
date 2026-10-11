package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.LightReflex
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object EnoughDodge : Quest(
    displayText = Component.text("이거면 충분해", NamedTextColor.RED),
    codeName = "enough_dodge",
    isHidden = true,
    howToGet = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로 회피하여 폭발에서 살아남으세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("폭발하기 직전의 위기상황을 모면하세요.", NamedTextColor.GRAY), QuestProgressDefinitions.DODGE.counter, QuestHints.DODGE_THRESHOLD),
    rewardAbility = LightReflex
)
