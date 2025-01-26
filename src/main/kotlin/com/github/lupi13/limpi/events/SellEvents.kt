package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

class SellEvents : Listener {
    companion object {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)
        val timer: MutableMap<Player, Int> = HashMap()
        val profit: MutableMap<Player, Int> = HashMap()
        val closeToggle: MutableList<Player> = ArrayList()

        fun getSellPrice(item: ItemStack): Int {
            return when {
                FileManager.getSellPriceConfig().getKeys(false).contains(item.type.toString().lowercase()) -> {
                    FileManager.getSellPriceConfig().getInt(item.type.toString().lowercase()) * item.amount
                }
                else -> item.amount
            }
        }

        val SellGUIName = "${ChatColor.YELLOW}${ChatColor.BOLD}LIMPI: 판매창"

        fun openSellGUI(player: Player) {
            val sellGUI: Inventory = Bukkit.createInventory(player, 54, SellGUIName)
            val close = ItemStack(Material.GOLD_INGOT, 1)
            val meta = close.itemMeta
            val lore: MutableList<String> = ArrayList()
            meta!!.setDisplayName("${ChatColor.RED}판매")
            lore.add("${ChatColor.GOLD}0${ChatColor.WHITE} 원")
            meta.lore = lore
            close.itemMeta = meta
            sellGUI.setItem(53, close)
            player.openInventory(sellGUI)
        }

    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title == SellGUIName) {
            val player = event.whoClicked as Player
            val sellGUI = event.view.topInventory

            if (timer.containsKey(player) && timer[player]!! >= 0) {
                player.sendMessage("${ChatColor.DARK_RED}판매중에 클릭하지 마세요.")
                event.isCancelled = true
            }

            if (event.currentItem != null && event.currentItem!!.hasItemMeta() && event.currentItem!!.itemMeta!!.displayName == "${ChatColor.RED}판매") {
                var count = 0
                for (i in 0..52) {
                    if (sellGUI.getItem(i) != null) {
                        count += 1
                    }
                }
                if (count != 0) {
                    timer[player] = 0
                    val preprofit = profit[player] ?: 0
                    object : BukkitRunnable() {
                        override fun run() {
                            if (timer[player]!! < 0) {
                                player.closeInventory()
                                cancel()
                            }
                            if (timer[player]!! >= 0 && (sellGUI.getItem(timer[player]!!) != null)) {
                                try {
                                    sellGUI.setItem(timer[player]!!, null)
                                    player.playSound(player.location, Sound.BLOCK_CHAIN_BREAK, 0.8f, (Math.random() * 0.4 + 0.9).toFloat())
                                } catch (ignored: ArrayIndexOutOfBoundsException) {
                                    player.closeInventory()
                                    timer[player] = -2
                                    cancel()
                                }
                            }
                            if (timer[player]!! >= 52) {
                                val config = FileManager.getPlayerData(player)
                                config["money"] = config.getLong("money") + preprofit
                                FileManager.savePlayerData(player, config)
                                player.sendMessage("${ChatColor.GOLD}$preprofit${ChatColor.WHITE}원이 계좌에 입금되었습니다.")
                                player.closeInventory()
                                timer[player] = -2
                                cancel()
                            }
                            if (timer[player]!! <= 51) {
                                timer[player] = timer[player]!! + 1
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L)
                } else {
                    player.sendMessage("${ChatColor.RED}아이템을 올려놓고 눌러주세요.")
                }
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title == SellGUIName) {
            val player = event.player as Player
            if (!(timer.containsKey(player) && timer[player]!! >= 0)) {
                for (i in 0..52) {
                    if (event.view.topInventory.getItem(i) != null) {
                        player.inventory.addItem(event.view.topInventory.getItem(i)!!)
                    }
                }
            }
            closeToggle.add(player)
        }
    }
}

fun sellTask(player: Player, inventory: Inventory) {
    object : BukkitRunnable() {
        override fun run() {
            if (SellEvents.closeToggle.contains(player)) {
                SellEvents.closeToggle.remove(player)
                cancel()
            }
            var price = 0
            for (item in inventory) {
                if (item != null) {
                    if (!(item.hasItemMeta() && item.itemMeta!!.displayName == "${ChatColor.RED}판매")) {
                        price += SellEvents.getSellPrice(item)
                    }
                }
            }
            val close = inventory.getItem(53)
            val meta = close!!.itemMeta
            val lore = meta!!.lore
            lore!![0] = "${ChatColor.GOLD}$price${ChatColor.WHITE} 원"
            if (SellEvents.timer.containsKey(player) && SellEvents.timer[player]!! >= 0) {
                if (lore.size <= 1) {
                    lore.add("${ChatColor.YELLOW}처리중")
                } else {
                    val multi = (SellEvents.timer[player]!! / 2) % 3
                    val dot = when (multi) {
                        0 -> "."
                        1 -> ".."
                        2 -> "..."
                        else -> ""
                    }
                    lore[1] = "${ChatColor.YELLOW}처리중${dot}"
                }
            }
            meta.lore = lore
            close.itemMeta = meta
            SellEvents.profit[player] = price
        }
    }.runTaskTimer(SellEvents.plugin, 0L, 1L)
}
