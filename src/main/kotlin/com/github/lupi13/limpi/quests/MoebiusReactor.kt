package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.SuperCloak
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object MoebiusReactor : Quest(
    displayText = Component.text("뫼비우스 반응로", NamedTextColor.WHITE),
    codeName = "moebius_reactor",
    isHidden = true,
    howToGet = Component.text("은폐", NamedTextColor.WHITE)
        .append(Component.text(" 능력으로 게이지 200을 끊지 않고 모두 사용하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("뭐했길래 이 에너지를 한번에 전부 태운거야?", NamedTextColor.GRAY), QuestHints.CLOAK_USES, 10),
    rewardAbility = SuperCloak
) {
    /** Gauge points one uninterrupted cloak must consume (activation cost included), as in the source quest text. */
    const val REQUIRED_GAUGE = 200.0
}
