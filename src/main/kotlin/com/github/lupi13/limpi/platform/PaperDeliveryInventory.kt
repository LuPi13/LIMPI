package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.delivery.*
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.Base64
import java.util.UUID

object PaperItemCodec {
    fun payload(item: ItemStack): String = Base64.getEncoder().encodeToString(item.asOne().serializeAsBytes())
    fun item(payload: String, count: Int): ItemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(payload)).apply { amount = count }
    fun grant(item: ItemStack) = ItemGrant(payload(item), item.amount.toLong(), item.maxStackSize)
    fun snapshot(player: Player, includeOffHand: Boolean = false): List<StoredStack?> {
        check(Bukkit.isPrimaryThread())
        val items = player.inventory.storageContents.toList() + if (includeOffHand) listOf(player.inventory.itemInOffHand) else emptyList()
        return items.map { item ->
            if (item == null || item.type.isAir) null else StoredStack(payload(item), item.amount,
                minOf(item.maxStackSize, player.inventory.maxStackSize))
        }
    }
}

class PaperDeliveryInventory(
    private val player: Player, private val checkpoint: NamespacedKey, private val files: PlayerCheckpointFile,
    private val includeOffHand: Boolean = false,
) : DeliveryInventory {
    override fun stackLimit(): Int { checkMainThread(); return player.inventory.maxStackSize }
    override fun snapshot(): List<StoredStack?> {
        checkMainThread()
        return PaperItemCodec.snapshot(player, includeOffHand)
    }

    override fun persistedCheckpoint(): UUID? { checkMainThread(); return files.read(player.uniqueId) }

    override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
        checkMainThread()
        check(player.isOnline && !player.isDead) { "Player is unavailable" }
        check(snapshot() == before) { "Inventory changed before delivery" }
        val storageSize = player.inventory.storageContents.size
        check(after.size == storageSize + if (includeOffHand) 1 else 0)
        // Decode everything before touching a slot, so a malformed payload cannot cause a partial replacement.
        val contents = after.map { stack -> stack?.let { PaperItemCodec.item(it.payload, it.count) } }.toTypedArray()
        // Verify the configured directory and save path before any physical item changes.
        val probe = UUID.randomUUID()
        player.persistentDataContainer.set(checkpoint, PersistentDataType.STRING, probe.toString())
        player.saveData()
        files.confirm(player.uniqueId, probe)
        check(snapshot() == before) { "Inventory changed during save preflight" }
        player.inventory.storageContents = contents.take(storageSize).toTypedArray()
        if (includeOffHand) player.inventory.setItemInOffHand(contents.last())
        player.persistentDataContainer.set(checkpoint, PersistentDataType.STRING, id.toString())
        player.saveData()
        files.confirm(player.uniqueId, id)
        check(snapshot() == after) { "Inventory serialization changed during delivery" }
    }

    private fun checkMainThread() { check(Bukkit.isPrimaryThread()) { "Inventory delivery must run on the server thread" } }
}
