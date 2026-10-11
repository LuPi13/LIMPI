package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.TimeLeaperPlus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object LightTime : Quest(
    displayText = Component.text("빛의 시간", NamedTextColor.WHITE),
    codeName = "light_time",
    isHidden = false,
    howToGet = Component.text("시간 역행", NamedTextColor.AQUA)
        .append(Component.text(" 능력으로 총 200의 체력을 회복하세요.", NamedTextColor.WHITE)),
    rewardAbility = TimeLeaperPlus
)
