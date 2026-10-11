package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.events.MarketEvents
import com.github.lupi13.limpi.market.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
                require(sender.hasPermission("limpi.financial.admin.market")) { "권한이 부족합니다!" }
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
            require(sender.hasPermission("limpi.financial.market")) { "권한이 부족합니다!" }
            if (subcommand == null) { MarketEvents.openMarketGUI(sender); return true }
            when (subcommand) {
                "add" -> {
                    require(args.size == 4) { "잘못된 형식입니다. /financial market add <코드> <가격>" }
                    val code = args[2]
                    require(code.matches(Regex("[0-9A-Z]{4}"))) { "코드는 대문자 알파벳과 숫자로만 이루어진 4글자로 입력해주세요." }
                    val units = args[3].toLongOrNull()?.takeIf { it >= 0 }
                    if (units == null) {
                        sender.sendMessage(Component.text(args[3], NamedTextColor.YELLOW)
                            .append(Component.text("은(는) 올바르지 않은 가격입니다!", NamedTextColor.RED)))
                        return true
                    }
                    val item = sender.inventory.itemInMainHand
                    require(!item.type.isAir) { "손에 아이템을 들고 있어야 합니다." }
                    val name = MarketEvents.itemName(item, item.type.name)
                    if (!FileManager.delivery.register(sender, request, code, name, Money(units))) return true
                    sender.sendMessage(Component.text(name, NamedTextColor.GREEN)
                        .append(Component.text("을(를) 장터에 추가했습니다.", NamedTextColor.WHITE)))
                    FileManager.delivery.claim(sender, quiet = true)
                }
                "remove" -> {
                    require(args.size == 3) { "잘못된 형식입니다. /financial market remove <코드>" }
                    val listing = requireNotNull(FileManager.market.findByCode(args[2])) { "존재하지 않는 코드입니다." }
                    val owner = listing.seller
                    if (owner != sender.uniqueId) {
                        require(sender.hasPermission("limpi.financial.admin.market")) { "다른 사람이 등록한 아이템은 삭제할 수 없습니다." }
                        sender.sendMessage(Component.text("다른 사람의 아이템을 관리자 권한으로 삭제합니다.", NamedTextColor.YELLOW))
                    }
                    // The item is never destroyed: it returns to the owner's claim storage.
                    FileManager.marketTrades.withdraw(request, owner, listing.id, sender.uniqueId.toString())
                    sender.sendMessage(Component.text(listing.name, NamedTextColor.GREEN)
                        .append(Component.text("을(를) 장터에서 삭제했습니다.", NamedTextColor.WHITE)))
                    if (owner == sender.uniqueId) FileManager.delivery.claim(sender, quiet = true)
                }
                else -> throw IllegalArgumentException("잘못된 형식입니다. /financial market [add/remove]")
            }
            MarketEvents.reopenMarketGUI()
        } catch (failure: IllegalArgumentException) {
            sender.sendMessage(Component.text(failure.message ?: "잘못된 형식입니다.", NamedTextColor.RED))
        } catch (failure: MarketException) {
            sender.sendMessage(Component.text(when (failure.reason) {
                MarketFailure.CODE_EXISTS -> "이미 존재하는 코드입니다."
                MarketFailure.NOT_AVAILABLE -> "존재하지 않는 코드입니다."
                MarketFailure.NOT_OWNER -> "다른 사람이 등록한 아이템은 삭제할 수 없습니다."
                else -> failure.reason.message
            }, NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Market command $request failed", failure)
            sender.sendMessage(Component.text("거래 결과를 확인하지 못했습니다. 관리자에게 문의하세요.", NamedTextColor.RED))
        }
        return true
    }
}
