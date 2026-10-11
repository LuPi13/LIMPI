package com.github.lupi13.limpi.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandAccess {
    /** Sub-commands directly under a route, in definition order (the original "[set/remove]" hint). */
    private fun children(index: CommandIndex, route: CommandRoute): List<String> = index.routes
        .filter { it.path.size == route.path.size + 1 && it.path.take(route.path.size) == route.path && !it.recovery }
        .map { it.path.last() }.distinct()

    fun route(sender: CommandSender, root: String, input: Array<out String>, index: CommandIndex, enabled: Boolean): Pair<CommandRoute, Array<String>>? {
        val args = if (input.isEmpty()) arrayOf("help") else input.toList().toTypedArray()
        val route = index.resolve(args.toList())
        val error = when {
            route == null -> "알 수 없는 명령입니다. /$root help를 확인하세요."
            !enabled && !route.recovery -> "해당 시스템이 비활성화되어 있습니다. /$root help에서 복구 명령을 확인하세요."
            route.permission != null && !sender.hasPermission(route.permission) -> "권한이 부족합니다!"
            route.playerOnly && sender !is Player -> "플레이어만 사용할 수 있습니다. /$root help에서 콘솔 명령을 확인하세요."
            args.size > route.maximum && children(index, route).isNotEmpty() ->
                "잘못된 형식입니다. /$root ${route.path.joinToString(" ")} [${children(index, route).joinToString("/")}]"
            args.size !in route.minimum..route.maximum -> "잘못된 형식입니다. /$root ${route.usage}"
            else -> null
        }
        if (error != null) { sender.sendMessage(Component.text(error, NamedTextColor.RED)); return null }
        return requireNotNull(route) to args
    }
}
