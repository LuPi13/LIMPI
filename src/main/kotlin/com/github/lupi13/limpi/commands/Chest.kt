package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.chest.ChestUnavailable
import com.github.lupi13.limpi.events.ChestEvents
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

class Chest(private val plugin: Plugin, private val enabled: Boolean) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        // A bare /chest opens the chest; every other path goes through the shared definitions.
        val (route, args) = CommandAccess.route(sender, "chest", if (input.isEmpty()) arrayOf("open") else input, CommandDefinitions.chest, enabled) ?: return true
        try {
            when (route.id) {
                "help" -> if (sender is Player && enabled) helpBook(sender)
                    else CommandHelp.show(sender, "chest", CommandDefinitions.chest, enabled, web = WebHelp.links(plugin, sender))
                "open" -> {
                    val player = sender as Player
                    if (FileManager.chests.state(player.uniqueId).unlocked) ChestEvents.open(player)
                    else if (enabled && player.hasPermission("limpi.chest.shop")) ChestEvents.openShop(player)
                    else player.sendMessage(Component.text("상자를 해금하지 않았습니다.", NamedTextColor.RED))
                }
                "shop" -> ChestEvents.openShop(sender as Player)
                "auto" -> {
                    val player = sender as Player
                    val requested = when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
                        null -> null; "on" -> true; "off" -> false
                        else -> throw IllegalArgumentException("잘못된 형식입니다. /chest auto [on|off]")
                    }
                    val on = FileManager.chests.setAuto(player.uniqueId, requested)
                    player.sendMessage(Component.text("자동 저장이 ", NamedTextColor.WHITE)
                        .append(if (on) Component.text("켜졌습니다", NamedTextColor.GREEN) else Component.text("꺼졌습니다", NamedTextColor.RED))
                        .append(Component.text(".", NamedTextColor.WHITE)))
                }
                "inspect" -> inspect(sender, args[1])
                "resolve" -> resolve(sender, args)
                else -> error("Unbound chest command ${route.id}")
            }
        } catch (failure: ChestUnavailable) {
            sender.sendMessage(Component.text(failure.message ?: "상자를 사용할 수 없습니다.", NamedTextColor.RED))
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "입력을 확인하세요.", NamedTextColor.RED))
        } catch (failure: IllegalStateException) {
            plugin.logger.log(Level.WARNING, "Chest command ${route.id} from ${sender.name} refused", failure)
            sender.sendMessage(Component.text("처리 중인 다른 아이템 기록이 있어 지금은 사용할 수 없습니다. 잠시 후 다시 시도하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Chest command ${route.id} from ${sender.name} failed", failure)
            throw failure // As elsewhere: Bukkit reports an internal command error to the sender.
        }
        return true
    }

    private fun inspect(sender: CommandSender, target: String) {
        val owner = runCatching { UUID.fromString(target) }.getOrNull() ?: FileManager.players.findByName(target)
            ?: throw IllegalArgumentException("해당 플레이어를 찾을 수 없습니다!")
        val name = runCatching { FileManager.players.profile(owner).name }.getOrNull() ?: target
        val state = FileManager.chests.state(owner)
        FileManager.chests.pending(owner).forEach {
            sender.sendMessage("미해결 창 기록 ${it.id}: ${it.state}${it.reason?.let { reason -> " ($reason)" }.orEmpty()}")
        }
        if (!state.unlocked) { sender.sendMessage("$name($owner): 상자를 해금하지 않았습니다."); return }
        val used = state.slots.filterNotNull()
        sender.sendMessage("$name($owner): ${state.rows}줄(${state.rows * 9}칸), ${used.size}칸 사용, " +
            "자동 저장 ${if (!state.autoOwned) "미구매" else if (state.autoEnabled) "켜짐" else "꺼짐"}, 엔더화 ${if (state.ender) "구매" else "미구매"}")
        used.groupBy { runCatching { PaperItemCodec.item(it.payload, 1).type.name }.getOrDefault("UNKNOWN") }
            .forEach { (type, stacks) -> sender.sendMessage("  $type x${stacks.sumOf { it.count }}") }
    }

    private fun resolve(sender: CommandSender, args: Array<String>) {
        val owner = UUID.fromString(args[1]); val id = UUID.fromString(args[2])
        val applyAfter = when (args[3].lowercase(Locale.ROOT)) {
            "after" -> true; "before" -> false
            else -> throw IllegalArgumentException("before 또는 after를 입력하세요.")
        }
        require(plugin.server.getPlayer(owner) == null) { "소유자가 오프라인일 때 저장 상태를 확인하세요." }
        val request = UUID.randomUUID()
        val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
        FileManager.chests.resolve(request, actor, owner, id, applyAfter, args.drop(4).joinToString(" "))
        sender.sendMessage("상자 창 기록 ${id}을(를) ${if (applyAfter) "닫은 뒤" else "열기 전"} 상태로 정리했습니다. 감사 거래: $request")
    }

    /** Same look as the /financial help book. */
    private fun helpBook(player: Player) {
        val routes = CommandDefinitions.chest.visible(enabled, true, player::hasPermission)
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta
        meta.title = "/chest 도움말"
        meta.author = "LIMPI"
        fun cmd(usage: String) = Component.text(usage, NamedTextColor.GREEN)
        fun desc(text: String) = Component.text(text, NamedTextColor.BLACK)

        var page = Component.text("\n\n[/chest 도움말]\n", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
        page = page.append(Component.text("/ch 로도 입력할 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
        page = page.append(WebHelp.component(WebHelp.links(plugin, player)))
        meta.addPages(page)

        page = cmd("/chest").append(desc(": 상자를 엽니다. 아직 해금하지 않았다면 상자 상점을 엽니다.\n\n"))
            .append(cmd("/chest help")).append(desc(": 이 도움말을 보여줍니다.\n\n"))
        if (routes.any { it.id == "shop" }) page = page.append(cmd("/chest shop")).append(desc(": 돈으로 상자 해금, 업그레이드, 자동 저장, 엔더화를 구매하는 창을 엽니다."))
        meta.addPages(page)

        if (routes.any { it.id == "auto" }) meta.addPages(cmd("/chest auto [on|off]")
            .append(desc(": 자동 저장을 켜거나 끕니다. 생략하면 켜짐/꺼짐을 바꿉니다.\n\n"))
            .append(desc("자동 저장이 켜져 있으면 인벤토리가 가득 찬 상태에서 주운 아이템이 상자에 들어갑니다.")))

        ChestEvents.prices()?.let { prices ->
            fun price(label: String, units: Long) = desc("$label: ").append(moneyDisplay(units)).append(desc("원\n"))
            var list = Component.text("상자 상점 가격\n", NamedTextColor.DARK_AQUA).append(price("해금(1줄)", prices.unlock.units))
            prices.upgrades.forEachIndexed { index, money -> list = list.append(price("${index + 2}줄", money.units)) }
            meta.addPages(list.append(price("자동 저장", prices.autoStore.units)).append(price("엔더화", prices.ender.units)))
        }
        meta.addPages(Component.text("엔더화\n", NamedTextColor.DARK_AQUA)
            .append(desc("엔더화하지 않은 상자는 죽으면 안의 아이템을 모두 떨어뜨립니다. 엔더화하면 떨어뜨리지 않습니다.\n\n"))
            .append(desc("keepInventory 게임룰이 켜져 있으면 엔더화하지 않아도 떨어뜨리지 않습니다.\n\n"))
            .append(desc("구매한 기능은 환불되지 않습니다.")))

        val admin = routes.filter { it.permission?.contains(".admin.") == true }
        if (admin.isNotEmpty()) {
            page = Component.text("이하 관리자 전용 명령어\n", NamedTextColor.RED)
            for (route in admin) page = page.append(cmd("/chest ${route.usage}").decoration(TextDecoration.BOLD, false)).append(desc(": ${route.description}\n\n"))
            meta.addPages(page)
        }
        book.itemMeta = meta
        player.openBook(book)
    }
}
