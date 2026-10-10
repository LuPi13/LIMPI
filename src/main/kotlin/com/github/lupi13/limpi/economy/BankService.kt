package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

enum class BankFailure(val message: String) {
    INVALID_AMOUNT("금액이 유효하지 않습니다."), UNKNOWN_PLAYER("해당 플레이어의 계좌를 찾을 수 없습니다."),
    SAME_ACCOUNT("자기 자신에게 송금할 수 없습니다."), INSUFFICIENT_FUNDS("계좌 잔액이 부족합니다."),
    OVERFLOW("변경 후 금액이 허용 범위를 초과합니다."), INVALID_ACCOUNT("계좌 데이터에 오류가 있습니다. 관리자에게 확인을 요청하세요."),
}
class BankException(val reason: BankFailure) : IllegalArgumentException(reason.message)
enum class BankOperation { TRANSFER, SET, ADJUST }
data class BankReceipt(
    val id: UUID, val operation: BankOperation, val actor: String, val target: UUID,
    val amount: Long, val fee: Long, val before: Map<UUID, Long>, val after: Map<UUID, Long>, val replayed: Boolean,
)

class BankService(private val store: StateStore, private val players: PlayerRepository) {
    fun transfer(id: UUID, sender: UUID, recipient: UUID, amount: Long): BankReceipt {
        if (sender == recipient) throw BankException(BankFailure.SAME_ACCOUNT)
        if (amount <= 0) throw BankException(BankFailure.INVALID_AMOUNT)
        return execute(id, BankOperation.TRANSFER, sender.toString(), recipient, amount) { transaction ->
            val from = Accounts.read(transaction, sender)
            val to = Accounts.read(transaction, recipient)
            val senderBalance = Accounts.balance(from)
            val recipientBalance = Accounts.balance(to)
            val value = Money(amount)
            val fee = value.fee(5)
            val total = checked { value + fee }
            if (senderBalance.units < total.units) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            val finalFrom = senderBalance - total
            val finalTo = checked { recipientBalance + value }
            from.set("money", finalFrom.units); to.set("money", finalTo.units)
            transaction.put(SqliteProfilePersistence.key(sender), from.saveToString())
            transaction.put(SqliteProfilePersistence.key(recipient), to.saveToString())
            receipt(id, BankOperation.TRANSFER, sender.toString(), recipient, amount, fee.units,
                mapOf(sender to senderBalance.units, recipient to recipientBalance.units),
                mapOf(sender to finalFrom.units, recipient to finalTo.units))
        }
    }

    /** An explicit admin set is also the repair path for a preserved negative legacy balance. */
    fun set(id: UUID, actor: String, target: UUID, amount: Long): BankReceipt {
        if (amount < 0) throw BankException(BankFailure.INVALID_AMOUNT)
        return execute(id, BankOperation.SET, actor, target, amount) { transaction ->
            val data = Accounts.read(transaction, target)
            val before = Accounts.integralBalance(data)
            data.set("money", amount)
            transaction.put(SqliteProfilePersistence.key(target), data.saveToString())
            receipt(id, BankOperation.SET, actor, target, amount, 0, mapOf(target to before), mapOf(target to amount))
        }
    }

    fun adjust(id: UUID, actor: String, target: UUID, delta: Long): BankReceipt {
        if (delta == 0L) throw BankException(BankFailure.INVALID_AMOUNT)
        return execute(id, BankOperation.ADJUST, actor, target, delta) { transaction ->
            val data = Accounts.read(transaction, target)
            val before = Accounts.balance(data).units
            val after = try { Math.addExact(before, delta) } catch (failure: ArithmeticException) {
                throw BankException(BankFailure.OVERFLOW)
            }
            if (after < 0) throw BankException(BankFailure.INSUFFICIENT_FUNDS)
            data.set("money", after)
            transaction.put(SqliteProfilePersistence.key(target), data.saveToString())
            receipt(id, BankOperation.ADJUST, actor, target, delta, 0, mapOf(target to before), mapOf(target to after))
        }
    }

    private fun execute(id: UUID, operation: BankOperation, actor: String, target: UUID, amount: Long,
                        body: (StateTransaction) -> String): BankReceipt {
        require(actor.isNotBlank())
        val fingerprint = fingerprint(operation.name, actor, target.toString(), amount.toString())
        val committed = store.execute(id, fingerprint, body)
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }
            .map { UUID.fromString(it.removePrefix("player/")) })
        return parseReceipt(committed)
    }

    private fun checked(body: () -> Money): Money = try { body() } catch (failure: ArithmeticException) {
        throw BankException(BankFailure.OVERFLOW)
    }

    private fun receipt(id: UUID, operation: BankOperation, actor: String, target: UUID, amount: Long, fee: Long,
                        before: Map<UUID, Long>, after: Map<UUID, Long>): String = YamlConfiguration().apply {
        set("id", id.toString()); set("operation", operation.name); set("actor", actor); set("target", target.toString())
        set("amount", amount); set("fee", fee)
        before.forEach { (id, value) -> set("before.$id", value) }
        after.forEach { (id, value) -> set("after.$id", value) }
    }.saveToString()

    private fun parseReceipt(committed: CommittedTransaction): BankReceipt {
        val data = YamlFiles.parse(committed.entry.receipt)
        fun balances(path: String) = requireNotNull(data.getConfigurationSection(path)).getValues(false)
            .map { (key, value) -> UUID.fromString(key) to (value as Number).toLong() }.toMap()
        return BankReceipt(committed.entry.id, BankOperation.valueOf(requireNotNull(data.getString("operation"))),
            requireNotNull(data.getString("actor")), UUID.fromString(data.getString("target")),
            data.getLong("amount"), data.getLong("fee"), balances("before"), balances("after"), committed.replayed)
    }

    companion object {
        /** Length-prefixed UTF-8 fields cannot collide through separators in actor names or item descriptions. */
        fun fingerprint(vararg fields: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            fields.forEach { field ->
                val bytes = field.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
