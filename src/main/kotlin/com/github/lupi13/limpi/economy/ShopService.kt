package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/** [bundle] is the most one Shift purchase buys (a stack for the server shop, 10 tickets for the ability shop). */
data class ShopOffer(val code: String, val item: ItemGrant, val price: Money, val bundle: Long = item.limit.toLong()) {
    init { require(code.isNotBlank()); require(item.count == 1L); require(bundle > 0) }
}
data class ShopReceipt(val transaction: UUID, val count: Long, val total: Long, val replayed: Boolean)
class ShopUnavailable : IllegalStateException("상품이 삭제되었거나 가격이 변경되었습니다. 화면을 다시 열어주세요.")

class ShopService(private val store: StateStore, private val deliveries: DeliveryJournal,
                  private val players: PlayerRepository, private val offer: (String) -> ShopOffer?,
                  private val kind: String = "SHOP_BUY") {
    fun buy(request: UUID, buyer: UUID, code: String, displayedPrice: Long, bundle: Boolean): ShopReceipt {
        require(displayedPrice >= 0)
        val committed = store.execute(request, BankService.fingerprint(kind, buyer.toString(), code, displayedPrice.toString(), bundle.toString())) { tx ->
            val current = offer(code) ?: throw ShopUnavailable()
            if (current.code != code || current.price.units != displayedPrice) throw ShopUnavailable()
            val account = Accounts.read(tx, buyer); val before = Accounts.balance(account)
            val count = if (!bundle) 1L else if (current.price.units == 0L) current.bundle
                else minOf(current.bundle, before.units / current.price.units)
            if (count == 0L) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            val cost = try { current.price.times(count) } catch (_: ArithmeticException) { throw BankException(BankFailure.OVERFLOW) }
            if (cost.units > before.units) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            Accounts.write(tx, buyer, account, before - cost)
            deliveries.stage(tx, request, buyer, "${if (kind == "SHOP_BUY") "shop" else "ability shop"} purchase $code", listOf(current.item.copy(count = count)))
            YamlConfiguration().apply {
                set("actor", buyer.toString()); set("code", code); set("price", displayedPrice); set("count", count)
                set("total", cost.units); set("before", before.units); set("after", before.units - cost.units)
            }.saveToString()
        }
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        val receipt = YamlFiles.parse(committed.entry.receipt)
        return ShopReceipt(request, receipt.getLong("count"), receipt.getLong("total"), committed.replayed)
    }
}
