package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.Financial
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.ChatColor
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
        var stockBook: ItemStack? = null


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

        fun writeStock(stock: String): String {
            val stockConfig = FileManager.getStockConfig()
            val stockPrice = stockConfig.getInt("$stock.currentPrice")
            val stockBefore = beforePrice[stock]!!
            val stockChange = stockPrice - stockBefore
            val stockChangePercent = ((stockChange.toDouble() / stockBefore) * 100 + 100).toInt()
            var disp = ""
            if (stockChangePercent == 100) {
                disp = "-100%"
            }
            else if (stockChangePercent >= 100) {
                disp = "${ChatColor.RED}▲${stockChangePercent}%${ChatColor.BLACK}"
            }
            else {
                disp = "${ChatColor.BLUE}▼${stockChangePercent}%${ChatColor.BLACK}"
            }
            return "${ChatColor.DARK_AQUA}${stock} (${stockConfig.getString("$stock.code")})${ChatColor.BLACK}: \n" +
                    "${Financial.moneyDisplay(stockBefore)}${ChatColor.BLACK}->${Financial.moneyDisplay(stockPrice)}${ChatColor.BLACK}\n(${disp})\n"
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
                                plugin.server.broadcastMessage("${ChatColor.GREEN}주식 가격이 변동되었습니다.")


                                val stockShow: BaseComponent = TextComponent("${ChatColor.LIGHT_PURPLE}여기를 눌러 주식 변동을 확인하세요.")
                                stockShow.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text("/fc stock show"))
                                stockShow.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fc stock show")

                                for (player in Functions.getOnlinePlayers()) {
                                    if (player.openInventory.title.equals(StockGUIName)) {
                                        openStockGUI(player)
                                    }
                                    player.spigot().sendMessage(ChatMessageType.CHAT, stockShow)
                                }


                            }
                        }
                    }
                    if ((Calendar.getInstance().get(Calendar.MINUTE) + 1) % plugin.config.getInt("StockInterval") == 0) {
                        if (plugin.server.onlinePlayers.isNotEmpty()) {
                            if (!fluctToggle) {
                                fluctToggle = true
                                plugin.server.broadcastMessage("${ChatColor.YELLOW}주식이 1분 후 변동됩니다!")
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
                val pages = mutableListOf<String>()
                val time = System.currentTimeMillis()
                val instant = Instant.ofEpochMilli(time)
                val zoneId = ZoneId.systemDefault()
                val zonedDateTime = instant.atZone(zoneId)
                val dateFormat = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")
                val date = zonedDateTime.format(dateFormat)
                val timeFormat = DateTimeFormatter.ofPattern("HH시 mm분")
                val time1 = zonedDateTime.format(timeFormat)

                var page = StringBuilder()
                page.append("\n${ChatColor.DARK_GREEN}${date}\n${time1} 부\n\n ${ChatColor.BLACK}${ChatColor.BOLD}주식 가격 변동\n\n\n\n")

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
                    "${ChatColor.RED}▲${String.format("%.2f", LOSPI)}"
                } else {
                    "${ChatColor.BLUE}▼${String.format("%.2f", LOSPI)}"
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

                page.append("LOSPI: ${stringLOSPI}%${ChatColor.BLACK}\n(기준 시점: ${standDate} ${standTime})\n\n")

                pages.add(page.toString())
                page = StringBuilder()
                page.append("\n")
                var line = 1;
                for (item in stock) {
                    if (line == stock.size) {
                        page.append(writeStock(item))
                        pages.add(page.toString())
                        page = StringBuilder()
                        page.append("\n")
                        break
                    }
                    if (line % 3 != 0) {
                        page.append(writeStock(item))
                        page.append("\n")
                    } else {
                        page.append(writeStock(item))
                        pages.add(page.toString())
                        page = StringBuilder()
                        page.append("\n")
                    }
                    line += 1
                }

                meta.pages = pages
                meta.setDisplayName("stockFluct")
                meta.author = "LIMPI"
                meta.title = "주식 가격 변동"
                meta.generation = BookMeta.Generation.ORIGINAL
                book.itemMeta = meta
                player.openBook(book)
            } catch (e: Exception) {
                player.sendMessage("${ChatColor.RED}지금은 이용할 수 없습니다. (서버가 재시작 되고 주식이 변하지 않았을 경우, 조회할 수 없습니다.)")
            }
        }

        val StockGUIName = "${ChatColor.AQUA}${ChatColor.BOLD}LIMPI: 주식창"
        fun openStockGUI(player: Player) {
            val stockGUI = Bukkit.createInventory(player, 54, StockGUIName)
            val stockConfig = FileManager.getStockConfig()
            val playerConfig = FileManager.getPlayerData(player)
            var index = 0
            for (item in stockConfig.getKeys(false)) {
                val stockItem = ItemStack(Functions.toMaterial(stockConfig.getString("$item.material")!!))
                val stockMeta = stockItem.itemMeta
                val stockLore = mutableListOf<String>()
                var stockChange = ""
                try {
                    val beforePrice = beforePrice[item]!!
                    val currentPrice = stockConfig.getInt("$item.currentPrice")
                    val percent = ((currentPrice.toDouble() / beforePrice) * 100).toInt()
                    if (percent == 100) {
                        stockChange = "${ChatColor.WHITE}-100%"
                    }
                    else if (percent >= 100) {
                        stockChange = "${ChatColor.RED}▲${percent}%"
                    }
                    else {
                        stockChange = "${ChatColor.BLUE}▼${percent}%"
                    }
                } catch (e: Exception) {
                    stockChange = "${ChatColor.WHITE}-(확인 불가)"
                }
                stockMeta!!.setDisplayName("${ChatColor.WHITE}${item} (${stockConfig.getString("$item.code")})")
                stockLore.add("${ChatColor.WHITE}현재 가격: ${ChatColor.YELLOW}${stockConfig.getInt("$item.currentPrice")}${ChatColor.WHITE}원, " +
                        "현재 보유 금액: ${Financial.moneyDisplay(playerConfig.getInt("money"))}원")
                val stockCount = playerConfig.getInt("stock.$item")
                stockLore.add("${ChatColor.WHITE}보유 주식: ${ChatColor.GREEN}${stockCount}${ChatColor.WHITE}주, " +
                        "최근 변동: ${stockChange}")
                stockLore.add("${ChatColor.WHITE}총 가치: ${Financial.moneyDisplay(stockCount * stockConfig.getInt("$item.currentPrice"))}원")
                stockLore.add("${ChatColor.WHITE}좌클릭하여 매수, 우클릭하여 매도합니다.")
                stockLore.add("${ChatColor.WHITE}Shift를 누르고 클릭하면 100주/전량 거래합니다.")
                stockMeta.lore = stockLore
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
        if (event.view.title == StockGUIName) {
            val player = event.whoClicked as Player
            val stockGUI = event.view.topInventory
            val stockConfig = FileManager.getStockConfig()
            val playerConfig = FileManager.getPlayerData(player)
            val item = event.currentItem
            if (item != null && item.hasItemMeta()) {
                val itemMeta = item.itemMeta!!
                val stockName = itemMeta.displayName.substring(2, itemMeta.displayName.indexOf(" ("))
                val stockCount = playerConfig.getInt("stock.$stockName")
                val stockPrice = stockConfig.getInt("$stockName.currentPrice")
                var count = 1

                if (event.isLeftClick) {
                    if (playerConfig.getInt("money") >= stockPrice) {
                        if (event.isShiftClick) {
                            count = min(playerConfig.getInt("money") / stockPrice, 100)
                        }
                        playerConfig["stock.$stockName"] = stockCount + count
                        playerConfig["money"] = playerConfig.getInt("money") - (stockPrice * count)
                        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                        player.sendMessage("${ChatColor.AQUA}${stockName} ${ChatColor.GREEN}${count}${ChatColor.WHITE}주 매수 완료.")
                    }
                    else {
                        player.sendMessage("${ChatColor.RED}돈이 부족합니다!")
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                    }
                }

                if (event.isRightClick) {
                    if (stockCount >= 1) {
                        if (event.isShiftClick) {
                            count = min(stockCount, 100)
                        }
                        playerConfig["stock.$stockName"] = stockCount - count
                        playerConfig["money"] = playerConfig.getInt("money") + (stockPrice * count)
                        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                        player.sendMessage("${ChatColor.AQUA}${stockName} ${ChatColor.GREEN}${count}${ChatColor.WHITE}주 매도 완료.")
                    }
                    else {
                        player.sendMessage("${ChatColor.RED}주식이 부족합니다!")
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