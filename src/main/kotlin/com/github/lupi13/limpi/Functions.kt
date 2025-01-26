package com.github.lupi13.limpi

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Functions {
    companion object {
        private var plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)
        val allMaterials = Material.entries.map { it.name.lowercase(Locale.getDefault()) }

        fun getOnlinePlayers(): List<Player> {
            var playerList = mutableListOf<Player>()
            plugin.server.onlinePlayers.forEach {playerList.add(it)}

            return playerList
        }

        fun getPlayers(): List<Player> {
            val playerList = mutableListOf<Player>()
            getOnlinePlayers().forEach {playerList.add(it)}
            plugin.server.offlinePlayers.forEach {playerList.add(it as Player)}

            return playerList
        }

        fun getAllPlayers(): List<Player> {
            var playerList = mutableListOf<Player>()
            plugin.server.offlinePlayers.forEach {playerList.add(it as Player)}

            return playerList
        }

        fun toMaterial(string: String): Material {
            for (material in Material.entries) {
                if (string.equals(material.name, ignoreCase = true)) {
                    return material
                }
            }
            return Material.AIR
        }


        /**
         * 인벤토리에 해당 아이템을 추가할 수 있는 지 확인
         * @param[player] 플레이어
         * @param[item] 아이템
         * @return 인벤토리 index as int, null if full
         */
        fun validInventoryIndex(player: Player, item: ItemStack): Int? {
            val inventory = player.inventory
            var count = item.amount
            for (i in 0..35) {
                if (inventory.getItem(i) == null) {
                    return i
                }
                if (inventory.getItem(i)!!.isSimilar(item)) {
                    if (inventory.getItem(i)!!.amount + count <= inventory.getItem(i)!!.maxStackSize) {
                        return i
                    }
                    else {
                        count -= inventory.getItem(i)!!.maxStackSize - inventory.getItem(i)!!.amount
                    }
                }
            }
            return null
        }

        /**
         * 현재 날짜 및 시간 출력
         */
        fun getDateTime(): String {
            val now = Calendar.getInstance()
            return "${now.get(Calendar.YEAR)}-${now.get(Calendar.MONTH) + 1}-${now.get(Calendar.DATE)} ${now.get(Calendar.HOUR_OF_DAY)}:${now.get(Calendar.MINUTE)}:${now.get(Calendar.SECOND)}"
        }
    }
}