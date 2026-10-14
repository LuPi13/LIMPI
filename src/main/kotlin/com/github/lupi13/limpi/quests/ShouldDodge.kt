package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.InvisibleDodge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object ShouldDodge : Quest(
    displayText = Component.text("굳이 회피해야해?", NamedTextColor.YELLOW),
    codeName = "should_dodge",
    isHidden = true,
    howToGet = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로 투명 상태에서 회피를 시전하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("아무도 당신을 볼 수 없는데, 회피를 한 이유가 있나요...?", NamedTextColor.GRAY), QuestProgressDefinitions.DODGE.counter, QuestHints.DODGE_THRESHOLD),
    rewardAbility = InvisibleDodge
)
