package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.ItemGrant
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.*
import java.util.UUID

object LegacyMarketMigration {
    /** Decode and validate every item before mutating the catalog; keep the entire source document. */
    fun import(store: StateStore, catalog: MarketCatalog, owner: (String) -> UUID?, item: (Any) -> ItemGrant): Int {
        if (catalog.imported()) return 0
        val source = YamlFiles.parse(store.get("financial/market"))
        val listings = source.getKeys(false).filterNot { it.startsWith("_") }.map { code ->
            require(code.matches(Regex("[A-Z0-9]{4}"))) { "Invalid legacy market code: $code" }
            val sellerName = requireNotNull(source.getString("$code.seller"))
            val seller = owner(sellerName)
            val price = when (val raw = source.get("$code.price")) {
                is Byte, is Short, is Int, is Long -> (raw as Number).toLong()
                else -> throw IllegalArgumentException("Invalid legacy price for $code")
            }
            val id = UUID.nameUUIDFromBytes("legacy-market:$code".toByteArray(Charsets.UTF_8))
            MarketListing(id, code, source.getString("$code.name")?.takeIf(String::isNotBlank) ?: code,
                seller, sellerName, Money(price), item(requireNotNull(source.get("$code.item"))),
                if (seller == null) ListingState.OWNERSHIP_REVIEW else ListingState.ACTIVE)
        }
        store.atomic { tx -> catalog.importLegacy(tx, listings) }
        return listings.size
    }
}
