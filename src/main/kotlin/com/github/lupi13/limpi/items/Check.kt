package com.github.lupi13.limpi.items

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.checks.*
import com.github.lupi13.limpi.economy.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

class Check(private val plugin: Plugin) : Listener {
    private val identity = NamespacedKey(plugin, "check_id")
    private val format = NamespacedKey(plugin, "check_format")

    fun item(id: UUID, amount: Money, issuerName: String): ItemStack = ItemStack(Material.PAPER).apply {
        val meta = itemMeta
        meta.itemName(Component.text("${amount.units}원 수표", NamedTextColor.WHITE))
        meta.setMaxStackSize(1)
        meta.persistentDataContainer.set(identity, PersistentDataType.STRING, id.toString())
        meta.persistentDataContainer.set(format, PersistentDataType.INTEGER, 1)
        meta.lore(listOf(Component.text("발행인: $issuerName", NamedTextColor.GRAY),
            Component.text("우클릭 또는 /financial check redeem으로 입금", NamedTextColor.YELLOW)))
        itemMeta = meta
    }

    fun id(item: ItemStack?): UUID? {
        if (item == null || item.type != Material.PAPER) return null
        val data = item.itemMeta.persistentDataContainer
        if (data.get(format, PersistentDataType.INTEGER) != 1) return null
        return data.get(identity, PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    fun legacy(item: ItemStack): LegacyCheck? {
        if (item.type != Material.PAPER || id(item) != null) return null
        val meta = item.itemMeta
        val name = meta.itemName()
        val plain = PlainTextComponentSerializer.plainText()
        return LegacyCheck.parse(plain.serialize(name), (meta.lore() ?: emptyList()).map(plain::serialize))
    }

    fun redeem(player: Player, hand: EquipmentSlot = EquipmentSlot.HAND): Boolean {
        if (!player.hasPermission("limpi.financial.check") || FileManager.delivery.blocked(player.uniqueId)) return false
        if (!FileManager.delivery.claim(player)) return false
        val source = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
        val checkId = id(source)
        if (checkId == null) {
            player.sendMessage(Component.text(if (legacy(source) != null) "기존 수표는 관리자 확인 후 전환해야 합니다. 원본은 보존하세요."
                else "손에 유효한 수표를 들고 있어야 합니다.", NamedTextColor.RED))
            return false
        }
        val request = UUID.randomUUID()
        try {
            val result = FileManager.checks.redeem(request, checkId, player.uniqueId)
            player.sendMessage(Component.text("${result.amount}원 입금을 확정했습니다. 거래 UUID: $request", NamedTextColor.GREEN))
            // The server record is already REDEEMED. Cleanup cannot create another spendable claim.
            val current = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
            if (id(current) == checkId) {
                val remaining = current.clone().apply { amount-- }
                if (hand == EquipmentSlot.OFF_HAND) player.inventory.setItemInOffHand(remaining) else player.inventory.setItemInMainHand(remaining)
                player.saveData()
            } else plugin.logger.warning("Redeemed check $checkId was not in its original hand; retaining physical items")
            return true
        } catch (failure: CheckException) {
            player.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: BankException) {
            player.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Check redemption $request failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("거래 기록을 확인해야 합니다. UUID: $request. 같은 수표를 다시 사용해도 중복 입금되지 않습니다.", NamedTextColor.RED))
        }
        return false
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun useCheck(event: PlayerInteractEvent) {
        if (event.action !in setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)) return
        val item = event.item ?: return
        if (id(item) == null && legacy(item) == null) return
        if (event.useItemInHand() == Event.Result.DENY) return
        event.setUseItemInHand(Event.Result.DENY); event.setUseInteractedBlock(Event.Result.DENY)
        redeem(event.player, event.hand ?: EquipmentSlot.HAND)
    }
}
