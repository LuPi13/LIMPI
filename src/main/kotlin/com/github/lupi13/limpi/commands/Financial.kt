package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level

/** Shared player-facing economy wording (kept identical to the original plugin presentation). */
object EconomyText {
    /** Canonical non-negative balance, or null when the account needs an admin repair. */
    fun balance(id: UUID): Long? =
        runCatching { Accounts.integralBalance(FileManager.players.snapshot(id)) }.getOrNull()?.takeIf { it >= 0 }

    fun balanceDisplay(id: UUID): Component =
        balance(id)?.let(::moneyDisplay) ?: Component.text("계좌 복구 필요", NamedTextColor.RED)

    /** "돈이 부족합니다! <player>님은 현재 계좌에 <money><tail>" */
    fun insufficient(player: Player, tail: String = "원이 있습니다."): Component =
        Component.text("돈이 부족합니다! ", NamedTextColor.RED)
            .append(playerDisplay(player))
            .append(Component.text("님은 현재 계좌에 ", NamedTextColor.RED))
            .append(balanceDisplay(player.uniqueId))
            .append(Component.text(tail, NamedTextColor.RED))

    fun invalidNumber(value: String, tail: String = "은(는) 올바르지 않은 숫자입니다!"): Component =
        Component.text(value, NamedTextColor.YELLOW).append(Component.text(tail, NamedTextColor.RED))

    fun error(sender: CommandSender, text: String) = sender.sendMessage(Component.text(text, NamedTextColor.RED))

    fun deny(player: Player) = player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)

    /** Original wording for bank failures; new failure kinds keep their short service message. */
    fun bankFailure(sender: CommandSender, failure: BankException, insufficientTail: String = "원이 있습니다.") {
        when (failure.reason) {
            BankFailure.INSUFFICIENT_FUNDS ->
                if (sender is Player) sender.sendMessage(insufficient(sender, insufficientTail)) else error(sender, "돈이 부족합니다!")
            BankFailure.SAME_ACCOUNT -> error(sender, "자기 자신에게 송금할 수 없습니다!")
            BankFailure.UNKNOWN_PLAYER -> error(sender, "해당 플레이어를 찾을 수 없습니다!")
            else -> error(sender, failure.reason.message)
        }
    }
}

