package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** Settings of abilities ported from GotchaAbility. */
object CloakSpecs {
    /** Gauge values are in display points; 1 point of drain/regen per second equals one source tick unit. */
    private fun cloakFields(initial: Double, max: Double, regen: Double) = listOf(
        integer("double_sneak_ms", 300, 1, 5000),
        number("initial_gauge", initial),
        number("max_gauge", max, 1.0, 1_000_000.0),
        number("activation_cost", 25.0, 0.0, 1_000_000.0),
        number("drain_per_second", 1.0, 0.0, 1_000_000.0),
        number("regen_per_second", regen, 0.0, 1_000_000.0),
        number("mob_reset_horizontal", 32.0, 0.0, 128.0),
        number("mob_reset_vertical", 16.0, 0.0, 128.0),
        number("mob_detect_range", 2.5, 0.0, 128.0),
        flag("hide_armor", true),
        flag("disable_armor_stats", true),
    )

    private fun check(settings: AbilitySettings) {
        require(settings.getDouble("initial_gauge") <= settings.getDouble("max_gauge")) { "initial_gauge must not exceed max_gauge" }
        require(settings.getDouble("activation_cost") <= settings.getDouble("max_gauge")) { "activation_cost must not exceed max_gauge" }
    }

    val all: Map<String, AbilitySpec> = linkedMapOf(
        "cloak" to AbilitySpec(cloakFields(50.0, 200.0, 1.0), ::check),
        "super_cloak" to AbilitySpec(cloakFields(62.5, 250.0, 1.5) + listOf(
            number("damage_reduction", 0.2, 0.0, 1.0),
            number("kill_gauge", 10.0, 0.0, 1_000_000.0),
            number("still_distance", 0.05, 0.0, 16.0),
        ), ::check),
    )
}
