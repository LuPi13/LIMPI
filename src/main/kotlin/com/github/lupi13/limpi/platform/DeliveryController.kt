package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.delivery.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Level

class DeliveryController(private val plugin: Plugin, private val service: DeliveryService) {
    private val checkpoint = NamespacedKey(plugin, "delivery_checkpoint")
    private val directory = plugin.config.getString("PlayerDataDirectory")?.takeIf(String::isNotBlank)?.let { configured ->
        val path = Path.of(configured)
        if (path.isAbsolute) path else plugin.server.worldContainer.toPath().resolve(path)
    } ?: plugin.server.worlds.first().worldFolder.toPath().resolve("playerdata")
    private val files = PlayerCheckpointFile(directory, checkpoint.toString())
    private val blocked = mutableSetOf<UUID>()
    init { plugin.logger.info("Delivery checkpoint directory: ${directory.toAbsolutePath().normalize()}") }

    fun blocked(id: UUID) = id in blocked
    fun release(id: UUID) { blocked.remove(id); service.release(id) }

    fun claim(player: Player, freshLogin: Boolean = false): Boolean {
        if (blocked(player.uniqueId) && !freshLogin) return false
        if (freshLogin) blocked.remove(player.uniqueId)
        try {
            val result = service.drain(player.uniqueId, PaperDeliveryInventory(player, checkpoint, files), freshLogin)
            if (result.delivered > 0) player.sendMessage(Component.text("미수령 물품 ${result.delivered}건을 지급했습니다.", NamedTextColor.GREEN))
            if (result.waitingForSpace > 0) player.sendMessage(Component.text(
                "미수령 물품 ${result.waitingForSpace}건이 남아 있습니다. 공간을 확보한 뒤 /financial claim을 사용하세요.", NamedTextColor.YELLOW))
            return true
        } catch (failure: Exception) {
            blocked.add(player.uniqueId)
            plugin.logger.log(Level.SEVERE, "Delivery recovery failed for ${player.uniqueId}; inventory use is blocked", failure)
            player.kick(Component.text("물품 지급 기록을 확인해야 합니다. 관리자에게 UUID ${player.uniqueId}를 알려주세요.", NamedTextColor.RED))
            return false
        }
    }
}
