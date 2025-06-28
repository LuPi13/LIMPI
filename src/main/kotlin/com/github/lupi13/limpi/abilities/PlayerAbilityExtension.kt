package com.github.lupi13.limpi.abilities

import org.bukkit.entity.Player

val Player.ability: Ability?
    get() {
        val codeName = AbilityManager.getPlayerAbilityCodeName(this) ?: return null
        return AbilityManager.getAbilityByCodeName(codeName)
    }