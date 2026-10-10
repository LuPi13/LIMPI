package com.github.lupi13.limpi.player

import java.util.UUID

/** Stable read model; unknown fields remain in the repository and are not discarded. */
data class PlayerProfile(
    val id: UUID,
    val name: String?,
    /** null when the stored value is not an integral Long; never coerced to 0. */
    val balance: Long?,
    val abilityCode: String?,
    val unlockedAbilities: Set<String>,
)
