package com.github.lupi13.limpi.items

import com.github.lupi13.limpi.FileManager
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

class Check(ask: Long, issuer: Player?): ItemStack(), Listener {
    companion object {
        val namePrefix = ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "금 "
        fun isCheck(item: ItemStack?): Boolean {
            if (item?.type != Material.PAPER) {
                return false
            }
            else if (item.itemMeta?.displayName?.startsWith(namePrefix) != true) {
                return false
            }
            return true
        }
    }
    init {
        this.type = Material.PAPER
        this.amount = 1
        var meta: ItemMeta? = this.itemMeta
        meta?.setDisplayName(namePrefix + "${ask}원 수표")
        val lore: List<String> = listOf(ChatColor.GREEN.toString() + (issuer?.displayName) + ChatColor.WHITE.toString() + " 발행",
            ChatColor.WHITE.toString() + "우클릭하여 계좌에 " + ChatColor.GOLD.toString() + ask + ChatColor.WHITE.toString() + "원을 추가합니다.")
        meta?.lore = lore
        this.itemMeta = meta
    }

    /**
     * 수표 사용: 우클릭
     */
    @EventHandler
    public fun useCheck(event: PlayerInteractEvent) {
        val player = event.player
        var item: ItemStack? = event.item
        if (isCheck(item) && item != null && (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR)) {
            val displayName = item.itemMeta!!.displayName
            val value = displayName.substring(6, displayName.length - 4)
            var config = FileManager.getPlayerData(player)
            config["money"] = config.getLong("money") + value.toLong()
            FileManager.savePlayerData(player, config)
            item.amount -= 1
            player.sendMessage(ChatColor.GREEN.toString() + player.displayName + ChatColor.WHITE + "님의 계좌에 " + ChatColor.GOLD + value + ChatColor.WHITE + "원을 추가했습니다.")
            player.playSound(player.location, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
        }
    }
}