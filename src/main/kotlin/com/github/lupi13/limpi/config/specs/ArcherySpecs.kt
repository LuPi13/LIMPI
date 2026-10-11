package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** Settings of abilities ported from GotchaAbility. */
object ArcherySpecs {
    val all: Map<String, AbilitySpec> = linkedMapOf(
        "focus_bow" to AbilitySpec(listOf(
            integer("brake_ticks", 20, 0, 72000),
            number("brake_multiplier", 0.3, 0.0, 1.0),
            integer("slowness_amplifier", 7, 0, 255)
        )),
        "hawk_eye" to AbilitySpec(listOf(
            integer("cooldown_ticks", 400, 0, 72000),
            number("horizontal_range", 16.0, 0.0, 64.0),
            number("vertical_range", 8.0, 0.0, 64.0),
            integer("glow_ticks", 200, 1, 72000),
            integer("drop_multiplier", 2, 1, 16),
            integer("exp_multiplier", 2, 1, 16),
            integer("extra_items", 1, 0, 64)
        )),
        "hawk_eye_plus" to AbilitySpec(listOf(
            integer("cooldown_ticks", 200, 0, 72000),
            number("horizontal_range", 16.0, 0.0, 64.0),
            number("vertical_range", 8.0, 0.0, 64.0),
            integer("glow_ticks", 200, 1, 72000),
            integer("drop_multiplier", 2, 1, 16),
            integer("exp_multiplier", 2, 1, 16),
            integer("extra_items", 2, 0, 64)
        )),
        "lava_jump" to AbilitySpec(listOf(
            number("launch_speed", 0.75, 0.0, 10.0),
            number("horizontal_spread", 0.25, 0.0, 2.0)
        ))
    )
}
