package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.StealthBackStab
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object GhostStab : Quest(
    displayText = Component.text("고스트 스탭", NamedTextColor.DARK_GRAY),
    codeName = "ghost_stab",
    isHidden = false,
    howToGet = Component.text("백스탭", NamedTextColor.GOLD)
        .append(Component.text(" 능력을 이용하여 100킬하세요.", NamedTextColor.WHITE)),
    rewardAbility = StealthBackStab
) {
    /** Profile counter `quest.back_stab_kills`, incremented atomically per back-stab kill. */
    const val COUNTER = "back_stab_kills"
    const val TARGET = 100L
}
