package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.JudgementRay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object DoEndermansDreamOfFreeingEnd : Quest(
    displayText = Component.text("엔더맨은 엔드 해방의 꿈을 꾸는가?", NamedTextColor.LIGHT_PURPLE),
    codeName = "do_endermans_dream_of_freeing_end",
    isHidden = true,
    howToGet = Component.text("밸런스 붕괴", NamedTextColor.YELLOW)
    .append(Component.text(" 능력을 이용하여\n엔더맨을 해치지 않고 엔더 드래곤을 처치하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("끝의 차원의 지배자를 꿰뚫되, 그 땅의 주민들은 해치지 말 것…", NamedTextColor.GRAY), QuestHints.OVER_BALANCE_USES, 1),
    rewardAbility = JudgementRay
)
