package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.menu.*
import com.github.lupi13.limpi.stock.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.math.BigInteger
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Random
import java.util.logging.Level

class StockEvents : Listener {
    companion object {
        private val plugin: Plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        private val clock = Clock.systemUTC()
        private val random = Random()
        private var flowTask: BukkitTask? = null
        val beforePrice = mutableMapOf<String, Long>()
        val StockGUIName = Component.text("LIMPI: 주식창", NamedTextColor.AQUA, TextDecoration.BOLD)

        fun codes(data: FileConfiguration = FileManager.getStockConfig()): List<String> =
            StockCatalog.codes(data)

        private fun price(data: FileConfiguration, code: String, field: String = "currentPrice"): Money =
            StockCatalog.price(data, code, field)

        private fun policy() = StockPricePolicy(
            plugin.config.getDouble("StockVolatility"),
            plugin.config.getDouble("StockReversion"),
        )

        fun stockFlow() {
            stop()
            val settings = policy()
            val schedule = StockSchedule(Math.multiplyExact(plugin.config.getLong("StockInterval"), 60_000L))
            val data = FileManager.getStockConfig()
            for (code in codes(data)) {
                price(data, code)
                price(data, code, "initialPrice")
                (data.get("$code.previousPrice") as? Number)?.toLong()?.takeIf { it > 0 }?.let { beforePrice[code] = it }
            }
            var warnedBoundary = Long.MIN_VALUE
            flowTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                if (plugin.server.onlinePlayers.isEmpty()) return@Runnable
                val now = clock.millis()
                val stocks = FileManager.getStockConfig()
                val lastUpdate = stocks.getLong("_meta.lastUpdate", -1)
                if (schedule.due(now, lastUpdate)) {
                    try {
                        val old = codes(stocks).associateWith { price(stocks, it).units }
                        val updated = old.mapValues { (code, current) ->
                            settings.next(price(stocks, code, "initialPrice").units, current,
                                random.nextGaussian())
                        }
                        for ((code, current) in updated) {
                            stocks.set("$code.previousPrice", old.getValue(code))
                            stocks.set("$code.currentPrice", current)
                        }
                        stocks.set("_meta.lastUpdate", schedule.boundary(now))
                        FileManager.saveStock()
                        beforePrice.putAll(old)
                        plugin.server.broadcast(Component.text("주식 가격이 변동되었습니다.", NamedTextColor.GREEN))
                        val notice = Component.text("여기를 눌러 주식 변동을 확인하세요.", NamedTextColor.LIGHT_PURPLE)
                            .hoverEvent(HoverEvent.showText(Component.text("/fc stock show")))
                            .clickEvent(ClickEvent.runCommand("/fc stock show"))
                        plugin.server.onlinePlayers.forEach { it.sendMessage(notice) }
                        refreshMenus()
                    } catch (failure: Exception) {
                        plugin.logger.log(Level.SEVERE, "Stock update failed; no success announcement sent", failure)
                    }
                }
                val next = schedule.nextBoundary(now)
                if (next - now <= 60_000 && warnedBoundary != next) {
                    warnedBoundary = next
                    plugin.server.broadcast(Component.text("주식이 1분 후 변동됩니다!", NamedTextColor.YELLOW))
                }
            }, 0L, 20L)
        }

        fun stop() { flowTask?.cancel(); flowTask = null; beforePrice.clear() }

        fun refreshMenus() {
            for (player in plugin.server.onlinePlayers) {
                val current = player.openInventory.topInventory
                val holder = current.holder as? MenuHolder ?: continue
                if (holder.kind == MenuKind.STOCK) openStockGUI(player, holder.page, current)
            }
        }

        /** A malformed or negative balance is shown as needing recovery, never as 0. */
        private fun balanceDisplay(profile: FileConfiguration): Component =
            runCatching { Accounts.integralBalance(profile) }.getOrNull()?.takeIf { it >= 0 }?.let(::moneyDisplay)
                ?: Component.text("계좌 복구 필요", NamedTextColor.RED)

        /** Percent change from the previous price, as in the book: ▲ red / ▼ blue / white when unchanged, for the menu's dark background. */
        private fun change(current: Long, previous: Long?): Component {
            if (previous == null || previous <= 0) return Component.text("-(확인 불가)", NamedTextColor.WHITE)
            val move = StockMove("", current, previous)
            val text = "${StockSummary.percentText(move.changeTenths)}%"
            return if (move.direction == 0) Component.text(text, NamedTextColor.WHITE) else signed(move.direction, text)
        }

        private fun previous(data: FileConfiguration, code: String): Long? =
            (data.get("$code.previousPrice") as? Number)?.toLong() ?: beforePrice[code]

        fun writeStock(code: String): Component {
            val data = FileManager.getStockConfig()
            val stockPrice = price(data, code).units
            val stockBefore = previous(data, code)?.takeIf { it > 0 } ?: stockPrice
            val move = StockMove(code, stockPrice, stockBefore)
            val disp = signed(move.direction, "${StockSummary.percentText(move.changeTenths)}%")
            return Component.text("${data.getString("$code.name")} ($code)", NamedTextColor.DARK_AQUA)
                .append(Component.text(":\n", NamedTextColor.BLACK))
                .append(moneyDisplay(stockBefore))
                .append(Component.text("->", NamedTextColor.BLACK))
                .append(moneyDisplay(stockPrice))
                .append(Component.text("\n(", NamedTextColor.BLACK))
                .append(disp)
                .append(Component.text(")\n\n", NamedTextColor.BLACK))
        }

        private fun signed(direction: Int, text: String): Component = when {
            direction > 0 -> Component.text("▲$text", NamedTextColor.RED)
            direction < 0 -> Component.text("▼$text", NamedTextColor.BLUE)
            else -> Component.text(text, NamedTextColor.BLACK)
        }

        private fun marketLines(summary: MarketSummary): Component {
            var lines = Component.text("상승 ", NamedTextColor.BLACK).append(Component.text("${summary.rising}", NamedTextColor.RED))
                .append(Component.text(" / 하락 ", NamedTextColor.BLACK)).append(Component.text("${summary.falling}", NamedTextColor.BLUE))
                .append(Component.text("\n", NamedTextColor.BLACK))
            for ((label, move) in listOf("최고" to summary.best, "최저" to summary.worst)) {
                if (move == null) continue
                lines = lines.append(Component.text("$label: ${move.name} ", NamedTextColor.BLACK))
                    .append(signed(move.direction, "${StockSummary.percentText(move.changeTenths)}%"))
                    .append(Component.text("\n", NamedTextColor.BLACK))
            }
            return lines
        }

        /** The player's own stocks at the current prices. Delisted ones have no price and are left out, and a damaged holding shows no sum at all. */
        private fun portfolioLines(player: Player, data: FileConfiguration, listed: Set<String>): Component {
            val damaged = Component.text("\n보유 데이터에 오류가 있습니다.", NamedTextColor.RED)
            val positions = mutableListOf<StockPosition>()
            var delisted = 0
            try {
                val profile = FileManager.players.snapshot(player.uniqueId)
                val codes = profile.getConfigurationSection("stock")?.getKeys(false).orEmpty().filter { it.matches(Regex("[A-Z0-9]{4}")) }
                for (code in codes) {
                    val holding = FileManager.stocks.holding(profile, code)
                    if (holding.count == 0L) continue
                    if (code in listed) positions += StockPosition(holding.count, holding.costBasis.units, price(data, code).units) else delisted++
                }
            } catch (failure: Exception) {
                plugin.logger.log(Level.WARNING, "Stock holdings of ${player.name} cannot be summed for the book", failure)
                return damaged
            }
            if (positions.isEmpty() && delisted == 0) return Component.text("\n보유 주식 없음", NamedTextColor.GRAY)
            val summary = StockSummary.portfolio(positions)
            val direction = summary.profit.signum()
            var lines = Component.text("\n내 평가액: ", NamedTextColor.BLACK).append(moneyDisplay(summary.value.toString()))
                .append(Component.text("원\n손익: ", NamedTextColor.BLACK))
                .append(signed(direction, "${summary.profit.abs()}원"))
            summary.profitTenths?.let { tenths ->
                val sign = if (tenths > 0) "+" else if (tenths < 0) "-" else ""
                lines = lines.append(Component.text(" ($sign${StockSummary.percentText(tenths)}%)", NamedTextColor.BLACK))
            }
            if (delisted > 0) lines = lines.append(Component.text("\n(상장 중지 ${delisted}종목 제외)", NamedTextColor.DARK_GRAY))
            return lines
        }

        fun showBook(player: Player) {
            try {
                val data = FileManager.getStockConfig()
                val stocks = codes(data)
                val book = ItemStack(Material.WRITTEN_BOOK, 1)
                val meta = book.itemMeta as BookMeta
                val now = ZonedDateTime.now(ZoneId.systemDefault())
                val date = now.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
                val time = now.format(DateTimeFormatter.ofPattern("HH시 mm분"))

                // A book page holds 14 lines; the header leaves room for the two summaries, whose long names may wrap.
                val moves = stocks.map { StockMove(data.getString("$it.name") ?: it, price(data, it).units, previous(data, it)) }
                val page: Component = Component.text("${date}\n${time} 부\n\n", NamedTextColor.DARK_GREEN)
                    .append(Component.text("주식 가격 변동\n\n", NamedTextColor.BLACK, TextDecoration.BOLD))
                    .append(marketLines(StockSummary.market(moves)))
                    .append(portfolioLines(player, data, stocks.toSet()))
                meta.addPages(page)
                for (group in stocks.chunked(3)) {
                    var stockPage: Component = Component.text("")
                    group.forEach { stockPage = stockPage.append(writeStock(it)) }
                    meta.addPages(stockPage)
                }

                meta.customName(Component.text("stockFluct"))
                meta.author = "LIMPI"
                meta.title = "주식 가격 변동"
                meta.generation = BookMeta.Generation.ORIGINAL
                book.itemMeta = meta
                player.openBook(book)
            } catch (e: Exception) {
                player.sendMessage(Component.text("지금은 이용할 수 없습니다. (서버가 재시작 되고 주식이 변하지 않았을 경우, 조회할 수 없습니다.)", NamedTextColor.RED))
            }
        }

        fun openStockGUI(player: Player, requestedPage: Int = 0, expected: Inventory? = null) {
            val data = FileManager.getStockConfig()
            val stocks = codes(data)
            val pagination = Pagination(stocks.size, requestedPage)
            val holder = MenuHolder(MenuKind.STOCK, pagination.page, StockGUIName)
            val inventory = holder.inventory
            val profile = FileManager.getPlayerData(player)
            val trading = FileManager.stocks
            for ((slot, code) in stocks.subList(pagination.start, pagination.end).withIndex()) {
                val current = price(data, code)
                val holding = try {
                    trading.holding(profile, code)
                } catch (failure: IllegalArgumentException) {
                    null
                } catch (failure: ArithmeticException) {
                    null
                }
                if (holding == null) {
                    val invalid = ItemStack(Material.BARRIER)
                    val meta = invalid.itemMeta
                    meta.customName(Component.text("${data.getString("$code.name")} ($code)", NamedTextColor.RED))
                    meta.lore(listOf(Component.text("보유 데이터에 오류가 있습니다. 관리자에게 확인을 요청하세요.", NamedTextColor.RED)))
                    invalid.itemMeta = meta
                    inventory.setItem(slot, invalid)
                    continue
                }
                val material = Functions.toMaterial(data.getString("$code.material") ?: "")
                    .takeIf { it.isItem && !it.isAir } ?: Material.PAPER
                val item = ItemStack(material)
                val meta = item.itemMeta
                meta.customName(Component.text("${data.getString("$code.name")} (${code})", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                meta.lore(listOf(
                    Component.text("현재 가격: ", NamedTextColor.WHITE)
                        .append(moneyDisplay(current.units))
                        .append(Component.text("원, 현재 보유 금액: ", NamedTextColor.WHITE))
                        .append(balanceDisplay(profile))
                        .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
                    Component.text("보유 주식: ", NamedTextColor.WHITE)
                        .append(Component.text("${holding.count}", NamedTextColor.GREEN))
                        .append(Component.text("주, 최근 변동: ", NamedTextColor.WHITE))
                        .append(change(current.units, previous(data, code))).decoration(TextDecoration.ITALIC, false),
                    Component.text("총투자금액: ", NamedTextColor.WHITE)
                        .append(moneyDisplay(holding.costBasis.units))
                        .append(Component.text("원, 현재가치: ", NamedTextColor.WHITE))
                        .append(moneyDisplay(BigInteger.valueOf(current.units).multiply(BigInteger.valueOf(holding.count)).toString()))
                        .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
                    Component.text("좌클릭하여 매수, 우클릭하여 매도합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift를 누르면 100주/전량 거래합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                ) + listOfNotNull(plugin.config.getInt("StockFeePercent").takeIf { it > 0 }?.let { fee ->
                    Component.text("매수·매도 시 거래 금액의 ", NamedTextColor.WHITE)
                        .append(Component.text("$fee%", NamedTextColor.GREEN))
                        .append(Component.text("를 수수료로 냅니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
                }))
                for (itemFlag in ItemFlag.entries) meta.addItemFlags(itemFlag)
                item.itemMeta = meta
                inventory.setItem(slot, item)
                holder.actions[slot] = MenuAction.StockQuote(code, current.units)
            }
            fun pageButton(slot: Int, target: Int, label: String) {
                val item = ItemStack(Material.ARROW)
                val meta = item.itemMeta
                meta.customName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                item.itemMeta = meta
                inventory.setItem(slot, item)
                holder.actions[slot] = MenuAction.Page(target)
            }
            if (pagination.hasPrevious) pageButton(45, pagination.page - 1, "이전 페이지")
            if (pagination.hasNext) pageButton(53, pagination.page + 1, "다음 페이지")
            openMenu(plugin, player, inventory, expected)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? MenuHolder ?: return
        if (holder.kind != MenuKind.STOCK) return
        event.isCancelled = true
        if (event.rawSlot !in 0 until top.size) return
        if (event.click !in setOf(ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.RIGHT, ClickType.SHIFT_RIGHT)) return
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission("limpi.financial.stock") || FileManager.delivery.blocked(player.uniqueId)) return
        val action = holder.actions[event.rawSlot] ?: return
        if (action is MenuAction.Page) {
            openStockGUI(player, action.index, top)
            return
        }
        val quote = action as? MenuAction.StockQuote ?: return
        if (!event.isLeftClick && !event.isRightClick) return
        try {
            val data = FileManager.getStockConfig()
            require(quote.code in codes(data)) { "존재하지 않는 주식입니다." }
            val current = price(data, quote.code)
            require(current.units == quote.price) { "가격이 변경되었습니다. 새 가격을 확인해 주세요." }
            val trading = FileManager.stocks
            val profile = FileManager.getPlayerData(player)
            val balance = runCatching { Accounts.balance(profile) }.getOrNull()
            require(balance != null) { "계좌 데이터에 오류가 있습니다. 관리자에게 확인을 요청하세요." }
            val count = if (event.isLeftClick) {
                var affordable = minOf(if (event.isShiftClick) 100L else 1L, balance.units / current.units)
                while (affordable > 0 && trading.purchaseCost(current, affordable).units > balance.units) affordable--
                affordable
            } else {
                minOf(if (event.isShiftClick) 100L else 1L, trading.holding(profile, quote.code).count)
            }
            require(count > 0) { if (event.isLeftClick) "돈이 부족합니다!" else "보유 주식이 없습니다!" }
            val result = trading.trade(java.util.UUID.randomUUID(), player.uniqueId, quote.code, current, count,
                if (event.isLeftClick) StockTrade.BUY else StockTrade.SELL)
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
            var done = Component.text("${data.getString("${quote.code}.name")} (${quote.code})", NamedTextColor.AQUA)
                .append(Component.text(" $count", NamedTextColor.GREEN))
                .append(Component.text(if (event.isLeftClick) "주 매수 완료." else "주 매도 완료.", NamedTextColor.WHITE))
            if (result.fee.units > 0) done = done.append(Component.text(" (수수료 ${result.fee.units}원)", NamedTextColor.GRAY))
            player.sendMessage(done)
        } catch (failure: IllegalArgumentException) {
            player.sendMessage(Component.text(failure.message ?: "거래 조건이 유효하지 않습니다.", NamedTextColor.RED))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
        } catch (failure: ArithmeticException) {
            player.sendMessage(Component.text("거래 금액 또는 보유 수량이 허용 범위를 초과합니다.", NamedTextColor.RED))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Stock trade failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("거래를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", NamedTextColor.RED))
        }
        openStockGUI(player, holder.page, top)
    }
}
