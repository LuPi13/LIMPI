package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.ChargeShotPlus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object WhyDidYouDoThat : Quest(
    displayText = Component.text("도대체 왜 그러셨어요...", NamedTextColor.RED),
    codeName = "why_did_you_do_that",
    isHidden = true,
    howToGet = Component.text("차지샷", NamedTextColor.AQUA)
        .append(Component.text(" 능력을 5분간 충전하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("차지샷을 정말 오랫동안 충전해보세요...", NamedTextColor.GRAY), QuestHints.CHARGE_SHOT_USES, 20),
    rewardAbility = ChargeShotPlus
)
