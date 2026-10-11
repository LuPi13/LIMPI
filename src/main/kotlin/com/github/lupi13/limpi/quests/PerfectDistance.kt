package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.ShieldStrikePlus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object PerfectDistance : Quest(
    displayText = Component.text("완벽한 거리조절", NamedTextColor.WHITE),
    codeName = "perfect_distance",
    isHidden = false,
    howToGet = Component.text("방패강타", NamedTextColor.GRAY)
        .append(Component.text(" 능력의 돌진이 끝나는 시점에 적과 부딪히세요.", NamedTextColor.WHITE)),
    rewardAbility = ShieldStrikePlus
)
