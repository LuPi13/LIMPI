package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.ExpRich
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object ExpAbsorber : Quest(
    displayText = Component.text("경험치 흡수기", NamedTextColor.GOLD),
    codeName = "exp_absorber",
    isHidden = false,
    howToGet = Component.text("테라버닝", NamedTextColor.YELLOW)
    .append(Component.text(" 능력을 적용중인 상태로 100레벨에 도달하세요.", NamedTextColor.WHITE)),
    rewardAbility = ExpRich
)