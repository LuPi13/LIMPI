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
    hint = QuestHint(Component.text("굉음이 모든 것을 삼키기 직전, 한 걸음이면 충분하다…", NamedTextColor.GRAY), QuestProgressDefinitions.DODGE.counter, QuestHints.DODGE_THRESHOLD),
    rewardAbility = LightReflex
)
