package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.events.MarketEvents
import com.github.lupi13.limpi.market.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Level

class MarketCommands(private val plugin: Plugin) {
    fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val request = UUID.randomUUID()
        try {
            val subcommand = args.getOrNull(1)?.lowercase(Locale.ROOT)
            if (subcommand in setOf("escrow", "resolve")) {
                require(sender.hasPermission("limpi.financial.admin.market")) { "권한이 부족합니다." }
                val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
                when (subcommand) {
                    "escrow" -> {
                        require(args.size == 3) { "/financial market escrow <소유자 UUID>" }
                        FileManager.escrow.pending(UUID.fromString(args[2])).forEach {
                            sender.sendMessage(Component.text("${it.listing}: ${it.state} / ${it.reason ?: "등록 중"}", NamedTextColor.WHITE))
                        }
                    }
                    "resolve" -> {
                        require(args.size >= 6) { "/financial market resolve <소유자 UUID> <매물 UUID> removed|retained <확인 근거>" }
                        val owner = UUID.fromString(args[2]); require(plugin.server.getPlayer(owner) == null) { "소유자가 오프라인일 때 저장 상태를 확인하세요." }
                        val decision = args[4].lowercase(Locale.ROOT); require(decision in setOf("removed", "retained"))
                        FileManager.escrow.resolve(request, actor, owner, UUID.fromString(args[3]), decision == "removed", args.drop(5).joinToString(" "))
                    }
                }
                sender.sendMessage(Component.text("처리했습니다. 감사 거래 UUID: $request", NamedTextColor.GREEN))
                MarketEvents.reopenMarketGUI(); return true
            }
            require(sender is Player) { "플레이어만 사용할 수 있습니다." }
            require(sender.hasPermission("limpi.financial.market")) { "권한이 부족합니다." }
            if (subcommand == null) { MarketEvents.openMarketGUI(sender); return true }
            when (subcommand) {
                "add" -> {
                    require(args.size == 4) { "/financial market add <4글자 코드> <0 이상 가격>" }
                    val code = args[2]; require(code.matches(Regex("[A-Z0-9]{4}"))) { "코드는 대문자/숫자 4글자입니다." }
                    val price = Money(requireNotNull(args[3].toLongOrNull()) { "가격은 Long 범위 정수로 입력하세요." })
                    val item = sender.inventory.itemInMainHand
                    require(!item.type.isAir) { "손에 물품을 들고 있어야 합니다." }
                    val name = item.itemMeta?.customName()?.let { PlainTextComponentSerializer.plainText().serialize(it) }
                        ?.takeIf(String::isNotBlank) ?: item.type.name
                    if (!FileManager.delivery.register(sender, request, code, name, price)) return true
                    sender.sendMessage(Component.text("매물을 등록했습니다. 거래 UUID: $request", NamedTextColor.GREEN))
                    FileManager.delivery.claim(sender)
                }
                "remove" -> {
                    require(args.size == 3) { "/financial market remove <코드>" }
                    val listing = requireNotNull(FileManager.market.findByCode(args[2])) { "존재하지 않는 매물입니다." }
                    val owner = listing.seller
                    require(owner == sender.uniqueId || sender.hasPermission("limpi.financial.admin.market")) { "다른 플레이어의 매물입니다." }
                    FileManager.marketTrades.withdraw(request, owner, listing.id, sender.uniqueId.toString())
                    sender.sendMessage(Component.text("회수를 확정했습니다. 물품은 소유자의 미수령으로 반환합니다. 거래 UUID: $request", NamedTextColor.GREEN))
                    if (owner == sender.uniqueId) FileManager.delivery.claim(sender)
                }
                else -> throw IllegalArgumentException("/financial market [add|remove|escrow|resolve]")
            }
            MarketEvents.reopenMarketGUI()
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "입력을 확인하세요.", NamedTextColor.RED))
        } catch (failure: MarketException) {
            sender.sendMessage(Component.text(failure.reason.message, NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Market command $request failed", failure)
            sender.sendMessage(Component.text("거래 결과를 확인하지 못했습니다. 관리자에게 UUID $request 을 알려주세요.", NamedTextColor.RED))
        }
        return true
    }
}
