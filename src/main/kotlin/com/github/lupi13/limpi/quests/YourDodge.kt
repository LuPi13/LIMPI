package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.EndDodge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object YourDodge : Quest(
    displayText = Component.text("너의 회피는", NamedTextColor.DARK_GREEN),
    codeName = "your_dodge",
    isHidden = true,
    howToGet = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로 주변에 5마리의 엔더맨을 두고 회피를 시전하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("회피 좀 치는 검은 관객들 앞에서 회피를 자랑해보세요...", NamedTextColor.GRAY), QuestProgressDefinitions.DODGE.counter, QuestHints.DODGE_THRESHOLD),
    rewardAbility = EndDodge
)
