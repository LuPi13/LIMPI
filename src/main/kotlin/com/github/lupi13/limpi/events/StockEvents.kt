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
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Clock
import java.util.Locale
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
            plugin.config.getDouble("StockMinimumRatio"),
            plugin.config.getDouble("StockMaximumRatio"),
            plugin.config.getDouble("StockStandardDeviation"),
            plugin.config.getDouble("StockPriceDrag"),
        )

        fun stockFlow() {
            stop()
            val settings = policy()
            val schedule = StockSchedule(Math.multiplyExact(plugin.config.getLong("StockInterval"), 60_000L))
            val data = FileManager.getStockConfig()
            for (code in codes(data)) {
                val current = price(data, code).units
                price(data, code, "initialPrice")
                beforePrice[code] = data.getLong("$code.previousPrice", current).coerceAtLeast(1)
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
                                random.nextGaussian(1.0, settings.standardDeviation))
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
                    plugin.server.broadcast(Component.text("주식이 1분 이내에 변동됩니다!", NamedTextColor.YELLOW))
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

        /** A malformed or negative legacy balance is shown as needing recovery, never as 0. */
        private fun balanceDisplay(profile: FileConfiguration): Component =
            runCatching { Accounts.integralBalance(profile) }.getOrNull()?.takeIf { it >= 0 }?.let(::moneyDisplay)
                ?: Component.text("계좌 복구 필요", NamedTextColor.RED)

        private fun change(current: Long, previous: Long, color: NamedTextColor = NamedTextColor.WHITE): Component {
            if (previous <= 0) return Component.text("확인 불가", color)
            val percent = (current.toDouble() / previous - 1) * 100
            val display = String.format(Locale.ROOT, "%.2f%%", kotlin.math.abs(percent))
            return when {
                current > previous -> Component.text("▲$display", NamedTextColor.RED)
                current < previous -> Component.text("▼$display", NamedTextColor.BLUE)
                else -> Component.text("0.00%", color)
            }
        }

        fun writeStock(code: String): Component {
            val data = FileManager.getStockConfig()
            val current = price(data, code).units
            val previous = data.getLong("$code.previousPrice", beforePrice[code] ?: current)
            return Component.text("${data.getString("$code.name")} ($code)\n", NamedTextColor.DARK_AQUA)
                .append(moneyDisplay(previous)).append(Component.text(" → ", NamedTextColor.BLACK))
                .append(moneyDisplay(current)).append(Component.text("\n", NamedTextColor.BLACK))
                .append(change(current, previous, NamedTextColor.BLACK))
                .append(Component.text("\n\n", NamedTextColor.BLACK))
        }

        fun showBook(player: Player) {
            val data = FileManager.getStockConfig()
            val stocks = codes(data)
            val initial = stocks.fold(BigDecimal.ZERO) { sum, code -> sum + BigDecimal.valueOf(price(data, code, "initialPrice").units) }
            val current = stocks.fold(BigDecimal.ZERO) { sum, code -> sum + BigDecimal.valueOf(price(data, code).units) }
            val index = if (initial.signum() == 0) "확인 불가" else current.multiply(BigDecimal.valueOf(100))
                .divide(initial, 2, RoundingMode.HALF_UP).toPlainString()
            val book = ItemStack(Material.WRITTEN_BOOK)
            val meta = book.itemMeta as BookMeta
            meta.title = "주식 가격 변동"
            meta.author = "LIMPI"
            meta.addPages(Component.text("주식 가격 변동\n\nLOSPI: $index\n(기준 100)\n\n기준 시점: " +
                (data.getString("lastStockStand") ?: FileManager.getMiscConfig().getString("lastStockStand")
                    ?: "기록 없음"), NamedTextColor.BLACK))
            for (group in stocks.chunked(3)) {
                var page = Component.empty()
                group.forEach { page = page.append(writeStock(it)) }
                meta.addPages(page)
            }
            book.itemMeta = meta
            player.openBook(book)
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
                meta.customName(Component.text("${data.getString("$code.name")} ($code)", NamedTextColor.WHITE))
                meta.lore(listOf(
                    Component.text("현재 가격: ", NamedTextColor.WHITE).append(moneyDisplay(current.units))
                        .append(Component.text("원, 보유 금액: ")).append(balanceDisplay(profile)),
                    Component.text("보유 주식: ${holding.count}주", NamedTextColor.WHITE),
                    Component.text("취득 원가: ", NamedTextColor.WHITE).append(moneyDisplay(holding.costBasis.units))
                        .append(Component.text("원, 현재 가치: ")).append(moneyDisplay(BigInteger.valueOf(current.units)
                            .multiply(BigInteger.valueOf(holding.count)).toString())),
                    Component.text("최근 변동: ", NamedTextColor.WHITE)
                        .append(change(current.units, data.getLong("$code.previousPrice", current.units))),
                    Component.text("좌클릭 매수 / 우클릭 매도, Shift: 최대 100주", NamedTextColor.WHITE),
                ).map { it.decoration(TextDecoration.ITALIC, false) })
                item.itemMeta = meta
                inventory.setItem(slot, item)
                holder.actions[slot] = MenuAction.StockQuote(code, current.units)
            }
            fun pageButton(slot: Int, target: Int, label: String) {
                val item = ItemStack(Material.ARROW)
                val meta = item.itemMeta
                meta.customName(Component.text(label, NamedTextColor.WHITE))
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
            require(quote.code in codes(data)) { "이미 삭제된 종목입니다." }
            val current = price(data, quote.code)
            require(current.units == quote.price) { "가격이 변경되었습니다. 새 가격을 확인해 주세요." }
            val trading = FileManager.stocks
            val profile = FileManager.getPlayerData(player)
            val balance = runCatching { Accounts.balance(profile) }.getOrNull()
            require(balance != null) { "계좌 데이터에 오류가 있습니다. 관리자에게 확인을 요청하세요." }
            val count = if (event.isLeftClick) {
                minOf(if (event.isShiftClick) 100L else 1L, balance.units / current.units)
            } else {
                minOf(if (event.isShiftClick) 100L else 1L, trading.holding(profile, quote.code).count)
            }
            require(count > 0) { if (event.isLeftClick) "돈이 부족합니다." else "보유 주식이 없습니다." }
            val result = trading.trade(java.util.UUID.randomUUID(), player.uniqueId, quote.code, current, count,
                if (event.isLeftClick) StockTrade.BUY else StockTrade.SELL)
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1f)
            player.sendMessage(Component.text("${data.getString("${quote.code}.name")} (${quote.code}) $count" +
                (if (event.isLeftClick) "주 매수 완료." else "주 매도 완료.") + " 거래: ${result.transaction}", NamedTextColor.WHITE))
        } catch (failure: IllegalArgumentException) {
            player.sendMessage(Component.text(failure.message ?: "거래 조건이 유효하지 않습니다.", NamedTextColor.RED))
        } catch (failure: ArithmeticException) {
            player.sendMessage(Component.text("거래 금액 또는 보유 수량이 허용 범위를 초과합니다.", NamedTextColor.RED))
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Stock trade failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("거래를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", NamedTextColor.RED))
        }
        openStockGUI(player, holder.page, top)
    }
}
