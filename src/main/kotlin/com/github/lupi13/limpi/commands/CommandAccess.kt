package com.github.lupi13.limpi.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandAccess {
    fun route(sender: CommandSender, root: String, input: Array<out String>, index: CommandIndex, enabled: Boolean): Pair<CommandRoute, Array<String>>? {
        val args = if (input.isEmpty()) arrayOf("help") else input.toList().toTypedArray()
        val route = index.resolve(args.toList())
        val error = when {
            route == null -> "알 수 없는 명령입니다. /$root help를 확인하세요."
            !enabled && !route.recovery -> "해당 시스템이 비활성화되어 있습니다. /$root help에서 복구 명령을 확인하세요."
            route.permission != null && !sender.hasPermission(route.permission) -> "권한이 부족합니다!"
            route.playerOnly && sender !is Player -> "플레이어만 사용할 수 있습니다. /$root help에서 콘솔 명령을 확인하세요."
            args.size !in route.minimum..route.maximum -> "잘못된 형식입니다. /$root ${route.usage}"
            else -> null
        }
        if (error != null) { sender.sendMessage(Component.text(error, NamedTextColor.RED)); return null }
        return requireNotNull(route) to args
    }
}
