package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.economy.*
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.Locale

class PaperShopCatalog(private val plugin: Plugin) {
    fun offer(code: String): ShopOffer? {
        val material = Material.getMaterial(code) ?: return null
        if (!material.isItem || material.isAir) return null
        val name = material.name.lowercase(Locale.ROOT)
        val ratios = FileManager.getShopRatioConfig()
        if (!ratios.contains(name)) return null
        val ratio = CatalogPrices.ratio(ratios.get(name) ?: plugin.config.get("DefaultShopRatio"))
        val price = CatalogPrices.shop(CatalogPrices.sell(FileManager.getSellPriceConfig().get(name)), ratio)
        return ShopOffer(code, PaperItemCodec.grant(ItemStack(material)), price)
    }
    fun available(): List<ShopOffer> = FileManager.getShopRatioConfig().getKeys(false).sorted().mapNotNull { code ->
        try { offer(code.uppercase(Locale.ROOT)) ?: run { plugin.logger.warning("Invalid shop material $code"); null } }
        catch (failure: Exception) { plugin.logger.warning("Invalid shop entry $code: ${failure.message}"); null }
    }
}
