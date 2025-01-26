package com.github.lupi13.limpi.items

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

class Check(ask: Long, issuer: Player?): ItemStack(), Listener {
    companion object {
        fun hashing(vararg data: String?): Long {
            var hash: Long = 0
            for (str in data) {
                hash = hash xor str.hashCode().toLong()
                hash *= 127
            }
            hash = hash.absoluteValue
            return hash
        }
        fun isCheck(item: ItemStack?): Boolean {
            try {
                val lore = item?.itemMeta?.lore
                val ask = item?.itemMeta?.displayName?.substring(0, item.itemMeta?.displayName?.length!! - 4)
                val issuer = lore?.get(1)?.substring(9, lore[1].length)
                val date = lore?.get(2)?.substring(9, lore[2].length)
                val key = lore?.get(3)?.substring(9, lore[3].length)
                val serial = lore?.get(4)?.substring(11, lore[4].length)
                val hash = hashing(ask, issuer, date, key)
                if (serial != hash.toString()) {
                    return false
                }
            } catch (e: Exception) {
                return false
            }
        return true
        }
    }
    init {
        this.type = Material.PAPER
        this.amount = 1
        val meta: ItemMeta? = this.itemMeta
        meta?.setDisplayName("${ask}원 수표")
        val date = Functions.getDateTime()
        val key = Math.random().toString().substring(2, 10)
        val hash = hashing(ask.toString(), issuer?.displayName, date, key)


        val lore: List<String> = listOf(ChatColor.WHITE.toString() + "우클릭하여 계좌에 해당 금액을 추가합니다.",
            ChatColor.WHITE.toString() + "발행인: " + ChatColor.GREEN.toString() + (issuer?.displayName),
            ChatColor.WHITE.toString() + "발행일: " + ChatColor.BLUE.toString() + date,
            ChatColor.WHITE.toString() + "공개키: " + ChatColor.GRAY.toString() + key,
            ChatColor.WHITE.toString() + "시리얼: " + ChatColor.UNDERLINE.toString() + hash)
        meta?.lore = lore
        this.itemMeta = meta
    }

    /**
     * 수표 사용: 우클릭
     */
    @EventHandler
    public fun useCheck(event: PlayerInteractEvent) {
        val player = event.player
        val item: ItemStack? = event.item
        if (item?.type == Material.PAPER && item.itemMeta?.displayName?.endsWith("원 수표") == true && (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR)) {
            if (!isCheck(item)) {
            player.playSound(player.location, Sound.BLOCK_VAULT_REJECT_REWARDED_PLAYER, 1F, 2F)
                player.sendMessage(ChatColor.RED.toString() + "수표가 유효하지 않습니다.")
                return
            }
            val displayName = item.itemMeta!!.displayName
            val value = displayName.substring(0, item.itemMeta?.displayName?.length!! - 4)
            val config = FileManager.getPlayerData(player)
            config["money"] = config.getLong("money") + value.toLong()
            FileManager.savePlayerData(player, config)
            item.amount -= 1
            player.sendMessage(ChatColor.GREEN.toString() + player.displayName + ChatColor.WHITE + "님의 계좌에 " + ChatColor.GOLD + value + ChatColor.WHITE + "원을 추가했습니다.")
            player.playSound(player, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
        }
    }
}