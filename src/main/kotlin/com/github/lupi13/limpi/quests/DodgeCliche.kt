package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.CriticalDodge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object DodgeCliche : Quest(
    displayText = Component.text("전형적인 회피 클리셰", NamedTextColor.DARK_PURPLE),
    codeName = "dodge_cliche",
    isHidden = true,
    howToGet = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로 회피 직후 적을 일격에 처치하세요.", NamedTextColor.WHITE)),
    hint = QuestHint(Component.text("회피 이후에는 뭔가 멋있는 장면이 있어야합니다.", NamedTextColor.GRAY), QuestProgressDefinitions.DODGE.counter, QuestHints.DODGE_THRESHOLD),
    rewardAbility = CriticalDodge
)
