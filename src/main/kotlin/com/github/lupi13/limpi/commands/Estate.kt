package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.estate.EstateUnavailable
import com.github.lupi13.limpi.events.EstateEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

class Estate(private val plugin: Plugin, private val enabled: Boolean) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "estate", input, CommandDefinitions.estate, enabled) ?: return true
        try {
            when (route.id) {
                "help", "help admin" -> CommandHelp.show(plugin, sender, "estate", CommandDefinitions.estate, enabled, admin = route.adminHelp)
                "select" -> EstateEvents.toggle(sender as Player)
                "full" -> EstateEvents.toggleFull(sender as Player)
                "accept" -> {
                    if (!plugin.config.getBoolean("EnableFinancialSystem")) throw EstateUnavailable("금융 시스템이 꺼져 있어 지금은 구매할 수 없습니다.")
                    EstateEvents.accept(sender as Player, args[1])
                }
                "deny" -> EstateEvents.deny(sender as Player)
                "overlaps" -> EstateEvents.showOverlaps(sender as Player)
                "show" -> show(sender as Player, args.getOrNull(1))
                "list" -> list(sender as Player)
                "where" -> where(sender as Player)
                "sell" -> sell(sender as Player, args[1], args.getOrNull(2))
                "trust", "untrust" -> trust(sender as Player, args[1], args[2], route.id == "trust")
                "inspect" -> inspect(sender, args[1])
                "remove" -> remove(sender, args)
                else -> error("Unbound estate command ${route.id}")
            }
        } catch (failure: EstateUnavailable) {
            sender.sendMessage(Component.text(failure.message ?: "사유지를 사용할 수 없습니다.", NamedTextColor.RED))
        } catch (failure: BankException) {
            sender.sendMessage(Component.text(failure.message ?: "계좌를 확인하세요.", NamedTextColor.RED))
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "입력을 확인하세요.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Estate command ${route.id} from ${sender.name} failed", failure)
            throw failure // As elsewhere: Bukkit reports an internal command error to the sender.
        }
        return true
    }

    private fun own(player: Player, name: String) = FileManager.estates.also { it.requireUsable(player.uniqueId) }.byName(player.uniqueId, name)
        ?: throw IllegalArgumentException("$name 은(는) 존재하지 않는 사유지이름입니다.")

    private fun notFound(player: Player, name: String) {
        player.sendMessage(Component.text(name, NamedTextColor.YELLOW).append(Component.text("은(는) 존재하지 않는 사유지이름입니다.", NamedTextColor.RED)))
    }

    private fun show(player: Player, name: String?) {
        if (name == null) { EstateEvents.showSelection(player); return }
        FileManager.estates.requireUsable(player.uniqueId)
        val estate = FileManager.estates.byName(player.uniqueId, name)
            ?: FileManager.estates.shared(player.uniqueId).firstOrNull { it.name.equals(name, true) }
            ?: return notFound(player, name)
        EstateEvents.show(player, listOf(estate.region))
        player.sendMessage(Component.text(estate.name, NamedTextColor.GREEN).append(Component.text(" 지역에 반짝이는 효과가 나타납니다.", NamedTextColor.WHITE)))
    }

    private fun line(estate: com.github.lupi13.limpi.estate.Estate, owned: Boolean): Component {
        var text = Component.text(estate.name, NamedTextColor.GREEN)
            .append(Component.text(" ${estate.worldName} ${estate.region.describe()} ", NamedTextColor.WHITE))
            .append(Component.text("${estate.region.volume}칸", NamedTextColor.AQUA))
        if (!owned) text = text.append(Component.text(" (${EstateEvents.ownerName(estate.owner)}님)", NamedTextColor.GRAY))
        text = text.append(Component.text(" [보기]", NamedTextColor.LIGHT_PURPLE).clickEvent(ClickEvent.runCommand("/estate show ${estate.name}")))
        if (owned) text = text.append(Component.text(" [매각]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/estate sell ${estate.name}")))
        return text
    }

    private fun list(player: Player) {
        FileManager.estates.requireUsable(player.uniqueId)
        val owned = FileManager.estates.owned(player.uniqueId)
        val max = EstateEvents.settings()?.maxCount?.toString() ?: "?"
        player.sendMessage(Component.text("━━━━━ 내 사유지 (${owned.size}/$max) ━━━━━", NamedTextColor.WHITE))
        if (owned.isEmpty()) player.sendMessage(Component.text("가진 사유지가 없습니다. /estate select로 구역을 선택해 살 수 있습니다.", NamedTextColor.GRAY))
        owned.forEach { player.sendMessage(line(it, true)) }
        val shared = FileManager.estates.shared(player.uniqueId)
        if (shared.isNotEmpty()) {
            player.sendMessage(Component.text("━━━━━ 공유받은 사유지 ━━━━━", NamedTextColor.WHITE))
            shared.forEach { player.sendMessage(line(it, false)) }
        }
    }

    private fun where(player: Player) {
        val location = player.location
        val estate = FileManager.estates.at(player.world.uid, location.blockX, location.blockY, location.blockZ)
        if (estate == null) { player.sendMessage(Component.text("이 지역은 사유지가 아닙니다.", NamedTextColor.WHITE)); return }
        var text = Component.text("이 지역은 ", NamedTextColor.YELLOW).append(Component.text(EstateEvents.ownerName(estate.owner), NamedTextColor.GREEN))
            .append(Component.text("님의 사유지 ", NamedTextColor.YELLOW)).append(Component.text(estate.name, NamedTextColor.GREEN))
            .append(Component.text("입니다.", NamedTextColor.YELLOW))
        if (estate.members.isNotEmpty()) text = text.append(Component.text(" 공유: ${estate.members.joinToString(", ") { EstateEvents.ownerName(it) }}", NamedTextColor.GRAY))
        player.sendMessage(text)
    }

    /** sell <name> shows the refund with a confirm button; the button adds the shown amount, which must still match. */
    private fun sell(player: Player, name: String, confirm: String?) {
        FileManager.estates.requireUsable(player.uniqueId)
        val estate = FileManager.estates.byName(player.uniqueId, name) ?: return notFound(player, name)
        val settings = EstateEvents.settings() ?: throw EstateUnavailable("사유지 설정에 오류가 있어 지금은 매각할 수 없습니다.")
        val refund = FileManager.estates.refund(estate, settings)
        if (confirm == null) {
            player.sendMessage(Component.text(estate.name, NamedTextColor.GREEN)
                .append(Component.text("을(를) 매각하면 구매가 ${estate.paid.units}원의 ${settings.refundPercent}%인 ", NamedTextColor.WHITE))
                .append(Component.text("${refund.units}원", NamedTextColor.GOLD)).append(Component.text("을 돌려받습니다. ", NamedTextColor.WHITE))
                .append(Component.text("[매각 확인]", NamedTextColor.RED)
                    .hoverEvent(HoverEvent.showText(Component.text("/estate sell ${estate.name}")))
                    .clickEvent(ClickEvent.runCommand("/estate sell ${estate.name} ${refund.units}"))))
            return
        }
        val shown = confirm.toLongOrNull() ?: throw IllegalArgumentException("잘못된 형식입니다. /estate sell <사유지이름>")
        val (sold, amount) = FileManager.estates.sell(UUID.randomUUID(), player.uniqueId, estate.id, shown, settings)
        player.sendMessage(Component.text(sold.name, NamedTextColor.GREEN).append(Component.text("이(가) ", NamedTextColor.WHITE))
            .append(Component.text("${amount.units}원", NamedTextColor.GOLD)).append(Component.text("에 매각되었습니다.", NamedTextColor.WHITE)))
    }

    private fun trust(player: Player, name: String, target: String, trusted: Boolean) {
        val estate = own(player, name)
        val member = runCatching { UUID.fromString(target) }.getOrNull() ?: FileManager.players.findByName(target)
            ?: throw IllegalArgumentException("해당 플레이어를 찾을 수 없습니다!")
        val changed = FileManager.estates.setMember(player.uniqueId, estate.id, member, trusted)
        val who = Component.text(EstateEvents.ownerName(member), NamedTextColor.GREEN)
        player.sendMessage(when {
            !changed && trusted -> who.append(Component.text("님은 이미 ", NamedTextColor.WHITE)).append(Component.text(estate.name, NamedTextColor.GREEN))
                .append(Component.text("에서 건축할 수 있습니다.", NamedTextColor.WHITE))
            !changed -> who.append(Component.text("님은 ", NamedTextColor.WHITE)).append(Component.text(estate.name, NamedTextColor.GREEN))
                .append(Component.text("에 공유되어 있지 않습니다.", NamedTextColor.WHITE))
            trusted -> who.append(Component.text("님이 ", NamedTextColor.WHITE)).append(Component.text(estate.name, NamedTextColor.GREEN))
                .append(Component.text("에서 건축할 수 있게 되었습니다.", NamedTextColor.WHITE))
            else -> who.append(Component.text("님은 이제 ", NamedTextColor.WHITE)).append(Component.text(estate.name, NamedTextColor.GREEN))
                .append(Component.text("에서 건축할 수 없습니다.", NamedTextColor.WHITE))
        })
    }

    private fun owner(target: String): UUID = runCatching { UUID.fromString(target) }.getOrNull() ?: FileManager.players.findByName(target)
        ?: throw IllegalArgumentException("해당 플레이어를 찾을 수 없습니다!")

    private fun inspect(sender: CommandSender, target: String) {
        val owner = owner(target)
        val name = runCatching { FileManager.players.profile(owner).name }.getOrNull() ?: target
        FileManager.estates.damaged().filter { com.github.lupi13.limpi.estate.EstateDocuments.ownerOf(it.value) == owner }.keys.forEach {
            sender.sendMessage(Component.text("손상된 사유지 기록 $it: /limpi estate discard로 정리", NamedTextColor.RED))
        }
        val owned = FileManager.estates.owned(owner)
        sender.sendMessage("$name($owner): 사유지 ${owned.size}개")
        owned.forEach { estate ->
            sender.sendMessage("  ${estate.name} ${estate.id} ${estate.worldName} ${estate.region.describe()} ${estate.region.volume}칸, " +
                "구매가 ${estate.paid.units}원, 공유 ${estate.members.joinToString(", ") { EstateEvents.ownerName(it) }.ifEmpty { "없음" }}")
        }
    }

    /** remove <player> <name> refund|keep <reason...> */
    private fun remove(sender: CommandSender, args: Array<String>) {
        val owner = owner(args[1])
        val estate = FileManager.estates.byName(owner, args[2]) ?: throw IllegalArgumentException("${args[2]} 은(는) 존재하지 않는 사유지이름입니다.")
        val refund = when (args[3].lowercase(Locale.ROOT)) {
            "refund" -> true; "keep" -> false
            else -> throw IllegalArgumentException("refund 또는 keep을 입력하세요.")
        }
        val settings = EstateEvents.settings() ?: throw EstateUnavailable("사유지 설정에 오류가 있어 지금은 처리할 수 없습니다.")
        val request = UUID.randomUUID()
        val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
        val (removed, amount) = FileManager.estates.remove(request, actor, estate.id, refund, args.drop(4).joinToString(" "), settings)
        sender.sendMessage("${removed.name}(${removed.id})을(를) 회수했습니다. 환불 ${amount.units}원. 감사 거래: $request")
    }
}

class EstateTab(plugin: Plugin) : CommandCompletion(plugin, CommandDefinitions.estate, "EnableEstateSystem")