class Financial(private val plugin: Plugin, private val accountCommands: AccountCommands,
    private val deliveryCommands: DeliveryCommands, private val enabled: Boolean, private val marketCommands: MarketCommands,
    private val checkCommands: CheckCommands, private val catalogCommands: CatalogCommands,
    private val removalCommands: RemovalCommands, private val transitionCommands: TransitionCommands) : CommandExecutor {
    private val stocks = StockCommands()
    override fun onCommand(sender: CommandSender, command: Command, label: String, input: Array<out String>): Boolean {
        val (route, args) = CommandAccess.route(sender, "financial", input, CommandDefinitions.financial, enabled) ?: return true
        try {
            when (route.id.substringBefore(' ')) {
                "help" -> if (sender is Player && enabled) helpBook(sender)
                    else CommandHelp.show(sender, "financial", CommandDefinitions.financial, enabled)
                "send", "set", "add" -> accountCommands.execute(sender, args)
                "claim", "delivery" -> deliveryCommands.execute(sender, args)
                "market" -> marketCommands.execute(sender, args)
                "check" -> checkCommands.execute(sender, args)
                "sell", "shop" -> catalogCommands.execute(sender, args)
                "removal" -> removalCommands.execute(sender, args)
                "transition" -> transitionCommands.execute(sender, args)
                "stock" -> stocks.execute(sender, args)
                "sugar" -> sugar(sender, args)
                "me" -> {
                    val player = sender as Player
                    player.sendMessage(playerDisplay(player)
                        .append(Component.text("님의 계좌에는 ", NamedTextColor.WHITE))
                        .append(EconomyText.balanceDisplay(player.uniqueId))
                        .append(Component.text("원이 있습니다.", NamedTextColor.WHITE)))
                }
                "spy" -> {
                    val id = runCatching { UUID.fromString(args[1]) }.getOrNull() ?: FileManager.players.findByName(args[1])
                    if (id == null) { EconomyText.error(sender, "해당 플레이어를 찾을 수 없습니다!"); return true }
                    val data = runCatching { FileManager.players.snapshot(id) }.getOrNull()
                    if (data == null) { EconomyText.error(sender, "해당 플레이어를 찾을 수 없습니다!"); return true }
                    val name = data.getString("name") ?: id.toString()
                    // A malformed value is reported as needing repair instead of being shown as 0.
                    sender.sendMessage(playerDisplay(name)
                        .append(Component.text("님의 계좌에는 ", NamedTextColor.WHITE))
                        .append(EconomyText.balanceDisplay(id))
                        .append(Component.text("원이 있습니다.", NamedTextColor.WHITE)))
                }
                else -> error("Unbound financial command ${route.id}")
            }
        } catch (failure: BankException) {
            EconomyText.bankFailure(sender, failure)
        } catch (failure: IllegalArgumentException) {
            EconomyText.error(sender, failure.message ?: "잘못된 형식입니다.")
        } catch (failure: ArithmeticException) {
            EconomyText.error(sender, "계산 결과가 허용 범위를 초과합니다.")
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Financial command ${route.id} from ${sender.name} failed", failure)
            throw failure // As before: Bukkit reports an internal command error to the sender.
        }
        return true
    }

    /** The original /financial help book; commands added later are appended in the same style. */
    private fun helpBook(sender: Player) {
        val routes = CommandDefinitions.financial.visible(enabled, true, sender::hasPermission)
        val admin = routes.any { it.permission?.contains(".admin.") == true }
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta
        meta.title = "/financial 도움말"
        meta.author = "LIMPI"
        fun cmd(usage: String) = Component.text(usage, NamedTextColor.GREEN)
        fun desc(text: String) = Component.text(text, NamedTextColor.BLACK)

        var page = Component.text("\n\n[/financial 도움말]\n", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
        page = page.append(Component.text("/fi, /fc 로도 입력할 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
        meta.addPages(page)

        page = cmd("/financial help").append(desc(": 이 도움말을 보여줍니다.\n\n"))
            .append(cmd("/financial me")).append(desc(": 현재 본인 계좌의 돈을 보여줍니다.\n\n"))
            .append(cmd("/financial check <금액>")).append(desc(": 입력한 금액의 수표를 작성합니다. 우클릭하여 사용 가능합니다. "))
        meta.addPages(page)

        page = cmd("/financial send <플레이어> <금액>").append(desc(": 입력한 금액을 대상 플레이어에게 송금합니다. 수수료 5%가 부과됩니다.\n\n"))
            .append(cmd("/financial sell")).append(desc(": 아이템을 판매하는 창을 엽니다. 판매할 아이템을 넣고 오른쪽 아래 금을 클릭하면 판매됩니다.\n\n"))
            .append(cmd("/financial shop")).append(desc(": 아이템을 구매하는 창을 엽니다.\n\n"))
        meta.addPages(page)

        page = cmd("/financial stock").append(desc(": 주식 시장을 엽니다.\n\n"))
            .append(cmd("/financial market")).append(desc(": 유저간 거래가 가능한 장터 창을 엽니다.\n\n"))
            .append(cmd("/financial market add <코드> <가격>")).append(desc(": 손에 든 아이템을 장터에 추가합니다. 코드는 대문자 알파벳, 숫자로 이루어진 4글자입니다.\n\n"))
        meta.addPages(page)

        page = cmd("/financial market remove <코드>").append(desc(": 장터에서 아이템을 삭제합니다.\n\n"))
            .append(cmd("/financial claim")).append(desc(": 인벤토리 공간이 부족해 받지 못한 아이템을 받습니다.\n\n"))
        meta.addPages(page)

        val covered = setOf("help", "me", "check", "send", "sell", "shop", "stock", "market", "market add", "market remove", "claim",
            "spy", "set", "add", "sell set", "sell remove", "shop set", "shop remove", "stock add", "stock refix", "stock remove")
        fun extraPages(routes: List<CommandRoute>) {
            for (group in routes.chunked(3)) {
                var extra = Component.empty()
                for (route in group) extra = extra.append(cmd("/financial ${route.usage}")).append(desc(": ${route.description}\n\n"))
                meta.addPages(extra)
            }
        }
        val extras = routes.filter { it.id !in covered }
        extraPages(extras.filter { it.permission?.contains(".admin.") != true })

        if (admin) {
            page = Component.text("이하 관리자 전용 명령어\n", NamedTextColor.RED)
                .append(cmd("/financial spy <플레이어>").decoration(TextDecoration.BOLD, false)).append(desc(": 대상의 계좌의 돈을 보여줍니다.\n\n"))
                .append(cmd("/financial set <플레이어> <금액>")).append(desc(": 대상의 계좌의 돈을 입력한 금액으로 설정합니다.\n\n"))
                .append(cmd("/financial add <플레이어> <금액>")).append(desc(": 대상의 계좌에 입력한 금액을 추가합니다. 금액에 음수를 적을 시 돈을 차감합니다.\n\n"))
            meta.addPages(page)

            page = cmd("/financial sell set <아이템> <금액>").append(desc(": 아이템의 판매가격을 설정합니다.\n\n"))
                .append(cmd("/financial sell remove <아이템>")).append(desc(": 아이템의 판매가격을 삭제합니다.\n\n"))
                .append(cmd("/financial shop set <아이템> <비율>")).append(desc(": 아이템의 구매가격비율을 설정합니다. sell 가격 * 비율이 구매가격이 됩니다.\n\n"))
            meta.addPages(page)

            page = cmd("/financial shop remove <아이템>").append(desc(": 아이템의 구매가격비율을 삭제합니다.\n\n"))
                .append(cmd("/financial stock add <이름> <코드> <아이템> <가격>")).append(desc(": 주식을 추가합니다. 이름은 주식의 이름, 코드는 주식의 코드, 아이템은 주식의 아이템, 가격은 주식의 기준 가격입니다.\n\n"))
            meta.addPages(page)

            page = cmd("/financial stock refix <이름> <코드> <아이템> <가격>").append(desc(": 주식의 기준 가격을 현재 가격으로 재설정합니다. 주식의 가격은 기준 가격을 향하게 미세하게 조정됩니다.\n\n"))
                .append(cmd("/financial stock remove <이름>")).append(desc(": 주식을 삭제합니다.\n\n"))
            meta.addPages(page)
            page = cmd("/financial market remove <코드>").append(desc(": 다른 사람이 장터에 올린 아이템을 강제로 제거합니다.\n\n"))
            meta.addPages(page)
            extraPages(extras.filter { it.permission?.contains(".admin.") == true })
        }

        book.itemMeta = meta
        sender.openBook(book)
    }

    private fun sugar(sender: CommandSender, args: Array<out String>) {
        val owner = UUID.fromString(if (args[1].equals("void", true)) args[2] else args[1])
        if (!args[1].equals("void", true)) {
            val ids = FileManager.sugar.pendingIds(owner)
            sender.sendMessage(if (ids.isEmpty()) "적용 대기 중인 설탕 결과가 없습니다." else "적용 대기: ${ids.joinToString()}")
            return
        }
        require(plugin.server.getPlayer(owner) == null) { "소유자가 오프라인일 때 결과를 종료하세요." }
        val request = UUID.randomUUID()
        val actor = (sender as? Player)?.uniqueId?.toString() ?: "CONSOLE"
        FileManager.sugar.void(request, actor, owner, UUID.fromString(args[3]), args.drop(4).joinToString(" "))
        sender.sendMessage("설탕 결과 ${args[3]}을(를) 종료했습니다. 감사 거래: $request")
    }
}
