package com.github.lupi13.limpi

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.OfflinePlayer
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

        private val materialsByName = Material.entries.associateBy { it.name }

        fun toMaterial(string: String): Material = materialsByName[string.uppercase(Locale.ROOT)] ?: Material.AIR

        /** 능력이 놓는 불도 보호 플러그인이 막을 수 있도록 점화 이벤트를 거친 뒤에만 놓습니다. */
        fun igniteBy(player: Player, block: org.bukkit.block.Block): Boolean {
            if (block.type != Material.AIR) return false
            val event = org.bukkit.event.block.BlockIgniteEvent(block, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player)
            plugin.server.pluginManager.callEvent(event)
            if (event.isCancelled) return false
            block.type = Material.FIRE
            return true
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

        /** Remove only modifiers owned by LIMPI abilities; preserve server and other plugin attributes. */
        fun resetPlayerAttributes(player: Player) {
            com.github.lupi13.limpi.platform.AbilityRuntime.clearAttributes(player)
        }
    }
}
