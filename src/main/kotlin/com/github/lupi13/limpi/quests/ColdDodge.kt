package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.WinterShroud
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object ColdDodge : Quest(
    displayText = Component.text("바람처럼 차갑게", NamedTextColor.DARK_AQUA),
    codeName = "cold_dodge",
    isHidden = true,
    howToGet = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로 ", NamedTextColor.WHITE))
        .append(Component.text("신속 II", NamedTextColor.BLUE))
        .append(Component.text(" 이상의 상태에서 빠른 속도로 회피하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("바람처럼 빠르게 이동하는 물약을 먹고 회피하세요.", NamedTextColor.GRAY), QuestProgressDefinitions.DODGE.counter, QuestHints.DODGE_THRESHOLD),
    rewardAbility = WinterShroud
)
