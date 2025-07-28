package com.github.lupi13.limpi

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
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
            val playerList = mutableListOf<Player>()
            plugin.server.onlinePlayers.forEach {playerList.add(it)}

            return playerList
        }

        fun getAllPlayers(): List<OfflinePlayer> {
            val playerList = mutableListOf<OfflinePlayer>()
            plugin.server.onlinePlayers.forEach { playerList.add(it) }
            plugin.server.offlinePlayers.forEach { playerList.add(it) }

            return playerList
        }

        fun getAllPlayersName(): List<String> {
            return getAllPlayers().map { it.name!! }
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
         * 인벤토리에 해당 아이템이 몇개 있는지 확인
         * @param[player] 플레이어
         * @param[item] 아이템
         * @return 아이템 개수 as int
         */
        fun getInventoryItemCount(player: Player, item: ItemStack): Int {
            var count = 0
            for (inventoryItem in player.inventory) {
                if (inventoryItem != null && inventoryItem.isSimilar(item)) {
                    count += inventoryItem.amount
                }
            }
            return count
        }


        /**
         * 인벤토리에서 해당 아이템을 제거, 반드시 getInventoryItemCount로 개수를 확인한 후 사용할 것
         * @param[player] 플레이어
         * @param[item] 아이템
         * @param[count] 제거할 아이템 개수
         */
        fun removeInventoryItem(player: Player, item: ItemStack, count: Int) {
            var remaining = count
            for (inventoryItem in player.inventory) {
                if (inventoryItem != null && inventoryItem.isSimilar(item)) {
                    if (inventoryItem.amount > remaining) {
                        inventoryItem.amount -= remaining
                        return
                    } else {
                        remaining -= inventoryItem.amount
                        inventoryItem.amount = 0
                    }
                }
            }
        }

        /**
        * 현재 날짜 및 시간 출력
         */
        fun getDateTime(): String {
            val now = Calendar.getInstance()
            return "${now[Calendar.YEAR]}-${now[Calendar.MONTH] + 1}-${now[Calendar.DATE]} ${now[Calendar.HOUR_OF_DAY]}:${now[Calendar.MINUTE]}:${now[Calendar.SECOND]}"
        }


        /**
         * 이름 표시 앞에 연두색, 뒤에 흰색 넣어줌
         */
        fun playerDisplay(player: Player): Component {
            return Component.text(player.name, NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("", NamedTextColor.WHITE)).decoration(TextDecoration.BOLD, false)
        }
        fun playerDisplay(player: String): Component {
            return Component.text(player, NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("", NamedTextColor.WHITE)).decoration(TextDecoration.BOLD, false)
        }

        /**
         * 돈 표시 앞에 금색, 뒤에 흰색 넣어줌
         */
        fun moneyDisplay(value: Long): Component {
            return Component.text(value, NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("", NamedTextColor.WHITE)).decoration(TextDecoration.BOLD, false)
        }
        fun moneyDisplay(value: Double): Component {
            return Component.text(value, NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("", NamedTextColor.WHITE)).decoration(TextDecoration.BOLD, false)
        }
        fun moneyDisplay(value: Int): Component {
            return Component.text(value, NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("", NamedTextColor.WHITE)).decoration(TextDecoration.BOLD, false)
        }
        fun moneyDisplay(value: String): Component {
            return Component.text(value, NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("", NamedTextColor.WHITE)).decoration(TextDecoration.BOLD, false)
        }

        val defaultAttributes: MutableMap<Attribute, Double> = mutableMapOf(
            Attribute.MAX_HEALTH to 20.0,
            Attribute.ATTACK_DAMAGE to 1.0,
            Attribute.MOVEMENT_SPEED to 0.1,
            Attribute.ARMOR to 0.0,
            Attribute.ARMOR_TOUGHNESS to 0.0,
            Attribute.JUMP_STRENGTH to 0.42,
            Attribute.GRAVITY to 0.08,
            Attribute.SCALE to 1.0,
        )
        /**
         * 플레이어의 attributes를 초기값으로 설정
         * @param[player] 플레이어
         */
        fun resetPlayerAttributes(player: Player) {

            for (attribute in defaultAttributes.keys) {
                player.getAttribute(attribute)?.baseValue = defaultAttributes[attribute] ?: continue
            }
        }
    }
}