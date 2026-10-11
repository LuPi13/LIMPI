package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** Settings of abilities ported from GotchaAbility. */
object SmithAndShieldSpecs {
    private val alchemyOutputs = listOf("coal", "iron_nugget", "iron_ingot", "gold_nugget", "gold_ingot", "redstone",
        "lapis_lazuli", "emerald", "diamond", "netherite_ingot")

    val all: Map<String, AbilitySpec> = linkedMapOf(
        "shield_strike_plus" to AbilitySpec(listOf(
            integer("cooldown", 10000),
            number("velocity", 1.2, 0.000001, 64.0),
            number("damage", 8.0),
            number("knockbackAmplifier", 1.3, 0.0, 64.0),
            number("maxKnockback", 1.5, 0.0, 64.0),
            flag("allowJump", false)
        )),
        "roaring_flame" to AbilitySpec(listOf(
            integer("cooldown", 10000),
            number("velocity", 1.1, 0.000001, 64.0),
            number("damage", 8.0),
            number("knockbackAmplifier", 1.3, 0.0, 64.0),
            number("maxKnockback", 1.5, 0.0, 64.0),
            flag("allowJump", false),
            integer("fireTicks", 100),
            integer("fireRange", 7, 0, 32)
        )),
        "metal_smith" to AbilitySpec(listOf(
            number("coal", 0.2, 0.0, 1.0),
            number("iron_nugget", 0.2, 0.0, 1.0),
            number("iron_ingot", 0.1, 0.0, 1.0),
            number("gold_nugget", 0.2, 0.0, 1.0),
            number("gold_ingot", 0.1, 0.0, 1.0),
            number("redstone", 0.1, 0.0, 1.0),
            number("lapis_lazuli", 0.05, 0.0, 1.0),
            number("emerald", 0.04, 0.0, 1.0),
            number("diamond", 0.009, 0.0, 1.0),
            number("netherite_ingot", 0.001, 0.0, 1.0),
            integer("fireTicks", 60),
            integer("repair", 20)
        )) { settings -> require(kotlin.math.abs(alchemyOutputs.sumOf(settings::getDouble) - 1.0) <= 1.0e-9) { "Metal smith probabilities must sum to 1" } },
        "linear_bow" to AbilitySpec(listOf(
            number("minimumSpeed", 2.9, 0.0, 64.0),
            integer("delayTicks", 5, 0, 200),
            integer("lifeTicks", 2, 1, 200),
            number("speedMultiplier", 100.0, 1.0, 1000.0)
        ))
    )
}
