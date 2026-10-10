package com.github.lupi13.limpi.player

import java.util.UUID

/** Stable read model; unknown legacy fields remain in the repository and are not discarded. */
data class PlayerProfile(
    val id: UUID,
    val name: String?,
    val balance: Long,
    val abilityCode: String?,
    val unlockedAbilities: Set<String>,
)
