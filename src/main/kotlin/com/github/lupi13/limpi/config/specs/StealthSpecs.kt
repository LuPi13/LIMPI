package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** Settings of abilities ported from GotchaAbility. */
object StealthSpecs {
    private fun backStabFields() = listOf(
        number("damage_multiplier", 1.5, 0.0, 64.0),
        number("back_angle", 120.0, 0.0, 360.0),
    )

    val all: Map<String, AbilitySpec> = linkedMapOf(
        "back_stab" to AbilitySpec(backStabFields()),
        "berserker" to AbilitySpec(listOf(
            number("max_multiplier", 2.5, 1.0, 64.0),
        )),
        "block_thrower" to AbilitySpec(listOf(
            number("damage", 2.0),
            number("velocity", 1.0, 0.05, 10.0),
            integer("cooldown_ms", 250, 0, 60_000),
        )),
        "counter" to AbilitySpec(listOf(
            integer("cooldown_ticks", 100, 0, 72_000),
            integer("stance_ticks", 40, 1, 1200),
            integer("window_ms", 500, 0, 60_000),
            integer("counter_delay_ticks", 10, 0, 1200),
            number("damage_multiplier", 2.0, 0.0, 64.0),
            integer("slowness_level", 8, 1, 256),
        )) { settings ->
            // 반격은 태세가 유지되는 동안 실행되어야 합니다.
            require(settings.getInt("window_ms") / 50 + settings.getInt("counter_delay_ticks") < settings.getInt("stance_ticks")) {
                "window_ms/50 + counter_delay_ticks must be shorter than stance_ticks"
            }
        },
    )
}
