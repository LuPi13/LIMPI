package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.HawkEyePlus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object GiveAll : Quest(
    displayText = Component.text("주머니에 있는 거 다 꺼내!", NamedTextColor.WHITE),
    codeName = "give_all",
    isHidden = false,
    howToGet = Component.text("매의 눈", NamedTextColor.YELLOW)
        .append(Component.text(" 능력으로 100개의 추가 아이템을 획득하세요.", NamedTextColor.WHITE)),
    rewardAbility = HawkEyePlus
)
