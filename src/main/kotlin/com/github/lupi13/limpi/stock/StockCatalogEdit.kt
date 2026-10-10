package com.github.lupi13.limpi.stock

import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration

object StockCatalogEdit {
    fun add(source: FileConfiguration, name: String, code: String, material: String, price: Money, timestamp: String): YamlConfiguration {
        require(name.isNotBlank() && !name.equals("all", true)) { "종목 이름은 비어 있거나 all일 수 없습니다." }
        require(code.matches(Regex("[A-Z0-9]{4}"))) { "종목 코드는 4자리 대문자/숫자입니다." }
        require(material.isNotBlank()) { "아이템 material을 지정하세요." }
        require(price.units > 0) { "가격은 양수여야 합니다." }
        require(!source.isSet(code)) { "이미 사용한 종목 코드는 재사용할 수 없습니다. 상장 중지는 restore로 복구하세요." }
        require(StockCatalog.allCodes(source).none { source.getString("$it.name") == name }) { "이미 존재하는 주식 이름입니다." }
        return YamlFiles.parse(source.saveToString()).apply {
            set("$code.name", name); set("$code.material", material); set("$code.initialPrice", price.units)
            set("$code.currentPrice", price.units); set("$code.listed", true); set("lastStockStand", timestamp)
        }
    }
    fun edit(source: FileConfiguration, action: String, code: String, timestamp: String): YamlConfiguration {
        require(action in setOf("remove", "restore", "refix"))
        val candidates = if (action == "restore") StockCatalog.allCodes(source) else StockCatalog.codes(source)
        val selected = if (code.equals("all", true)) candidates else listOf(code).also { require(code in candidates) { "해당 상태의 종목을 찾을 수 없습니다." } }
        require(selected.isNotEmpty()) { "변경할 종목이 없습니다." }
        if (action != "remove") selected.forEach { StockCatalog.price(source, it) }
        // Price updates also read the base price; restoring one with a bad base would stop every update. refix rewrites it.
        if (action == "restore") selected.forEach { StockCatalog.price(source, it, "initialPrice") }
        return YamlFiles.parse(source.saveToString()).apply {
            for (selectedCode in selected) when (action) {
                "remove" -> set("$selectedCode.listed", false)
                "restore" -> set("$selectedCode.listed", true)
                "refix" -> set("$selectedCode.initialPrice", StockCatalog.price(source, selectedCode).units)
            }
            set("lastStockStand", timestamp)
        }
    }
}
