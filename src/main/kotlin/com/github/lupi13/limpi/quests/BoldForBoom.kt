package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.RealHandCannon
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object BoldForBoom : Quest(
    displayText = Component.text("폭파를 위한 대담함", NamedTextColor.DARK_RED),
    codeName = "bold_for_boom",
    isHidden = true,
    howToGet = Component.text("핸드캐논", NamedTextColor.GRAY)
        .append(Component.text("으로 충전된 크리퍼를 처치하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("번개를 머금은 초록빛 폭탄이 다가올 때, 물러서지 말고 쏘아라…", NamedTextColor.GRAY), QuestProgressDefinitions.CANNON_SHOTS.counter, 200),
    rewardAbility = RealHandCannon
)
