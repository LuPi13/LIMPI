package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.Ability
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent

abstract class Quest(
    val displayText: Component,
    val codeName: String,
    val isHidden: Boolean,
    open val howToGet: Component,
    open val description: Component = (if (isHidden) {
        Component.text("???")
    } else {
        howToGet
    }),
    open val displayName: Component = displayText.hoverEvent(HoverEvent.showText(description)),
    val rewardAbility: Ability
) {
}