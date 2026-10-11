package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** Settings of abilities ported from GotchaAbility. */
object DodgeFamilySpecs {
    val all: Map<String, AbilitySpec> = linkedMapOf(
        "dodge_plus" to AbilitySpec(listOf(
            integer("cooldown", 2500),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false)
        )),
        "end_dodge" to AbilitySpec(listOf(
            integer("cooldown", 10000),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false),
            number("distance", 20.0, 0.0, 128.0),
            number("speed_distance_multiplier", 200.0, 0.0, 1024.0),
            number("max_distance", 64.0, 0.0, 128.0),
            integer("delay_ticks", 10, 0, 1200)
        )),
        "light_reflex" to AbilitySpec(listOf(
            integer("cooldown", 5000),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false),
            integer("window", 500),
            number("damage_multiplier", 0.1, 0.0, 1.0)
        )),
        "blight_ranger" to AbilitySpec(listOf(
            integer("cooldown", 10000),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false),
            integer("invisible_ticks", 140, 1, 72000),
            integer("speed_level", 3, 0, 255),
            number("smoke_radius", 9.0, 0.0, 64.0),
            integer("weaken_ticks", 60, 1, 72000),
            number("target_clear_range", 20.0, 0.0, 128.0)
        )),
        "invisible_dodge" to AbilitySpec(listOf(
            integer("cooldown", 10000),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false),
            integer("invisible_ticks", 100, 1, 72000),
            number("target_clear_range", 20.0, 0.0, 128.0)
        )),
        "critical_dodge" to AbilitySpec(listOf(
            integer("cooldown", 5000),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false),
            integer("window", 3000),
            number("damage_multiplier", 1.5, 0.0, 64.0),
            integer("kill_cooldown_reduction", 2500)
        )),
        "winter_shroud" to AbilitySpec(listOf(
            integer("cooldown", 10000),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false),
            number("horizontal_range", 8.0, 0.0, 64.0),
            number("vertical_range", 4.0, 0.0, 64.0),
            integer("freeze_ticks", 200, 0, 72000)
        ))
    )
}
