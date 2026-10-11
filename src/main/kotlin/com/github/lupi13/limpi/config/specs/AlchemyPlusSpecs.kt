package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.AbilitySpec
import com.github.lupi13.limpi.config.number
import com.github.lupi13.limpi.ability.AlchemyPolicy

object AlchemyPlusSpecs {
    val all = mapOf("alchemy_plus" to AbilitySpec(listOf(
        number("coal", 0.05, 0.0, 1.0), number("iron_nugget", 0.20, 0.0, 1.0),
        number("iron_ingot", 0.10, 0.0, 1.0), number("gold_nugget", 0.25, 0.0, 1.0),
        number("gold_ingot", 0.20, 0.0, 1.0), number("redstone", 0.10, 0.0, 1.0),
        number("lapis_lazuli", 0.05, 0.0, 1.0), number("emerald", 0.04, 0.0, 1.0),
        number("diamond", 0.009, 0.0, 1.0), number("netherite_ingot", 0.001, 0.0, 1.0)
    )) { settings -> AlchemyPolicy(AlchemyPolicy.OUTPUTS.associateWith(settings::getDouble)) })
}
