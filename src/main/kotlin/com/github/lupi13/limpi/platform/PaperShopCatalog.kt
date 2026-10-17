package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.economy.*
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.Locale

class PaperShopCatalog(private val plugin: Plugin) {
    fun offer(code: String): ShopOffer? {
        val sell = FileManager.getSellPriceConfig()
        return offer(code, FileManager.getShopPriceConfig(), sell, SellCatalog.of(sell))
    }
    /** The shop window lists every entry: read the price files and build the sell catalog once, not per entry. */
    fun available(): List<ShopOffer> {
        val prices = FileManager.getShopPriceConfig(); val sell = FileManager.getSellPriceConfig()
        val catalog = runCatching { SellCatalog.of(sell) }
        return prices.getKeys(false).mapNotNull { code ->
            try { offer(code.uppercase(Locale.ROOT), prices, sell, catalog.getOrThrow()) ?: run { plugin.logger.warning("Invalid shop material $code"); null } }
            catch (failure: Exception) { plugin.logger.warning("Invalid shop entry $code: ${failure.message}"); null }
        }
    }
    private fun offer(code: String, prices: org.bukkit.configuration.ConfigurationSection, sell: org.bukkit.configuration.ConfigurationSection,
                      catalog: SellCatalog): ShopOffer? {
        val material = Material.getMaterial(code) ?: return null
        if (!material.isItem || material.isAir) return null
        val name = material.name.lowercase(Locale.ROOT)
        if (!prices.contains(name)) return null
        val price = CatalogPrices.shop(prices.get(name), CatalogPrices.highestSell(catalog, name) { sell.get(it) })
        return ShopOffer(code, grants.getOrPut(material) { PaperItemCodec.grant(ItemStack(material)) }, price)
    }
    private companion object {
        /** A plain stack of one material always serializes the same within a server run. */
        val grants = java.util.concurrent.ConcurrentHashMap<Material, com.github.lupi13.limpi.delivery.ItemGrant>()
    }
}
