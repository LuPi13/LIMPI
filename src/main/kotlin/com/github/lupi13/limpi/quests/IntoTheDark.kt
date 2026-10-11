package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.BlightRanger
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object IntoTheDark : Quest(
    displayText = Component.text("어둠 속으로", NamedTextColor.DARK_GRAY),
    codeName = "into_the_dark",
    isHidden = true,
    howToGet = Component.text("소멸의 걸음", NamedTextColor.DARK_GRAY)
        .append(Component.text(" 능력으로 주변 5블록 안에 몬스터가 10마리 이상일 때 회피하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("괴물들이 우글거리는 한가운데서 도망치세요.", NamedTextColor.GRAY), QuestHints.INVISIBLE_DODGE_USES, 10),
    rewardAbility = BlightRanger
)
