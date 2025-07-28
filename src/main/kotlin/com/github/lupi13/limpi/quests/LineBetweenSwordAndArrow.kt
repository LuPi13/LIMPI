package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.ExpRich
import com.github.lupi13.limpi.abilities.Transcendence
import com.github.lupi13.limpi.abilities.ZombieKing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object LineBetweenSwordAndArrow : Quest(
    displayText = Component.text("검과 화살의 경계", NamedTextColor.LIGHT_PURPLE),
    codeName = "line_between_sword_and_arrow",
    isHidden = false,
    howToGet = Component.text("조화: 검", NamedTextColor.DARK_PURPLE)
        .append(Component.text(", ", NamedTextColor.WHITE))
        .append(Component.text("조화: 화살", NamedTextColor.DARK_PURPLE))
        .append(Component.text(" 능력의 효과로\n증가한 피해로 각각 10번 공격하세요.", NamedTextColor.WHITE)),
    rewardAbility = Transcendence
)