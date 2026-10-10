package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.ItemGrant
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class ListingState { ESCROW, ACTIVE, SOLD, RETURNED }
data class MarketListing(
    val id: UUID, val code: String, val name: String, val seller: UUID, val sellerName: String,
    val price: Money, val item: ItemGrant, val state: ListingState = ListingState.ACTIVE,
    val buyer: UUID? = null,
) {
    init {
        require(code.matches(Regex("[A-Z0-9]{4}"))); require(name.isNotBlank()); require(sellerName.isNotBlank())
        require(state != ListingState.SOLD || buyer != null)
    }
}

/** Each listing has a permanent UUID; a reused four-letter code cannot revive an old GUI quote. */
class MarketCatalog(private val store: StateStore) {
    fun available(): List<MarketListing> = store.documents("market_code/").map { (key, id) ->
        requireNotNull(find(UUID.fromString(id))).also { check(key == "market_code/${it.code}") }
    }
    fun listings(): List<MarketListing> = store.documents("market_listing/").map { (key, payload) ->
        decode(payload).also { check(key == key(it.id)) }
    }
    fun find(id: UUID): MarketListing? = store.get(key(id))?.let { decode(it).also { listing -> check(listing.id == id) } }
    fun read(tx: StateTransaction, id: UUID): MarketListing? = tx.get(key(id))?.let { decode(it).also { listing -> check(listing.id == id) } }
    fun write(tx: StateTransaction, listing: MarketListing) {
        val index = "market_code/${listing.code}"
        if (listing.state in setOf(ListingState.ACTIVE, ListingState.ESCROW)) {
            val reserved = tx.get(index)
            if (reserved != null && reserved != listing.id.toString()) throw MarketException(MarketFailure.CODE_EXISTS)
            tx.put(index, listing.id.toString())
        } else if (tx.get(index) == listing.id.toString()) tx.put(index, null)
        tx.put(key(listing.id), encode(listing))
    }
    fun findByCode(code: String): MarketListing? = store.get("market_code/$code")?.let { find(UUID.fromString(it)) }

    internal fun encode(listing: MarketListing) = YamlConfiguration().apply {
        set("schemaVersion", 1); set("id", listing.id.toString()); set("code", listing.code); set("name", listing.name)
        set("seller", listing.seller.toString()); set("sellerName", listing.sellerName); set("price", listing.price.units)
        set("item.payload", listing.item.payload); set("item.count", listing.item.count); set("item.limit", listing.item.limit)
        set("state", listing.state.name); set("buyer", listing.buyer?.toString())
    }.saveToString()

    internal fun decode(payload: String): MarketListing {
        val data = YamlFiles.parse(payload)
        require(data.get("schemaVersion") == 1) { "Unsupported market catalog schema" }
        fun number(path: String): Long = when (val value = data.get(path)) {
            is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            else -> throw IllegalArgumentException("Invalid market number: $path")
        }
        return MarketListing(UUID.fromString(data.getString("id")), requireNotNull(data.getString("code")),
            requireNotNull(data.getString("name")), UUID.fromString(requireNotNull(data.getString("seller"))),
            requireNotNull(data.getString("sellerName")), Money(number("price")),
            ItemGrant(requireNotNull(data.getString("item.payload")), number("item.count"), Math.toIntExact(number("item.limit"))),
            ListingState.valueOf(requireNotNull(data.getString("state"))), data.getString("buyer")?.let(UUID::fromString))
    }

    companion object { private fun key(id: UUID) = "market_listing/$id" }
}
