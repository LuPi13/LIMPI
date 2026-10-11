package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.ChargeShot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object NoHurt : Quest(
    displayText = Component.text("손가락 안아파요?", NamedTextColor.RED),
    codeName = "no_hurt",
    isHidden = false,
    howToGet = Component.text("핸드캐논", NamedTextColor.GRAY)
        .append(Component.text("을 총 ${QuestProgressDefinitions.CANNON_SHOTS.target}회 사격하세요.", NamedTextColor.WHITE)),
    rewardAbility = ChargeShot,
    progress = QuestProgressDefinitions.CANNON_SHOTS
)
