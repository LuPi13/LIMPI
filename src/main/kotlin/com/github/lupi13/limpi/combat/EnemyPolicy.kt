package com.github.lupi13.limpi.combat

import org.bukkit.configuration.ConfigurationSection

enum class TargetGroup { TAMED, NON_AGGRESSIVE, NEUTRAL, AGGRESSIVE, PLAYER }

data class EnemySettings(private val modes: Map<TargetGroup, Int>) {
    init { require(modes.keys == TargetGroup.entries.toSet() && modes.values.all { it in 0..2 }) }
    fun mode(group: TargetGroup) = modes.getValue(group)
    companion object {
        fun read(data: ConfigurationSection) = EnemySettings(TargetGroup.entries.associateWith { group ->
            val raw = data.get("AbilityEnemy.${group.name}")
            if (raw == null) { if (group == TargetGroup.PLAYER) 1 else 2 }
            else {
                require(raw is Int || raw is Long) { "AbilityEnemy.${group.name} must be an integer" }
                val value = (raw as Number).toLong(); require(value in 0..2); value.toInt()
            }
        })
    }
}

object EnemyPolicy {
    fun hostile(group: TargetGroup?, mode: Int, sameTeam: Boolean = false, hasPlayerOwner: Boolean = false,
                targetingOwner: Boolean = false, self: Boolean = false): Boolean {
        require(mode in 0..2)
        if (self) return false
        return when (group) {
            TargetGroup.PLAYER -> when (mode) { 0 -> false; 1 -> !sameTeam; else -> true }
            TargetGroup.TAMED -> when (mode) { 0 -> !hasPlayerOwner; 1 -> !sameTeam; else -> true }
            null -> true
            else -> when (mode) { 0 -> false; 1 -> targetingOwner; else -> true }
        }
    }
    fun cycle(mode: Int, forward: Boolean): Int { require(mode in 0..2); return (mode + if (forward) 1 else 2) % 3 }
}
