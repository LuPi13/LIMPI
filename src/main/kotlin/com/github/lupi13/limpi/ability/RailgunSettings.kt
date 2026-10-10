package com.github.lupi13.limpi.ability

import org.bukkit.configuration.ConfigurationSection

data class RailgunSettings(val delay: Int, val damage: Double, val heal: Double, val speed: Double,
                          val acceleration: Double, val size: Double, val lifetime: Int) {
    init {
        require(delay >= 0 && lifetime > 0)
        require(listOf(damage, heal, speed, acceleration, size).all(Double::isFinite))
        require(damage >= 0 && heal >= 0 && speed > 0 && acceleration > 0 && size > 0 && size <= Float.MAX_VALUE)
        Math.addExact(Math.addExact(delay, lifetime), 1)
    }
    companion object {
        fun read(data: ConfigurationSection, healing: Boolean): RailgunSettings {
            fun integer(path: String): Int {
                val value = data.get(path); require(value is Int || value is Long) { "Non-integral railgun $path" }
                val long = (value as Number).toLong(); require(long in 0..Int.MAX_VALUE.toLong())
                return long.toInt()
            }
            fun number(path: String) = (data.get(path) as? Number)?.toDouble() ?: error("Missing railgun $path")
            return RailgunSettings(integer("shootingDelayTicks"), number("damage"), if (healing) number("heal") else 0.0,
                number("velocity"), number("accelerateTimeTicks"), number("size"), integer("lifeTimeTicks"))
        }
    }
}
