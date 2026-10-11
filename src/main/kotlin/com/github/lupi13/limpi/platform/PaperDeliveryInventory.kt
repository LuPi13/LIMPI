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
    /**
     * Canonical bytes: a freshly built Bukkit ItemStack (meta not yet applied to a server stack) can serialize
     * differently from the same item read back from an inventory, so round-trip through a server stack first.
     */
    fun payload(item: ItemStack): String =
        Base64.getEncoder().encodeToString(ItemStack.deserializeBytes(item.asOne().serializeAsBytes()).serializeAsBytes())
    fun item(payload: String, count: Int): ItemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(payload)).apply { amount = count }
    fun grant(item: ItemStack) = ItemGrant(payload(item), item.amount.toLong(), item.maxStackSize)
    fun snapshot(player: Player, includeOffHand: Boolean = false, includeArmor: Boolean = false): List<StoredStack?> {
        check(Bukkit.isPrimaryThread())
        require(!includeArmor || includeOffHand)
        val items = player.inventory.storageContents.toList() +
            (if (includeArmor) player.inventory.armorContents.toList() else emptyList()) +
            (if (includeOffHand) listOf(player.inventory.itemInOffHand) else emptyList())
        return items.map { item ->
            if (item == null || item.type.isAir) null else StoredStack(payload(item), item.amount,
                minOf(item.maxStackSize, player.inventory.maxStackSize))
        }
    }
}

class PaperDeliveryInventory(
    private val player: Player, private val checkpoint: NamespacedKey, private val files: PlayerCheckpointFile,
    private val includeOffHand: Boolean = false,
    private val includeArmor: Boolean = false,
) : DeliveryInventory {
    override fun stackLimit(): Int { checkMainThread(); return player.inventory.maxStackSize }
    override fun snapshot(): List<StoredStack?> {
        checkMainThread()
        return PaperItemCodec.snapshot(player, includeOffHand, includeArmor)
    }

    override fun persistedCheckpoint(): UUID? { checkMainThread(); return files.read(player.uniqueId) }

    override fun applyAndCheckpoint(before: List<StoredStack?>, after: List<StoredStack?>, id: UUID) {
        checkMainThread()
        check(player.isOnline && !player.isDead) { "Player is unavailable" }
        check(snapshot() == before) { "Inventory changed before delivery" }
        val storageSize = player.inventory.storageContents.size
        check(after.size == storageSize + (if (includeArmor) 4 else 0) + (if (includeOffHand) 1 else 0))
        // Decode everything before touching a slot, so a malformed payload cannot cause a partial replacement.
        val contents = after.map { stack -> stack?.let { PaperItemCodec.item(it.payload, it.count) } }.toTypedArray()
        // Verify the configured directory and save path before any physical item changes.
        val probe = UUID.randomUUID()
        player.persistentDataContainer.set(checkpoint, PersistentDataType.STRING, probe.toString())
        player.saveData()
        files.confirm(player.uniqueId, probe)
        check(snapshot() == before) { "Inventory changed during save preflight" }
        player.inventory.storageContents = contents.take(storageSize).toTypedArray()
        if (includeArmor) player.inventory.armorContents = contents.slice(storageSize until storageSize + 4).toTypedArray()
        if (includeOffHand) player.inventory.setItemInOffHand(contents.last())
        player.persistentDataContainer.set(checkpoint, PersistentDataType.STRING, id.toString())
        player.saveData()
        files.confirm(player.uniqueId, id)
        check(snapshot() == after) { "Inventory serialization changed during delivery" }
    }

    private fun checkMainThread() { check(Bukkit.isPrimaryThread()) { "Inventory delivery must run on the server thread" } }
}
