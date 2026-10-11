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
    hint = QuestHint(Component.text("되감은 시간이 언제나 너의 편은 아니다. 잃어버린 것을 세어 보라…", NamedTextColor.GRAY), QuestHints.TIME_LEAPER_USES, 5),
    rewardAbility = Chronobreak,
    progress = QuestProgressDefinitions.TIME_LOST
)
