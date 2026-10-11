package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** Settings of abilities ported from GotchaAbility. */
object HandCannonSpecs {
    val all: Map<String, AbilitySpec> = linkedMapOf(
        "hand_cannon" to AbilitySpec(listOf(
            number("damage", 1.0),
            number("velocity", 3.0, 0.000001, 64.0),
            integer("lifetime_ticks", 20, 1, 1200)
        )),
        "pulse_cannon" to AbilitySpec(listOf(
            number("damage", 1.0),
            number("velocity", 3.0, 0.000001, 64.0),
            integer("lifetime_ticks", 20, 1, 1200),
            integer("burst_count", 5, 1, 64),
            number("no_invulnerability_chance", 0.1, 0.0, 1.0)
        )),
        "charge_shot" to AbilitySpec(listOf(
            number("max_damage", 10.0),
            integer("charge_ticks_per_damage", 4, 1, 1200),
            integer("max_charge_ticks", 40, 1, 1200),
            integer("lifetime_ticks", 127, 1, 1200),
            number("charging_slowness", 0.45, 0.0, 1.0),
            integer("cooldown_ticks", 10, 0, 72000)
        )),
        "explosion_move" to AbilitySpec(listOf(
            number("damage", 6.0),
            integer("max_charges", 6, 1, 64),
            integer("charge_ticks", 120, 1, 72000),
            number("recoil", 1.5, 0.0, 16.0),
            number("hit_distance", 2.0, 0.0, 16.0),
            number("hit_size", 3.0, 0.000001, 16.0)
        )),
        "real_hand_cannon" to AbilitySpec(listOf(
            number("explosion_power", 1.5, 0.0, 16.0),
            flag("break_blocks", true),
            number("velocity", 0.4, 0.000001, 16.0),
            integer("cooldown_ticks", 10, 0, 72000),
            integer("lifetime_ticks", 200, 1, 1200)
        ))
    )
}
