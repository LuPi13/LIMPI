package com.github.lupi13.limpi.market

import com.github.lupi13.limpi.delivery.DeliveryJournal
import com.github.lupi13.limpi.economy.*
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

enum class MarketFailure { NOT_AVAILABLE, STALE_QUOTE, UNKNOWN_OWNER, OWN_LISTING, NOT_OWNER }
class MarketException(val reason: MarketFailure) : IllegalStateException(reason.name)
data class MarketReceipt(
    val id: UUID, val listing: UUID, val buyer: UUID?, val seller: UUID,
    val price: Long, val fee: Long, val deliveryOwner: UUID, val replayed: Boolean,
)

/** GUI-independent settlement. Items become a durable claim in the same commit as the accounts/listing. */
class MarketService(
    private val store: StateStore, private val catalog: MarketCatalog,
    private val deliveries: DeliveryJournal, private val players: PlayerRepository,
) {
    fun buy(id: UUID, buyer: UUID, listingId: UUID, quotedPrice: Long): MarketReceipt {
        val committed = store.execute(id, BankService.fingerprint("MARKET_BUY", buyer.toString(), listingId.toString(), quotedPrice.toString())) { tx ->
            val listing = available(tx, listingId)
            if (listing.price.units != quotedPrice) throw MarketException(MarketFailure.STALE_QUOTE)
            val seller = requireNotNull(listing.seller)
            if (seller == buyer) throw MarketException(MarketFailure.OWN_LISTING)
            val from = Accounts.read(tx, buyer); val to = Accounts.read(tx, seller)
            val balance = Accounts.balance(from); val sellerBalance = Accounts.balance(to)
            if (balance.units < listing.price.units) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            val fee = listing.price.fee(5)
            val finalSeller = try { sellerBalance + (listing.price - fee) } catch (_: ArithmeticException) {
                throw BankException(BankFailure.OVERFLOW)
            }
            Accounts.write(tx, buyer, from, balance - listing.price)
            Accounts.write(tx, seller, to, finalSeller)
            catalog.write(tx, listing.copy(state = ListingState.SOLD, buyer = buyer))
            deliveries.stage(tx, id, buyer, "market purchase ${listing.id}", listOf(listing.item))
            receipt(id, listing, buyer, fee.units, buyer)
        }
        return result(committed)
    }

    /** Withdrawing a listing always returns it to its owner; inventory space does not erase the claim. */
    fun withdraw(id: UUID, seller: UUID, listingId: UUID): MarketReceipt {
        val committed = store.execute(id, BankService.fingerprint("MARKET_WITHDRAW", seller.toString(), listingId.toString())) { tx ->
            val listing = available(tx, listingId)
            if (listing.seller != seller) throw MarketException(MarketFailure.NOT_OWNER)
            catalog.write(tx, listing.copy(state = ListingState.RETURNED))
            deliveries.stage(tx, id, seller, "market return ${listing.id}", listOf(listing.item))
            receipt(id, listing, null, 0, seller)
        }
        return result(committed)
    }

    private fun available(tx: StateTransaction, id: UUID): MarketListing {
        val listing = catalog.read(tx, id) ?: throw MarketException(MarketFailure.NOT_AVAILABLE)
        if (listing.state == ListingState.OWNERSHIP_REVIEW) throw MarketException(MarketFailure.UNKNOWN_OWNER)
        if (listing.state != ListingState.ACTIVE) throw MarketException(MarketFailure.NOT_AVAILABLE)
        return listing
    }

    private fun receipt(id: UUID, listing: MarketListing, buyer: UUID?, fee: Long, owner: UUID) = YamlConfiguration().apply {
        set("id", id.toString()); set("listing", listing.id.toString()); set("buyer", buyer?.toString())
        set("seller", requireNotNull(listing.seller).toString()); set("price", listing.price.units)
        set("fee", fee); set("deliveryOwner", owner.toString())
    }.saveToString()

    private fun result(committed: CommittedTransaction): MarketReceipt {
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }
            .map { UUID.fromString(it.removePrefix("player/")) })
        val data = YamlFiles.parse(committed.entry.receipt)
        return MarketReceipt(committed.entry.id, UUID.fromString(data.getString("listing")), data.getString("buyer")?.let(UUID::fromString),
            UUID.fromString(data.getString("seller")), data.getLong("price"), data.getLong("fee"),
            UUID.fromString(data.getString("deliveryOwner")), committed.replayed)
    }
}
