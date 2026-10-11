package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.DodgePlus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object DodgeMaster : Quest(
    displayText = Component.text("회피의 달인", NamedTextColor.AQUA),
    codeName = "dodge_master",
    isHidden = false,
    howToGet = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로 회피를 100번 사용하세요.", NamedTextColor.WHITE)),
    rewardAbility = DodgePlus
)
