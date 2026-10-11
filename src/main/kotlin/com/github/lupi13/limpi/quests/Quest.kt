package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.Ability
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.configuration.file.FileConfiguration

/**
 * A hidden quest's hint, shown by /ability quest instead of "???" once the profile's durable `quest.<counter>`
 * reaches [threshold]. The counter is a usage count of the related base ability; it never completes a quest.
 */
data class QuestHint(val text: Component, val counter: String, val threshold: Long) {
    init { require(counter.matches(Regex("[a-z0-9_]+")) && threshold > 0) }

    /** Malformed (non-integral or negative) counters never reveal the hint and are left untouched. */
    fun revealed(profile: FileConfiguration): Boolean {
        val raw = profile.get("quest.$counter")
        if (raw !is Int && raw !is Long) return false
        return (raw as Number).toLong() >= threshold
    }
}

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
    val progress: QuestProgress? = null,
    val hint: QuestHint? = null
) {
    fun visibleCondition(cleared: Boolean): Component =
        if (isHidden && !cleared) Component.text("???", NamedTextColor.GRAY) else howToGet

    /** Like [visibleCondition], but an uncleared hidden quest shows its hint once the player has used the ability enough. */
    fun visibleCondition(profile: FileConfiguration, cleared: Boolean): Component {
        val hint = hint
        if (!isHidden || cleared || hint == null || !hint.revealed(profile)) return visibleCondition(cleared)
        return Component.text("힌트: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(hint.text)
    }

    fun visibleProgress(profile: FileConfiguration, cleared: Boolean): List<String> {
        if (isHidden && !cleared) return emptyList()
        return progress?.counters.orEmpty().map { counter ->
            runCatching { "${counter.label} ${counter.format(counter.current(profile))}/${counter.format(counter.target)}".trim() }
                .getOrElse { "퀘스트 진행도 확인 필요" }
        }
    }
}
