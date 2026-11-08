package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.economy.*
import net.kyori.adventure.text.Component
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
        ShopSpecials.of(code)?.let { special ->
            if (!prices.contains(special)) return null
            return ShopOffer(code, specialGrant(special), CatalogPrices.shop(prices.get(special), 0))
        }
        val material = Material.getMaterial(code) ?: return null
        if (!material.isItem || material.isAir) return null
        val name = material.name.lowercase(Locale.ROOT)
        if (!prices.contains(name)) return null
        val price = CatalogPrices.shop(prices.get(name), CatalogPrices.highestSell(catalog, name) { sell.get(it) })
        return ShopOffer(code, grants.getOrPut(material) { PaperItemCodec.grant(ItemStack(material)) }, price)
    }
    companion object {
        /** A plain stack of one material always serializes the same within a server run. */
        private val grants = java.util.concurrent.ConcurrentHashMap<Material, com.github.lupi13.limpi.delivery.ItemGrant>()
        private val specials = java.util.concurrent.ConcurrentHashMap<String, com.github.lupi13.limpi.delivery.ItemGrant>()

        private fun specialGrant(special: String) = specials.getOrPut(special) {
            when (special) {
                ShopSpecials.RECALL_SCROLL -> PaperItemCodec.grant(RecallScrolls.item())
                else -> error("unknown special product $special")
            }
        }

        /** The product name in messages; uncolored so the caller's color applies. */
        fun name(code: String): Component? = when (ShopSpecials.of(code)) {
            ShopSpecials.RECALL_SCROLL -> RecallScrolls.name
            else -> Material.matchMaterial(code)?.let(ItemNames::of)
        }

        /** The window icon: a special product's copy is not the real item, like the ability shop's ticket icons. */
        fun icon(offer: ShopOffer): ItemStack {
            val icon = PaperItemCodec.item(offer.item.payload, 1)
            return if (ShopSpecials.of(offer.code) == ShopSpecials.RECALL_SCROLL) RecallScrolls.unmark(icon) else icon
        }
    }
}
