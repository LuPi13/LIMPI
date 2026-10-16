package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class SaleReceipt(val id: UUID, val total: Long, val replayed: Boolean)

/**
 * Sale-window settlement: the price is recomputed from the player's stored sold counts inside the transaction,
 * and the credit, the updated counts and the receipt commit together. A replay of the same request returns the
 * first receipt without crediting or counting again.
 */
class SaleService(private val store: StateStore, private val players: PlayerRepository) {
    fun counters(owner: UUID): Map<String, SaleCounter> = SaleCounters.decode(store.get(SaleCounters.key(owner)))

    fun sell(request: UUID, owner: UUID, lines: List<SaleLine>, catalog: SellCatalog, now: Long, recoveryMillis: Long,
             invalid: (SaleLine, Exception) -> Unit = { _, _ -> }): SaleReceipt {
        require(lines.isNotEmpty())
        val fields = listOf("SELL_GUI", owner.toString(), now.toString()) + lines.sortedBy { it.slot }.map { "${it.slot}:${it.code}:${it.amount}" }
        val committed = store.execute(request, BankService.fingerprint(*fields.toTypedArray())) { tx ->
            val counters = SaleCounters.decode(tx.get(SaleCounters.key(owner)))
            val quote = try { catalog.quote(lines, counters, now, recoveryMillis, invalid) } catch (_: ArithmeticException) {
                throw BankException(BankFailure.OVERFLOW)
            }
            val account = Accounts.read(tx, owner); val before = Accounts.balance(account)
            val after = try { before + quote.total } catch (_: ArithmeticException) { throw BankException(BankFailure.OVERFLOW) }
            if (quote.total.units > 0) Accounts.write(tx, owner, account, after)
            if (quote.counters != counters) tx.put(SaleCounters.key(owner), SaleCounters.encode(quote.counters))
            YamlConfiguration().apply {
                set("actor", "SELL_GUI"); set("target", owner.toString()); set("total", quote.total.units)
                set("before", before.units); set("after", after.units); set("slots", quote.slots.sorted())
            }.saveToString()
        }
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        return SaleReceipt(request, YamlFiles.parse(committed.entry.receipt).getLong("total"), committed.replayed)
    }
}
