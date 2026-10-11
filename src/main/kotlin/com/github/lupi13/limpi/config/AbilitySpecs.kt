package com.github.lupi13.limpi.config

/** Defaults, types and constraints live together, independent of Bukkit ability instances. */
object AbilitySpecs {
    val all: Map<String, AbilitySpec> = linkedMapOf(
        "zombie_king" to AbilitySpec(listOf(
            number("armor_point", 2.0),
            number("damage_addition", 3.0),
            number("bullying_range", 32.0, 0.0, 128.0),
            number("reinforcement_probability", 0.3, 0.0, 1.0)
        )),
        "web_shooter" to AbilitySpec(listOf(
            number("range", 64.0, 0.0, 128.0),
            number("velocityAmplifier", 0.15, 0.0, 64.0),
            number("maxVelocity", 1.0, 0.000001, 64.0),
            number("damageAmplifier", 8.0),
            number("knockbackAmplifier", 3.0, 0.0, 64.0),
            number("liquidDragCoefficient", 0.3, 0.0, 64.0)
        )),
        "water_melee_boost" to AbilitySpec(listOf(
            number("damageBoost", 1.5)
        )),
        "upper_cut" to AbilitySpec(listOf(
            number("knockBackAmplifier", 1.1, 0.0, 64.0)
        )),
        "transcendence" to AbilitySpec(listOf(
            number("sword_required_damage", 30.0, 0.000001),
            number("arrow_required_damage", 30.0, 0.000001),
            number("sword_damage_amplifier", 1.3),
            number("arrow_damage_amplifier", 1.3),
            integer("duration_ticks", 200),
            integer("resistance_level", 1, 0, 255),
            integer("speed_level", 1, 0, 255),
            integer("additional_arrow_count", 3, 0, 256),
            number("additional_arrow_velocity", 2.0, 0.0, 64.0),
            number("additional_arrow_spread", 30.0, 0.0, 180.0),
            integer("additional_arrow_cooldown", 10),
            number("additional_sword_damage", 2.0),
            number("additional_sword_range", 3.0, 0.0, 128.0),
            integer("additional_sword_ticks", 3, 1)
        )) { settings -> Math.addExact(settings.getInt("duration_ticks"), 22) },
        "suspicious_sugar" to AbilitySpec(listOf(
            number("areaChance", 0.1, 0.0, 1.0),
            integer("duration", 200),
            number("range", 10.0, 0.0, 128.0)
        )),
        "shield_strike" to AbilitySpec(listOf(
            integer("cooldown", 10000),
            number("velocity", 1.1, 0.000001, 64.0),
            number("damage", 8.0),
            number("knockbackAmplifier", 1.3, 0.0, 64.0),
            number("maxKnockback", 1.5, 0.0, 64.0),
            flag("allowJump", false)
        )),
        "pulling_bow" to AbilitySpec(listOf(
            number("knockBackAmplifier", 1.1, 0.0, 64.0)
        )),
        "over_balance" to AbilitySpec(listOf(
            integer("shootingDelayTicks", 100),
            number("damage", 256.0),
            number("velocity", 2.0, 0.000001, 64.0),
            number("accelerateTimeTicks", 20.0, 0.000001),
            number("size", 3.0, 0.000001, 32.0),
            integer("lifeTimeTicks", 200, 1)
        )) { settings -> com.github.lupi13.limpi.ability.RailgunSettings.read(settings.asConfiguration(), false) },
        "muspell" to AbilitySpec(listOf(
            number("damage", 10.0),
            number("damage_range", 3.0, 0.000001, 128.0),
            integer("lifetime_ticks", 100, 1),
            integer("fire_ticks", 100),
            number("velocity", 1.5, 0.000001, 64.0),
            number("track_search_distance", 40.0, 0.000001, 128.0),
            number("track_search_angle", 15.0, 0.0, 180.0),
            number("tracking_coefficient", 0.2, 0.0, 64.0)
        )),
        "long_throw" to AbilitySpec(listOf(
            number("item", 2.0),
            number("splash_potion", 2.0),
            number("lingering_potion", 2.0)
        )),
        "knee_propel" to AbilitySpec(listOf(
            integer("baseLevel", 0, 0, 255),
            integer("increment", 1, 1, 255),
            integer("increaseTick", 10, 1),
            integer("maxStrength", 10, 1, 255)
        )) { settings -> require(settings.getInt("baseLevel") <= settings.getInt("maxStrength")); Math.multiplyExact(settings.getInt("maxStrength"), settings.getInt("increaseTick")) },
        "killing_dice" to AbilitySpec(listOf(
            number("damageReduction", 0.5, 0.0, 1.0),
            number("instantKillDegree", 2.0, 0.000001),
            integer("KillDelay", 10)
        )),
        "judgement_ray" to AbilitySpec(listOf(
            integer("shootingDelayTicks", 60),
            number("damage", 256.0),
            number("heal", 128.0),
            number("velocity", 3.0, 0.000001, 64.0),
            number("accelerateTimeTicks", 20.0, 0.000001),
            number("size", 4.0, 0.000001, 32.0),
            integer("lifeTimeTicks", 200, 1)
        )) { settings -> com.github.lupi13.limpi.ability.RailgunSettings.read(settings.asConfiguration(), true) },
        "impact_landing" to AbilitySpec(listOf(
            number("dampingRatio", 2.0),
            number("minimumDamage", 0.5)
        )),
        "hot_pickaxe" to AbilitySpec(listOf(
            integer("fireTicks", 60)
        )),
        "heavy_snowball" to AbilitySpec(listOf(
            number("damage", 1.0)
        )),
        "harmony_sword" to AbilitySpec(listOf(
            number("required_damage", 20.0, 0.000001),
            number("damage_amplifier", 1.2)
        )),
        "harmony_arrow" to AbilitySpec(listOf(
            number("required_damage", 20.0, 0.000001),
            number("damage_amplifier", 1.2)
        )),
        "gathering_storm" to AbilitySpec(listOf(
            number("projection_velocity", 2.0, 0.0, 64.0),
            number("storm_radius", 10.0, 0.0, 128.0),
            number("storm_damage", 4.0),
            number("damage_probability", 0.2, 0.0, 1.0),
            integer("storm_duration_tick", 200, 1),
            integer("storm_cooldown_milliseconds", 20000)
        )),
        "entity_thrower" to AbilitySpec(listOf(
            number("heldHeight", 0.5, 0.0, 64.0),
            number("forceDropDistance", 5.0, 0.0, 128.0),
            number("baseThrowVelocity", 1.0, 0.000001, 64.0),
            number("maxThrowVelocity", 3.0, 0.000001, 64.0),
            number("collisionDamage", 6.0),
            number("phantomRandomMovementIntensity", 0.01, 0.0, 64.0),
            flag("tntAndCreeperExplodeImmediately", true),
            number("enderManTeleportProbability", 0.01, 0.0, 1.0)
        )),
        "emerald_fix" to AbilitySpec(listOf(
            integer("repair", 50)
        )),
        "dodge" to AbilitySpec(listOf(
            integer("cooldown", 5000),
            number("velocity", 2.0, 0.000001, 64.0),
            flag("allowJump", false)
        )),
        "defense_all_in" to AbilitySpec(listOf(
            integer("noDamageMilliseconds", 1000)
        )),
        "cookie_run" to AbilitySpec(listOf(
            integer("duration", 200),
            integer("intensity", 1, 0, 255)
        )),
        "comet_strike" to AbilitySpec(listOf(
            integer("cooldown_milliseconds", 10000),
            number("jump_forward_amplifier", 0.02, 0.0, 64.0),
            number("jump_amplifier", 0.1, 0.0, 64.0),
            number("striking_speed", 5.0, 0.000001, 64.0),
            number("max_striking_angle", 45.0, 0.0, 180.0),
            integer("max_strike_ticks", 100, 1),
            number("range", 4.0, 0.0, 128.0),
            number("damage", 10.0)
        )),
        "chain_lightning" to AbilitySpec(listOf(
            number("damageRatio", 0.9),
            number("probability", 0.8, 0.0, 1.0),
            number("radius", 5.0, 0.000001, 128.0),
            integer("maximumChainHops", 16, 1, 256)
        )),
        "blunder_booster" to AbilitySpec(listOf(
            integer("energy_max", 200, 1),
            integer("energy_per_tick", 1),
            integer("dodge_energy", 100),
            number("velocity", 2.0, 0.000001, 64.0),
            integer("ignore_damage_energy", 150)
        )),
        "blade_leaper" to AbilitySpec(listOf(
            integer("throwLifeTimeTicks", 40, 1),
            number("throwVelocity", 2.0, 0.000001, 64.0),
            number("throwGravity", 0.03, 0.0, 64.0),
            number("damageRadius", 4.0, 0.000001, 128.0),
            number("damage", 8.0),
            integer("coolTimeTicks", 200)
        )) { settings -> Math.addExact(settings.getInt("throwLifeTimeTicks"), 26) },
        "alchemy" to AbilitySpec(listOf(
            number("coal", 0.3, 0.0, 1.0),
            number("iron_nugget", 0.15, 0.0, 1.0),
            number("iron_ingot", 0.1, 0.0, 1.0),
            number("gold_nugget", 0.15, 0.0, 1.0),
            number("gold_ingot", 0.1, 0.0, 1.0),
            number("redstone", 0.1, 0.0, 1.0),
            number("lapis_lazuli", 0.05, 0.0, 1.0),
            number("emerald", 0.039, 0.0, 1.0),
            number("diamond", 0.01, 0.0, 1.0),
            number("netherite_ingot", 0.001, 0.0, 1.0)
        )) { settings -> require(listOf("coal", "iron_nugget", "iron_ingot", "gold_nugget", "gold_ingot", "redstone", "lapis_lazuli", "emerald", "diamond", "netherite_ingot").sumOf(settings::getDouble).let { kotlin.math.abs(it - 1.0) <= 1.0e-9 }) { "Alchemy probabilities must sum to 1" } },
        "adaptive_defense" to AbilitySpec(listOf(
            number("damage_reduction", 0.8, 0.0, 1.0)
        ))
    ) + com.github.lupi13.limpi.config.specs.DodgeFamilySpecs.all + com.github.lupi13.limpi.config.specs.SmithAndShieldSpecs.all +
        com.github.lupi13.limpi.config.specs.CloakSpecs.all + com.github.lupi13.limpi.config.specs.StealthSpecs.all +
        com.github.lupi13.limpi.config.specs.ArcherySpecs.all + com.github.lupi13.limpi.config.specs.HandCannonSpecs.all +
        com.github.lupi13.limpi.config.specs.TimeLeapSpecs.all + com.github.lupi13.limpi.config.specs.AlchemyPlusSpecs.all
}
