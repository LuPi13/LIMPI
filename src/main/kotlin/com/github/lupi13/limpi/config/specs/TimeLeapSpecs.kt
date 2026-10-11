package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** Settings of abilities ported from GotchaAbility. */
object TimeLeapSpecs {
    private fun rewind(cooldown: Int, healMultiplier: Double) = listOf(
        integer("cooldown_ticks", cooldown, 1, 72000),
        integer("rewind_ticks", 60, 1, 600),
        integer("rewind_visual_ticks", 4, 0, 20),
        number("rewind_max_speed", 3.0, 0.1, 10.0),
        number("heal_multiplier", healMultiplier, 0.0, 64.0)
    )

    val all: Map<String, AbilitySpec> = linkedMapOf(
        "time_leaper" to AbilitySpec(rewind(600, 1.0)),
        "time_leaper_plus" to AbilitySpec(rewind(400, 1.5)),
        "chronobreak" to AbilitySpec(rewind(400, 1.0) + listOf(
            number("radius", 5.0, 0.0, 32.0),
            number("max_health_damage_ratio", 0.5, 0.0, 1.0),
            integer("absorption_ticks", 100, 1, 72000),
            number("damage_per_absorption_level", 15.0, 0.000001, 1000000.0),
            integer("max_absorption_level", 5, 0, 255)
        ))
    )
}
