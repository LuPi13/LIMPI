package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.LIMPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.asJavaRandom

class StockEvents : Listener {
    companion object {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)
        var fluctToggle = false
        var beforePrice: MutableMap<String, Int> = HashMap()


        /**
         * 주식 가격 변동
         * @param[initialPrice] 초기 가격
         * @param[currentPrice] 현재 가격
         * @return 변동된 가격
         */
        fun fluctPrice(initialPrice: Int, currentPrice: Int): Int {
            val config = plugin.config
            val min = config.getDouble("StockMinimumRatio")
            val max = config.getDouble("StockMaximumRatio")
            val stdDev = config.getDouble("StockStandardDeviation")
            val drag = config.getDouble("StockDrag")

            val bias = (initialPrice - currentPrice) / initialPrice
            val random = Random.asJavaRandom().nextGaussian(1.0, stdDev) * (1 + bias * drag).coerceIn(min, max)
            return (currentPrice * random).toInt()
        }

        fun writeStock(stock: String): Component {
            val stockConfig = FileManager.getStockConfig()
            val stockPrice = stockConfig.getInt("$stock.currentPrice")
            val stockBefore = beforePrice[stock]!!
            val stockChange = stockPrice - stockBefore
            val stockChangePercent = ((stockChange.toDouble() / stockBefore) * 100 + 100).toInt()
            val disp = if (stockChangePercent == 100) {
                Component.text("-100%", NamedTextColor.BLACK)
            } else if (stockChangePercent >= 100) {
                Component.text("▲${stockChangePercent}%", NamedTextColor.RED)
            } else {
                Component.text("▼${stockChangePercent}%", NamedTextColor.BLUE)
            }
            return Component.text("${stockConfig.getString("$stock.name")} ($stock)", NamedTextColor.DARK_AQUA)
                .append(Component.text(":\n", NamedTextColor.BLACK))
                .append(moneyDisplay(stockBefore))
                .append(Component.text("->", NamedTextColor.BLACK))
                .append(moneyDisplay(stockPrice))
                .append(Component.text("\n(", NamedTextColor.BLACK))
                .append(disp)
                .append(Component.text(")\n\n", NamedTextColor.BLACK))
        }

