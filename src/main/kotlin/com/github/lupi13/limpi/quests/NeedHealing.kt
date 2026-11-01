package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.CombatMedic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/** 치료가 필요하신가요?: 응급처치로 자신이 아닌 대상을 50번 회복합니다(회복을 시작할 때 셈). */
object NeedHealing : Quest(
    displayText = Component.text("치료가 필요하신가요?", NamedTextColor.YELLOW),
    codeName = "need_healing",
    isHidden = false,
    howToGet = Component.text("응급처치", NamedTextColor.YELLOW)
        .append(Component.text("로 자신이 아닌 대상을 ${QuestProgressDefinitions.FIRST_AID_OTHERS.target}번 회복하세요.", NamedTextColor.WHITE)),
    rewardAbility = CombatMedic,
    progress = QuestProgressDefinitions.FIRST_AID_OTHERS
)
