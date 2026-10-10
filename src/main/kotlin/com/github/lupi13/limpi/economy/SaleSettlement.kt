package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration

/** The per-slot frozen quote and exact removed stack remain in the durable removal terms. */
object SaleSettlement : RemovalEffect {
    const val KIND = "SELL"
    fun terms(prices: Map<Int, Money>) = YamlConfiguration().apply {
        set("schemaVersion", 1)
        prices.toSortedMap().forEach { (slot, price) -> set("prices.$slot", price.units) }
    }.saveToString()
    fun total(record: RemovalRecord): Money {
        require(record.kind == KIND)
        val data = YamlFiles.parse(record.terms); require(data.get("schemaVersion") == 1)
        val section = requireNotNull(data.getConfigurationSection("prices"))
        val slots = section.getKeys(false).map { it.toInt().also { slot -> require(it == slot.toString()) } }.toSet()
        val e = record.evidence
        require(slots == e.before.indices.filter { e.before[it] != e.after[it] }.toSet())
        var total = Money(0)
        for (slot in slots) {
            val raw = section.get(slot.toString()); require(raw is Int || raw is Long)
            val source = requireNotNull(e.before[slot]); val remaining = e.after[slot]?.count ?: 0
            total += Money((raw as Number).toLong()).times((source.count - remaining).toLong())
        }
        return total
    }
    override fun validate(tx: StateTransaction, record: RemovalRecord) {
        val balance = Accounts.balance(Accounts.read(tx, record.evidence.owner))
        try { balance + total(record) } catch (_: ArithmeticException) { throw BankException(BankFailure.OVERFLOW) }
    }
    override fun settle(tx: StateTransaction, record: RemovalRecord): String {
        val e = record.evidence; val amount = total(record)
        val account = Accounts.read(tx, e.owner); val before = Accounts.balance(account)
        val after = try { before + amount } catch (_: ArithmeticException) { throw BankException(BankFailure.OVERFLOW) }
        Accounts.write(tx, e.owner, account, after)
        return YamlConfiguration().apply {
            set("actor", e.owner.toString()); set("removal", e.id.toString()); set("total", amount.units)
            set("before", before.units); set("after", after.units); set("terms", record.terms)
        }.saveToString()
    }
}
