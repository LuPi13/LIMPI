package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.delivery.*
import com.github.lupi13.limpi.market.*
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.economy.SaleSettlement
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Level

class DeliveryController(private val plugin: Plugin, private val service: DeliveryService, private val escrow: MarketEscrow, private val removals: RemovalJournal) {
    private val checkpoint = NamespacedKey(plugin, "delivery_checkpoint")
    private val directory = plugin.config.getString("PlayerDataDirectory")?.takeIf(String::isNotBlank)?.let { configured ->
        val path = Path.of(configured)
        if (path.isAbsolute) path else plugin.server.worldContainer.toPath().resolve(path)
    } ?: plugin.server.worlds.first().worldFolder.toPath().resolve("playerdata")
    private val files = PlayerCheckpointFile(directory, checkpoint.toString())
    private val blocked = mutableSetOf<UUID>()
    var afterClaim: (Player) -> Unit = {}
    init { plugin.logger.info("Delivery checkpoint directory: ${directory.toAbsolutePath().normalize()}") }

    fun blocked(id: UUID) = id in blocked
    fun release(id: UUID) { blocked.remove(id); service.release(id) }

    fun claim(player: Player, freshLogin: Boolean = false): Boolean {
        if (blocked(player.uniqueId) && !freshLogin) return false
        if (freshLogin) blocked.remove(player.uniqueId)
        try {
            val inventory = PaperDeliveryInventory(player, checkpoint, files)
            val pendingRemovals = removals.pending(player.uniqueId)
            check(escrow.pending(player.uniqueId).size + pendingRemovals.size <= 1) { "Multiple interrupted removals" }
            escrow.recover(player.uniqueId, inventory, freshLogin)
            val removalInventory = pendingRemovals.singleOrNull()?.let { removalInventory(player, it.evidence.before.size) } ?: inventory
            removals.recover(player.uniqueId, removalInventory, freshLogin)
            val result = service.drain(player.uniqueId, inventory, freshLogin)
            if (result.delivered > 0) player.sendMessage(Component.text("미수령 물품 ${result.delivered}건을 지급했습니다.", NamedTextColor.GREEN))
            if (result.waitingForSpace > 0) player.sendMessage(Component.text(
                "미수령 물품 ${result.waitingForSpace}건이 남아 있습니다. 공간을 확보한 뒤 /financial claim을 사용하세요.", NamedTextColor.YELLOW))
            afterClaim(player)
            return true
        } catch (failure: Exception) {
            blocked.add(player.uniqueId)
            plugin.logger.log(Level.SEVERE, "Delivery recovery failed for ${player.uniqueId}; inventory use is blocked", failure)
            player.kick(Component.text("물품 지급 기록을 확인해야 합니다. 관리자에게 UUID ${player.uniqueId}를 알려주세요.", NamedTextColor.RED))
            return false
        }
    }

    fun register(player: Player, id: UUID, code: String, name: String, price: Money): Boolean {
        if (!claim(player)) return false // Finish any earlier checkpoint before reserving a new operation.
        val inventory = PaperDeliveryInventory(player, checkpoint, files)
        var prepared = false
        try {
            val record = escrow.prepare(id, player.uniqueId, player.name, code, name, price,
                inventory.snapshot(), player.inventory.heldItemSlot)
            prepared = true
            escrow.apply(record, inventory)
            return true
        } catch (failure: Exception) {
            if (!prepared && failure is MarketException) throw failure // A code conflict has not touched an inventory.
            blocked.add(player.uniqueId)
            plugin.logger.log(Level.SEVERE, "Market escrow $id failed for ${player.uniqueId}", failure)
            player.kick(Component.text("매물 회수 기록을 복구해야 합니다. 거래 UUID: $id", NamedTextColor.RED))
            return false
        }
    }

    fun sell(player: Player, id: UUID, before: List<StoredStack?>, prices: Map<Int, Money>): Boolean {
        require(prices.isNotEmpty() && prices.keys.all { it in before.indices && before[it] != null })
        val after = before.toMutableList().apply { prices.keys.forEach { this[it] = null } }.toList()
        return remove(player, id, SaleSettlement.KIND, SaleSettlement.terms(prices), before, after).also { success ->
            if (success) {
                val record = requireNotNull(removals.find(player.uniqueId, id))
                player.sendMessage(Component.text("${SaleSettlement.total(record).units}원 판매를 확정했습니다. 거래: $id", NamedTextColor.GREEN))
            }
        }
    }

    fun remove(player: Player, id: UUID, kind: String, terms: String, before: List<StoredStack?>, after: List<StoredStack?>): Boolean {
        if (!claim(player)) return false
        val inventory = removalInventory(player, before.size)
        require(inventory.snapshot() == before) { "인벤토리가 변경되었습니다. 소모 대상을 다시 확인하세요." }
        var prepared = false
        try {
            val record = removals.prepare(id, player.uniqueId, kind, terms, before, after)
            prepared = true
            removals.apply(record, inventory)
            return true
        } catch (failure: Exception) {
            if (!prepared && (failure is com.github.lupi13.limpi.economy.BankException || failure is IllegalArgumentException)) throw failure
            blocked.add(player.uniqueId)
            plugin.logger.log(Level.SEVERE, "Item removal $id ($kind) failed for ${player.uniqueId}", failure)
            player.kick(Component.text("물품 회수 기록을 복구해야 합니다. 거래 UUID: $id", NamedTextColor.RED))
            return false
        }
    }

    private fun removalInventory(player: Player, size: Int): PaperDeliveryInventory {
        val storageSize = player.inventory.storageContents.size
        require(size == storageSize || size == storageSize + 1) { "Unsupported removal inventory size $size" }
        return PaperDeliveryInventory(player, checkpoint, files, size == storageSize + 1)
    }
}
