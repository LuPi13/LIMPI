package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.WinterShroud
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object ColdDodge : Quest(
    displayText = Component.text("바람처럼 차갑게", NamedTextColor.DARK_AQUA),
    codeName = "cold_dodge",
    isHidden = true,
    howToGet = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로 빠른 속도로 회피하세요.", NamedTextColor.WHITE)),
    rewardAbility = WinterShroud
)
