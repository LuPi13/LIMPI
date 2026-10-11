package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.PulseCannon
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object TriggerFinger : Quest(
    displayText = Component.text("TRIGGER FINGER!", NamedTextColor.YELLOW),
    codeName = "trigger_finger",
    isHidden = false,
    howToGet = Component.text("핸드캐논", NamedTextColor.GRAY)
        .append(Component.text("을 1초에 20번 사격하세요.", NamedTextColor.WHITE)),
    description = Component.text("It's BOSHY TIME!", NamedTextColor.WHITE),
    rewardAbility = PulseCannon
)
