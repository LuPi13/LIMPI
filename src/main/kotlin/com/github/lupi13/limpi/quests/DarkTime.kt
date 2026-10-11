package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.Chronobreak
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object DarkTime : Quest(
    displayText = Component.text("어둠의 시간", NamedTextColor.DARK_GRAY),
    codeName = "dark_time",
    isHidden = true,
    howToGet = Component.text("시간 역행", NamedTextColor.AQUA)
        .append(Component.text(" 능력으로 총 50의 체력을 잃으세요.", NamedTextColor.WHITE)),
    rewardAbility = Chronobreak,
    progress = QuestProgressDefinitions.TIME_LOST
)
