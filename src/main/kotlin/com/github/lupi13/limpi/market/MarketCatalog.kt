package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.ItemGrant
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class ListingState { ACTIVE, OWNERSHIP_REVIEW, SOLD, RETURNED }
data class MarketListing(
    val id: UUID, val code: String, val name: String, val seller: UUID?, val sellerName: String,
    val price: Money, val item: ItemGrant, val state: ListingState = ListingState.ACTIVE,
    val buyer: UUID? = null,
) {
    init {
        require(code.matches(Regex("[A-Z0-9]{4}"))); require(name.isNotBlank()); require(sellerName.isNotBlank())
        require(price.units > 0)
        require(state == ListingState.OWNERSHIP_REVIEW || seller != null) { "Active listings require a known seller UUID" }
        require(state != ListingState.SOLD || buyer != null)
    }
}

/** Each listing has a permanent UUID; a reused four-letter code cannot revive an old GUI quote. */
class MarketCatalog(private val store: StateStore) {
    fun listings(): List<MarketListing> = store.documents("market_listing/").map { (key, payload) ->
        decode(payload).also { check(key == key(it.id)) }
    }
    fun find(id: UUID): MarketListing? = store.get(key(id))?.let(::decode)
    fun read(tx: StateTransaction, id: UUID): MarketListing? = tx.get(key(id))?.let(::decode)
    fun write(tx: StateTransaction, listing: MarketListing) = tx.put(key(listing.id), encode(listing))

    /** Only the legacy migration or a future confirmed inventory escrow may publish a new listing. */
    fun importLegacy(tx: StateTransaction, listings: List<MarketListing>) {
        check(tx.get("migration/market_catalog_v1") == null) { "Market catalog already imported" }
        require(listings.map { it.id }.distinct().size == listings.size)
        require(listings.map { it.code }.distinct().size == listings.size)
        check(tx.documents("market_listing/").isEmpty()) { "Cannot overwrite an existing market catalog" }
        listings.forEach { write(tx, it) }
        tx.put("migration/market_catalog_v1", "imported: ${listings.size}\n")
    }

    private fun encode(listing: MarketListing) = YamlConfiguration().apply {
        set("schemaVersion", 1); set("id", listing.id.toString()); set("code", listing.code); set("name", listing.name)
        set("seller", listing.seller?.toString()); set("sellerName", listing.sellerName); set("price", listing.price.units)
        set("item.payload", listing.item.payload); set("item.count", listing.item.count); set("item.limit", listing.item.limit)
        set("state", listing.state.name); set("buyer", listing.buyer?.toString())
    }.saveToString()

    private fun decode(payload: String): MarketListing {
        val data = YamlFiles.parse(payload)
        require(data.get("schemaVersion") == 1) { "Unsupported market catalog schema" }
        fun number(path: String): Long = when (val value = data.get(path)) {
            is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            else -> throw IllegalArgumentException("Invalid market number: $path")
        }
        return MarketListing(UUID.fromString(data.getString("id")), requireNotNull(data.getString("code")),
            requireNotNull(data.getString("name")), data.getString("seller")?.let(UUID::fromString),
            requireNotNull(data.getString("sellerName")), Money(number("price")),
            ItemGrant(requireNotNull(data.getString("item.payload")), number("item.count"), Math.toIntExact(number("item.limit"))),
            ListingState.valueOf(requireNotNull(data.getString("state"))), data.getString("buyer")?.let(UUID::fromString))
    }

    companion object { private fun key(id: UUID) = "market_listing/$id" }
}
