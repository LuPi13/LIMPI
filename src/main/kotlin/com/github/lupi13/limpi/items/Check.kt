package com.github.lupi13.limpi.items

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.google.common.collect.Lists
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import kotlin.math.absoluteValue

class Check: Listener {
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
        fun isCheck(item: ItemStack): Boolean {
            try {
                val lore = item.lore()!!
                val name = PlainTextComponentSerializer.plainText().serialize(item.displayName())
                val ask = name.substring(1, name.length - 5)
                val lore1 = PlainTextComponentSerializer.plainText().serialize(lore[1])
                val issuer = lore1.substring(5, lore1.length)
                val lore2 = PlainTextComponentSerializer.plainText().serialize(lore[2])
                val date = lore2.substring(5, lore2.length)
                val lore3 = PlainTextComponentSerializer.plainText().serialize(lore[3])
                val key = lore3.substring(5, lore3.length)
                val lore4 = PlainTextComponentSerializer.plainText().serialize(lore[4])
                val serial = lore4.substring(5, lore4.length)
                val hash = hashing(ask, issuer, date, key)
                if (serial != hash.toString()) {
                    return false
                }
            } catch (e: Exception) {
                return false
            }
        return true
        }
        fun getCheck(ask: Long, issuer: Player): ItemStack {
            val item = ItemStack(Material.PAPER, 1)
            item.amount = 1
            val meta: ItemMeta? = item.itemMeta
            meta?.itemName(Component.text("${ask}원 수표", NamedTextColor.WHITE, TextDecoration.ITALIC))
            val date = Functions.getDateTime()
            val key = Math.random().toString().substring(2, 10)
            val hash = hashing(ask.toString(), issuer.name, date, key)
            val lore = Lists.newArrayList<Component>()

            lore.add(Component.text("우클릭하여 계좌에 해당 금액을 추가합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text("발행인: ", NamedTextColor.WHITE).append(Component.text(issuer.name, NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text("발행일: ", NamedTextColor.WHITE).append(Component.text(date, NamedTextColor.BLUE)).decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text("공개키: ", NamedTextColor.WHITE).append(Component.text(key, NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text("시리얼: ", NamedTextColor.WHITE).append(Component.text(hash.toString(), NamedTextColor.GRAY, TextDecoration.UNDERLINED)).decoration(TextDecoration.ITALIC, false))
            meta?.lore(lore)
            item.itemMeta = meta

            return item
        }
    }

    /**
     * 수표 사용: 우클릭
     */
    @EventHandler
    fun useCheck(event: PlayerInteractEvent) {
        val player = event.player
        val item: ItemStack? = event.item
        if ((item?.type == Material.PAPER)
            // 도대체 왜 displayname은 serialize하면 []안에 집어넣어서 반환하냐 이거때문에 뭔지도 모르고 개뺑이 침
            && PlainTextComponentSerializer.plainText().serialize(item.displayName()).endsWith("원 수표]")
            && (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR)) {
            if (!isCheck(item)) {
                player.playSound(player.location, Sound.BLOCK_VAULT_REJECT_REWARDED_PLAYER, 1F, 2F)
                player.sendMessage(NamedTextColor.RED.toString() + "수표가 유효하지 않습니다.")
                return
            }
            val customName = PlainTextComponentSerializer.plainText().serialize(item.displayName())
            val value = customName.substring(1, customName.length - 5)
            val config = FileManager.getPlayerData(player)
            config["money"] = config.getLong("money") + value.toLong()
            FileManager.savePlayerData(player, config)
            item.amount -= 1
            player.sendMessage(playerDisplay(player)
                .append(Component.text("님의 계좌에 ", NamedTextColor.WHITE))
                .append(moneyDisplay(value))
                .append(Component.text("원을 추가했습니다.", NamedTextColor.WHITE)))
            player.playSound(player, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
        }
    }
}