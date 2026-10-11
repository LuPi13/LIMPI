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
    val rewardAbility: Ability,
    val progress: QuestProgress? = null
) {
    fun visibleCondition(cleared: Boolean): Component =
        if (isHidden && !cleared) Component.text("???", net.kyori.adventure.text.format.NamedTextColor.GRAY) else howToGet

    fun visibleProgress(profile: org.bukkit.configuration.file.FileConfiguration, cleared: Boolean): List<String> {
        if (isHidden && !cleared) return emptyList()
        return progress?.counters.orEmpty().map { counter ->
            runCatching { "${counter.label} ${counter.format(counter.current(profile))}/${counter.format(counter.target)}".trim() }
                .getOrElse { "퀘스트 진행도 확인 필요" }
        }
    }
}