        fun stockFlow() {
            object: BukkitRunnable() {
                override fun run() {
                    if (Calendar.getInstance().get(Calendar.MINUTE) % plugin.config.getInt("StockInterval") == 0) {
                        if (plugin.server.onlinePlayers.isNotEmpty()) {
                            if (fluctToggle) {
                                fluctToggle = false
                                val stockConfig = FileManager.getStockConfig()
                                val stock = stockConfig.getKeys(false)
                                for (item in stock) {
                                    val price = stockConfig.getInt("$item.currentPrice")
                                    beforePrice[item] = price
                                    val currentPrice = fluctPrice(stockConfig.getInt("$item.initialPrice"), price)
                                    stockConfig["${item}.currentPrice"] = currentPrice
                                }
                                FileManager.saveStock()
                                plugin.server.broadcast(Component.text("주식 가격이 변동되었습니다.", NamedTextColor.GREEN))


                                val stockShow = Component.text("여기를 눌러 주식 변동을 확인하세요.", NamedTextColor.LIGHT_PURPLE)
                                    .hoverEvent(HoverEvent.showText(Component.text("/fc stock show")))
                                    .clickEvent(ClickEvent.runCommand("/fc stock show"))

                                for (player in Functions.getOnlinePlayers()) {
                                    if (player.openInventory.title() == StockGUIName) {
                                        openStockGUI(player)
                                    }
                                    player.sendMessage(stockShow)
                                }


                            }
                        }
                    }
                    if ((Calendar.getInstance().get(Calendar.MINUTE) + 1) % plugin.config.getInt("StockInterval") == 0) {
                        if (plugin.server.onlinePlayers.isNotEmpty()) {
                            if (!fluctToggle) {
                                fluctToggle = true
                                plugin.server.broadcast(Component.text("주식이 1분 후 변동됩니다!", NamedTextColor.YELLOW))
                            }
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }

        fun showBook(player: Player) {
            try {
                val stockConfig = FileManager.getStockConfig()
                val stock = stockConfig.getKeys(false)

                val book = ItemStack(Material.WRITTEN_BOOK, 1)
                val meta: BookMeta = book.itemMeta as BookMeta
                val time = System.currentTimeMillis()
                val instant = Instant.ofEpochMilli(time)
                val zoneId = ZoneId.systemDefault()
                val zonedDateTime = instant.atZone(zoneId)
                val dateFormat = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")
                val date = zonedDateTime.format(dateFormat)
                val timeFormat = DateTimeFormatter.ofPattern("HH시 mm분")
                val time1 = zonedDateTime.format(timeFormat)

                var page: Component
                page = Component.text("\n${date}\n${time1} 부\n\n", NamedTextColor.DARK_GREEN)
                    .append(Component.text("주식 가격 변동\n\n\n\n", NamedTextColor.BLACK, TextDecoration.BOLD))

                var initialSum = 0
                var currentSum = 0
                for (item in stock) {
                    val current = stockConfig.getInt("$item.currentPrice")
                    val initial = stockConfig.getInt("$item.initialPrice")
                    initialSum += initial
                    currentSum += current
                }
                val LOSPI = (currentSum - initialSum) / initialSum.toDouble() * 100 + 100
                val stringLOSPI = if (LOSPI >= 100.0) {
                    Component.text("▲${String.format("%.2f", LOSPI)}%", NamedTextColor.RED)
                } else {
                    Component.text("▼${String.format("%.2f", LOSPI)}%", NamedTextColor.BLUE)
                }

                val standDatetime = FileManager.getMiscConfig()["lastStockStand"] as String
                val standYear = standDatetime.substring(0, standDatetime.indexOf("-"))
                val standMonth = standDatetime.substring(standDatetime.indexOf("-") + 1, standDatetime.lastIndexOf("-"))
                val standDay = standDatetime.substring(standDatetime.lastIndexOf("-") + 1, standDatetime.indexOf(" "))
                val standHour = standDatetime.substring(standDatetime.indexOf(" ") + 1, standDatetime.indexOf(":"))
                val standMinute =
                    standDatetime.substring(standDatetime.indexOf(":") + 1, standDatetime.lastIndexOf(":"))
                val standDate = "${standYear}년 ${standMonth}월 ${standDay}일"
                val standTime = "${standHour}시 ${standMinute}분"

                page = page.append(Component.text("LOSPI: ", NamedTextColor.BLACK)
                    .append(stringLOSPI)
                    .append(Component.text("\n(기준 시점: $standDate ${standTime})\n\n", NamedTextColor.BLACK)))

                meta.addPages(page)
                page = Component.text("")
                var line = 1
                for (item in stock) {
                    if (line == stock.size) {
                        page = page.append(writeStock(item))
                        meta.addPages(page)
                        break
                    }
                    if (line % 3 != 0) {
                        page = page.append(writeStock(item))
                    } else {
                        page = page.append(writeStock(item))
                        meta.addPages(page)
                        page = Component.text("")
                    }
                    line += 1
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

        val StockGUIName = Component.text("LIMPI: 주식창", NamedTextColor.AQUA, TextDecoration.BOLD)
        fun openStockGUI(player: Player) {
            val stockGUI = Bukkit.createInventory(player, 54, StockGUIName)
            val stockConfig = FileManager.getStockConfig()
            val playerConfig = FileManager.getPlayerData(player)
            var index = 0
            for (code in stockConfig.getKeys(false)) {
                val stockItem = ItemStack(Functions.toMaterial(stockConfig.getString("$code.material")!!))
                val stockMeta = stockItem.itemMeta
                val stockLore = mutableListOf<Component>()
                var stockChange: Component
                try {
                    val beforePrice = beforePrice[code]!!
                    val currentPrice = stockConfig.getInt("$code.currentPrice")
                    val percent = ((currentPrice.toDouble() / beforePrice) * 100).toInt()
                    stockChange = if (percent == 100) {
                        Component.text("-100%", NamedTextColor.WHITE)
                    } else if (percent >= 100) {
                        Component.text("▲${percent}%", NamedTextColor.RED)
                    } else {
                        Component.text("${percent}%", NamedTextColor.BLUE)
                    }
                } catch (e: Exception) {
                    stockChange = Component.text("-(확인 불가)", NamedTextColor.WHITE)
                }
                stockMeta!!.customName(Component.text("${stockConfig.getString("$code.name")} (${code})", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                stockLore.add(Component.text("현재 가격: ", NamedTextColor.WHITE)
                    .append(moneyDisplay(stockConfig.getInt("$code.currentPrice")))
                    .append(Component.text("원, 현재 보유 금액: ", NamedTextColor.WHITE))
                    .append(moneyDisplay(playerConfig.getInt("money")))
                    .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false))

                val stockCount = playerConfig.getInt("stock.${code}.count")
                stockLore.add(Component.text("보유 주식: ", NamedTextColor.WHITE)
                    .append(Component.text("$stockCount", NamedTextColor.GREEN))
                    .append(Component.text("주, 최근 변동: ", NamedTextColor.WHITE))
                    .append(stockChange).decoration(TextDecoration.ITALIC, false))
                stockLore.add(Component.text("총투자금액: ", NamedTextColor.WHITE)
                    .append(moneyDisplay(playerConfig.getInt("stock.$code.averagePrice") * stockCount))
                    .append(Component.text("원, 현재가치: ", NamedTextColor.WHITE))
                    .append(moneyDisplay(stockCount * stockConfig.getInt("$code.currentPrice")))
                    .append(Component.text("원", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false))
                stockLore.add(Component.text("좌클릭하여 매수, 우클릭하여 매도합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                stockLore.add(Component.text("Shift를 누르면 100주/전량 거래합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                stockMeta.lore(stockLore)
                for (itemFlag in ItemFlag.entries) {
                    stockMeta.addItemFlags(itemFlag)
                }
                stockItem.itemMeta = stockMeta
                stockGUI.setItem(index, stockItem)
                index += 1
            }
            player.openInventory(stockGUI)
        }
    }


    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() == StockGUIName) {
            val player = event.whoClicked as Player
            val stockConfig = FileManager.getStockConfig()
            val playerConfig = FileManager.getPlayerData(player)
            val item = event.currentItem
            if (item != null && item.hasItemMeta()) {
                var stockCode = PlainTextComponentSerializer.plainText().serialize(item.displayName())
                val stockName = stockCode.substring(1, stockCode.length - 8)
                stockCode = stockCode.substring(stockCode.length - 6, stockCode.length - 2)
                val stockCount = playerConfig.getInt("stock.${stockCode}.count")
                val stockPrice = stockConfig.getInt("$stockCode.currentPrice")
                var count = 1

                if (event.isLeftClick) {
                    if (playerConfig.getInt("money") >= stockPrice) {
                        if (event.isShiftClick) {
                            count = min(playerConfig.getInt("money") / stockPrice, 100)
                        }
                        playerConfig["stock.${stockCode}.count"] = stockCount + count
                        playerConfig["stock.${stockCode}.averagePrice"] =
                            ((playerConfig.getInt("stock.${stockCode}.averagePrice") * stockCount + (stockPrice * count)) / (stockCount + count))
                        playerConfig["money"] = playerConfig.getInt("money") - (stockPrice * count)
                        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                        player.sendMessage(Component.text("$stockName (${stockCode})", NamedTextColor.AQUA)
                            .append(Component.text(" $count", NamedTextColor.GREEN))
                            .append(Component.text("주 매수 완료.", NamedTextColor.WHITE)))
                    }
                    else {
                        player.sendMessage(Component.text("돈이 부족합니다!", NamedTextColor.RED))
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                    }
                }

                if (event.isRightClick) {
                    if (stockCount >= 1) {
                        if (event.isShiftClick) {
                            count = min(stockCount, 100)
                        }
                        playerConfig["stock.${stockCode}.count"] = stockCount - count
                        playerConfig["stock.${stockCode}.averagePrice"] =
                            if (stockCount - count == 0) 0 else ((playerConfig.getInt("stock.${stockCode}.averagePrice") * stockCount - (stockPrice * count)) / (stockCount - count))
                        playerConfig["money"] = playerConfig.getInt("money") + (stockPrice * count)
                        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                        player.sendMessage(Component.text("$stockName (${stockCode})", NamedTextColor.AQUA)
                            .append(Component.text(" $count", NamedTextColor.GREEN))
                            .append(Component.text("주 매도 완료.", NamedTextColor.WHITE)))
                    }
                    else {
                        player.sendMessage(Component.text("보유 주식이 없습니다!", NamedTextColor.RED))
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                    }
                }
                FileManager.savePlayerData(player, playerConfig)
                openStockGUI(player)
            }
            event.isCancelled = true
        }
    }
}