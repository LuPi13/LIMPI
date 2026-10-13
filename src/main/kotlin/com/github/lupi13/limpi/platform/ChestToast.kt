package com.github.lupi13.limpi.platform

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * "<item>이(가) 자동 저장되었습니다" as an advancement toast instead of chat. A throwaway, non-persistent advancement is
 * awarded and removed shortly after. The item name is the translatable name, so each client shows it in its own language.
 */
object ChestToast {
    private const val COOLDOWN_MILLIS = 1500L
    private val last = ConcurrentHashMap<UUID, Long>()

    fun show(plugin: Plugin, player: Player, item: ItemStack) {
        val now = System.currentTimeMillis()
        val previous = last[player.uniqueId]
        // Walking over a pile fires many pickups; one toast per moment is enough (every item is still stored).
        if (previous != null && now - previous < COOLDOWN_MILLIS) return
        last[player.uniqueId] = now
        val key = NamespacedKey(plugin, "chest_toast/${UUID.randomUUID()}")
        try {
            val title = GsonComponentSerializer.gson().serialize(item.effectiveName().append(Component.text("이(가) 자동 저장되었습니다")))
            val json = """{"criteria":{"impossible":{"trigger":"minecraft:impossible"}},""" +
                """"display":{"icon":{"id":"${item.type.key.asString()}"},"title":$title,"description":{"text":""},""" +
                """"frame":"task","show_toast":true,"announce_to_chat":false,"hidden":true}}"""
            @Suppress("DEPRECATION")
            val advancement = checkNotNull(Bukkit.getUnsafe().loadAdvancement(key, json, false)) { "Toast advancement was not loaded" }
            player.getAdvancementProgress(advancement).awardCriteria("impossible")
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                try {
                    if (player.isOnline) player.getAdvancementProgress(advancement).revokeCriteria("impossible")
                    @Suppress("DEPRECATION")
                    Bukkit.getUnsafe().removeAdvancement(key)
                } catch (failure: Exception) {
                    plugin.logger.log(Level.WARNING, "Unable to remove chest toast advancement $key", failure)
                }
            }, 10L)
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to show the chest auto store toast to ${player.uniqueId}", failure)
        }
    }

    fun forget(owner: UUID) { last.remove(owner) }
}
