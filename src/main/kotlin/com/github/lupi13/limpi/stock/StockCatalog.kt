package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import org.bukkit.configuration.file.FileConfiguration

object StockCatalog {
    fun allCodes(data: FileConfiguration): List<String> = data.getKeys(false)
        .filter { it.matches(Regex("[0-9A-Z]{4}")) && data.isConfigurationSection(it) }.onEach {
            val listed = data.get("$it.listed")
            require(listed == null || listed is Boolean) { "Invalid listed state for $it" }
        }
    fun codes(data: FileConfiguration): List<String> = allCodes(data).filter { data.get("$it.listed") != false }
    fun price(data: FileConfiguration, code: String, field: String = "currentPrice"): Money {
        require(code in allCodes(data)) { "유효하지 않은 종목입니다." }
        val raw = data.get("$code.$field")
        require(raw is Int || raw is Long) { "Invalid integral $field for $code" }
        return Money((raw as Number).toLong()).also { require(it.units > 0) { "Invalid price for $code" } }
    }
}
