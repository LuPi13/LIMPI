package com.github.lupi13.limpi.items

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.commands.EconomyText
import com.github.lupi13.limpi.checks.*
import com.github.lupi13.limpi.economy.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
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
        meta.itemName(Component.text("${amount.units}원 수표", NamedTextColor.WHITE, TextDecoration.ITALIC))
        meta.setMaxStackSize(1)
        meta.persistentDataContainer.set(identity, PersistentDataType.STRING, id.toString())
        meta.persistentDataContainer.set(format, PersistentDataType.INTEGER, 1)
        // Display only; redemption authority is the PDC UUID and the server issue record.
        meta.lore(listOf(
            Component.text("우클릭하여 계좌에 해당 금액을 추가합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
            Component.text("발행인: ", NamedTextColor.WHITE).append(Component.text(issuerName, NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false),
            Component.text("발행일: ", NamedTextColor.WHITE).append(Component.text(Functions.getDateTime(), NamedTextColor.BLUE)).decoration(TextDecoration.ITALIC, false),
            Component.text("공개키: ", NamedTextColor.WHITE).append(Component.text(Math.random().toString().substring(2, 10), NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false),
            Component.text("시리얼: ", NamedTextColor.WHITE).append(Component.text(id.hashCode().toString(), NamedTextColor.GRAY, TextDecoration.UNDERLINED)).decoration(TextDecoration.ITALIC, false)))
        itemMeta = meta
    }

    fun id(item: ItemStack?): UUID? {
        if (item == null || item.type != Material.PAPER) return null
        val data = item.itemMeta.persistentDataContainer
        if (data.get(format, PersistentDataType.INTEGER) != 1) return null
        return data.get(identity, PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    fun redeem(player: Player, hand: EquipmentSlot = EquipmentSlot.HAND): Boolean {
        if (!player.hasPermission("limpi.financial.check") || FileManager.delivery.blocked(player.uniqueId)) return false
        if (!FileManager.delivery.claim(player)) return false
        val source = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
        val checkId = id(source)
        if (checkId == null) {
            reject(player)
            return false
        }
        val request = UUID.randomUUID()
        try {
            val result = FileManager.checks.redeem(request, checkId, player.uniqueId)
            player.sendMessage(playerDisplay(player)
                .append(Component.text("님의 계좌에 ", NamedTextColor.WHITE))
                .append(moneyDisplay(result.amount))
                .append(Component.text("원을 추가했습니다.", NamedTextColor.WHITE)))
            player.playSound(player, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
            // The server record is already REDEEMED. Cleanup cannot create another spendable claim.
            val current = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
            if (id(current) == checkId) {
                val remaining = current.clone().apply { amount-- }
                if (hand == EquipmentSlot.OFF_HAND) player.inventory.setItemInOffHand(remaining) else player.inventory.setItemInMainHand(remaining)
                player.saveData()
            } else plugin.logger.warning("Redeemed check $checkId was not in its original hand; retaining physical items")
            return true
        } catch (failure: CheckException) {
            plugin.logger.info("Check $checkId rejected for ${player.uniqueId}: ${failure.reason}")
            reject(player)
        } catch (failure: BankException) {
            EconomyText.bankFailure(player, failure)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Check redemption $request (check $checkId) failed for ${player.uniqueId}", failure)
        }
        return false
    }

    private fun reject(player: Player) {
        player.playSound(player.location, Sound.BLOCK_VAULT_REJECT_REWARDED_PLAYER, 1F, 2F)
        player.sendMessage(Component.text("수표가 유효하지 않습니다.", NamedTextColor.RED))
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun useCheck(event: PlayerInteractEvent) {
        if (event.action !in setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)) return
        val item = event.item ?: return
        if (id(item) == null) {
            // Look-alike paper without a server issue record: same rejection as before, never a deposit.
            if (item.type == Material.PAPER && PlainTextComponentSerializer.plainText().serialize(item.displayName()).endsWith("원 수표]")) {
                event.setUseItemInHand(Event.Result.DENY)
                reject(event.player)
            }
            return
        }
        if (event.useItemInHand() == Event.Result.DENY) return
        event.setUseItemInHand(Event.Result.DENY); event.setUseInteractedBlock(Event.Result.DENY)
        redeem(event.player, event.hand ?: EquipmentSlot.HAND)
    }
}
