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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
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

        /** Percent of the previous price, as in the original menu: ▲ red / plain blue / "-100%" unchanged. */
        private fun change(current: Long, previous: Long?): Component {
            if (previous == null || previous <= 0) return Component.text("-(확인 불가)", NamedTextColor.WHITE)
            val percent = BigDecimal.valueOf(current).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 0, RoundingMode.DOWN).toLong()
            return when {
                percent == 100L -> Component.text("-100%", NamedTextColor.WHITE)
                percent >= 100L -> Component.text("▲${percent}%", NamedTextColor.RED)
                else -> Component.text("${percent}%", NamedTextColor.BLUE)
            }
        }

        private fun previous(data: FileConfiguration, code: String): Long? =
            (data.get("$code.previousPrice") as? Number)?.toLong() ?: beforePrice[code]

        fun writeStock(code: String): Component {
            val data = FileManager.getStockConfig()
            val stockPrice = price(data, code).units
            val stockBefore = previous(data, code)?.takeIf { it > 0 } ?: stockPrice
            val stockChangePercent = BigDecimal.valueOf(stockPrice).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(stockBefore), 0, RoundingMode.DOWN).toLong()
            val disp = if (stockChangePercent == 100L) {
                Component.text("-100%", NamedTextColor.BLACK)
            } else if (stockChangePercent >= 100L) {
                Component.text("▲${stockChangePercent}%", NamedTextColor.RED)
            } else {
                Component.text("▼${stockChangePercent}%", NamedTextColor.BLUE)
            }
            return Component.text("${data.getString("$code.name")} ($code)", NamedTextColor.DARK_AQUA)
                .append(Component.text(":\n", NamedTextColor.BLACK))
                .append(moneyDisplay(stockBefore))
                .append(Component.text("->", NamedTextColor.BLACK))
                .append(moneyDisplay(stockPrice))
                .append(Component.text("\n(", NamedTextColor.BLACK))
                .append(disp)
                .append(Component.text(")\n\n", NamedTextColor.BLACK))
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

                var page: Component = Component.text("\n${date}\n${time} 부\n\n", NamedTextColor.DARK_GREEN)
                    .append(Component.text("주식 가격 변동\n\n\n\n", NamedTextColor.BLACK, TextDecoration.BOLD))

                val initialSum = stocks.fold(BigDecimal.ZERO) { sum, code -> sum + BigDecimal.valueOf(price(data, code, "initialPrice").units) }
                val currentSum = stocks.fold(BigDecimal.ZERO) { sum, code -> sum + BigDecimal.valueOf(price(data, code).units) }
                val lospi = currentSum.multiply(BigDecimal.valueOf(100)).divide(initialSum, 2, RoundingMode.HALF_UP)
                val stringLOSPI = if (lospi >= BigDecimal.valueOf(100)) {
                    Component.text("▲${lospi.toPlainString()}%", NamedTextColor.RED)
                } else {
                    Component.text("▼${lospi.toPlainString()}%", NamedTextColor.BLUE)
                }

                val standDatetime = requireNotNull(data.getString("lastStockStand") ?: FileManager.getMiscConfig().getString("lastStockStand"))
                val standYear = standDatetime.substring(0, standDatetime.indexOf("-"))
                val standMonth = standDatetime.substring(standDatetime.indexOf("-") + 1, standDatetime.lastIndexOf("-"))
                val standDay = standDatetime.substring(standDatetime.lastIndexOf("-") + 1, standDatetime.indexOf(" "))
                val standHour = standDatetime.substring(standDatetime.indexOf(" ") + 1, standDatetime.indexOf(":"))
                val standMinute = standDatetime.substring(standDatetime.indexOf(":") + 1, standDatetime.lastIndexOf(":"))
                val standDate = "${standYear}년 ${standMonth}월 ${standDay}일"
                val standTime = "${standHour}시 ${standMinute}분"

                page = page.append(Component.text("LOSPI: ", NamedTextColor.BLACK)
                    .append(stringLOSPI)
                    .append(Component.text("\n(기준 시점: $standDate ${standTime})\n\n", NamedTextColor.BLACK)))
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
