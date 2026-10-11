package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.RoaringFlame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object FireStrike : Quest(
    displayText = Component.text("불타는 돌진", NamedTextColor.DARK_RED),
    codeName = "fire_strike",
    isHidden = false,
    howToGet = Component.text("자신이 불타고 있을 때 ", NamedTextColor.WHITE)
        .append(Component.text("방패강타", NamedTextColor.GRAY))
        .append(Component.text("로 적을 처치하세요.", NamedTextColor.WHITE)),
    rewardAbility = RoaringFlame
)
