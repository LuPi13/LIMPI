package com.github.lupi13.limpi.estate

import java.util.UUID

/**
 * Protection decisions without Bukkit types. "Origin" is the estate a moving thing started in (null outside every
 * estate): a change inside an estate is allowed when it comes from the same estate or from someone trusted there.
 */
object EstateRules {
    /** A player may change [estate] when it is theirs, they are trusted there, or they bypass protection. */
    fun canBuild(estate: Estate?, player: UUID?, bypass: Boolean): Boolean =
        estate == null || bypass || (player != null && estate.trusted(player))

    /** Fluids, fire and growth may cross into an estate only from inside the same estate. */
    fun spreadAllowed(from: Estate?, to: Estate?): Boolean = to == null || to.id == from?.id

    /** A piston may move blocks out of or into estates other than its own only where no estate is involved. */
    fun pistonAllowed(piston: Estate?, touched: Iterable<Estate?>): Boolean = touched.all { it == null || it.id == piston?.id }

    /**
     * Whether an explosion may break a block in [block]. Allowed when the player behind it may build there, or when the
     * explosive was set off inside that same estate (the owner's own TNT); creepers, withers and cannons from outside are not.
     */
    fun explosionBreaks(block: Estate?, actorAllowed: (Estate) -> Boolean, origin: UUID?): Boolean =
        block == null || origin == block.id || actorAllowed(block)

    /** A block in an estate may fall only when what holds it up is in the same estate (no undermining from outside). */
    fun fallAllowed(block: Estate?, below: Estate?): Boolean = block == null || below?.id == block.id
}
