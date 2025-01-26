package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.events.*
import com.github.lupi13.limpi.items.Check
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import java.util.*
import java.util.regex.Pattern
import kotlin.math.absoluteValue

class Financial: CommandExecutor {

    companion object {
        /**
         * 이름 표시 앞에 연두색, 뒤에 흰색 넣어줌
         */
        fun playerDisplay(player: Player): String {
            return ChatColor.GREEN.toString() + player.name + ChatColor.WHITE
        }
        fun playerDisplay(player: String): String {
            return ChatColor.GREEN.toString() + player + ChatColor.WHITE
        }

        /**
         * 돈 표시 앞에 금색, 뒤에 흰색 넣어줌
         */
        fun moneyDisplay(value: Long): String {
            return ChatColor.GOLD.toString() + value + ChatColor.WHITE
        }
        fun moneyDisplay(value: Double): String {
            return ChatColor.GOLD.toString() + value + ChatColor.WHITE
        }
        fun moneyDisplay(value: Int): String {
            return ChatColor.GOLD.toString() + value + ChatColor.WHITE
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            val config = FileManager.getPlayerData(sender)
            val money = config.getLong("money")

            if (args.isEmpty() || args[0].equals("help", true)) {
                val book = ItemStack(Material.WRITTEN_BOOK)
                val meta = book.itemMeta as BookMeta
                meta.title = "/financial 도움말"
                meta.author = "LIMPI"
                val pages = mutableListOf<String>()
                var page = StringBuilder()
                page.append(ChatColor.DARK_AQUA.toString() + ChatColor.BOLD + "\n\n[/financial 도움말]\n" +
                        "${ChatColor.RESET}${ChatColor.GRAY}/fi, /fc 로도 입력할 수 있습니다.")
                pages.add(page.toString())
                page = StringBuilder()
                page.append(ChatColor.GREEN.toString() + "/financial help" + ChatColor.BLACK + ": 이 도움말을 보여줍니다.\n\n" +
                        ChatColor.GREEN.toString() + "/financial me" + ChatColor.BLACK + ": 현재 본인 계좌의 돈을 보여줍니다.\n\n" +
                        ChatColor.GREEN.toString() + "/financial check <금액>" + ChatColor.BLACK + ": 입력한 금액의 수표를 작성합니다. 우클릭하여 사용 가능합니다. ${ChatColor.STRIKETHROUGH}모루로 이름 바꾸면 재밌어집니다.")
                pages.add(page.toString())
                page = StringBuilder()
                page.append(ChatColor.GREEN.toString() + "/financial send <플레이어> <금액>" + ChatColor.BLACK + ": 입력한 금액을 대상 플레이어에게 송금합니다. 수수료 5%가 부과됩니다.\n\n" +
                        ChatColor.GREEN.toString() + "/financial sell" + ChatColor.BLACK + ": 아이템을 판매하는 창을 엽니다. 판매할 아이템을 넣고 오른쪽 아래 금을 클릭하면 판매됩니다.\n\n" +
                        ChatColor.GREEN.toString() + "/financial shop" + ChatColor.BLACK + ": 아이템을 구매하는 창을 엽니다.")
                pages.add(page.toString())
                page = StringBuilder()
                page.append(ChatColor.GREEN.toString() + "/financial stock" + ChatColor.BLACK + ": 주식 시장을 엽니다.\n\n" +
                        ChatColor.GREEN.toString() + "/financial stock show" + ChatColor.BLACK + ": 주식 정보를 확인합니다.")
                pages.add(page.toString())
                if (sender.isOp) {
                    page = StringBuilder()
                    page.append(ChatColor.RED.toString() + "이하 관리자 전용 명령어\n" +
                            ChatColor.GREEN.toString() + "/financial spy <플레이어>" + ChatColor.BLACK + ": 대상의 계좌의 돈을 보여줍니다.\n\n" +
                            ChatColor.GREEN.toString() + "/financial set <플레이어> <금액>" + ChatColor.BLACK + ": 대상의 계좌의 돈을 입력한 금액으로 설정합니다.\n\n" +
                            ChatColor.GREEN.toString() + "/financial add <플레이어> <금액>" + ChatColor.BLACK + ": 대상의 계좌에 입력한 금액을 추가합니다. 금액에 음수를 적을 시 돈을 차감합니다.")
                    pages.add(page.toString())
                    page = StringBuilder()
                    page.append(ChatColor.GREEN.toString() + "/financial sell set <아이템> <금액>" + ChatColor.BLACK + ": 아이템의 판매가격을 설정합니다.\n\n" +
                            ChatColor.GREEN.toString() + "/financial sell remove <아이템>" + ChatColor.BLACK + ": 아이템의 판매가격을 삭제합니다.\n\n" +
                            ChatColor.GREEN.toString() + "/financial shop set <아이템> <비율>" + ChatColor.BLACK + ": 아이템의 구매가격비율을 설정합니다. sell 가격 * 비율이 구매가격이 됩니다.")
                    pages.add(page.toString())
                    page = StringBuilder()
                    page.append(ChatColor.GREEN.toString() + "/financial shop remove <아이템>" + ChatColor.BLACK + ": 아이템의 구매가격비율을 삭제합니다.\n\n" +
                            ChatColor.GREEN.toString() + "/financial stock add <이름> <코드> <아이템> <가격>" + ChatColor.BLACK + ": 주식을 추가합니다.\n\n" +
                            ChatColor.GREEN.toString() + "/financial stock remove <이름>" + ChatColor.BLACK + ": 주식을 삭제합니다.")
                    pages.add(page.toString())
                    page = StringBuilder()
                    page.append(ChatColor.GREEN.toString() + "/financial stock refix <이름> <코드> <아이템> <가격>" + ChatColor.BLACK + ": 주식의 기준 가격을 현재 가격으로 재설정합니다. 주식의 가격은 기준 가격을 향하게 미세하게 조정됩니다.")
                    pages.add(page.toString())
                }
                meta.pages = pages
                book.itemMeta = meta
                sender.openBook(book)
                return true
            }


            //  /fc me
            if (args[0].equals("me", true)) {
                sender.sendMessage(playerDisplay(sender) + "님의 계좌에는 " + moneyDisplay(money) + "원이 있습니다.")
            }


            //  /fc spy <player>
            if (args[0].equals("spy", true)) {
                if (!sender.isOp) {
                    sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                    return true
                }
                if (args.size == 2) {
                    val targetName: String = args[1]
                    try {
                        val target: Player = FileManager.findPlayerByName(targetName)!!
                        val targetConfig: FileConfiguration = FileManager.getPlayerData(target)
                        sender.sendMessage(playerDisplay(targetName) + "님의 계좌에는 " + moneyDisplay(targetConfig.getLong("money")) + "원이 있습니다.")
                    } catch (e: Exception) {
                        sender.sendMessage(ChatColor.RED.toString() + "해당 플레이어를 찾을 수 없습니다!")
                        return true
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial spy <플레이어>")
                    return true
                }
            }


            //  /fc check <Long>
            if (args[0].equals("check", true)) {
                if (args.size == 2) {
                    val value: Long
                    try {
                        value = args[1].toLong()
                    } catch (e: Exception) {
                        sender.sendMessage(ChatColor.YELLOW.toString() + args[1] + ChatColor.RED + "는 올바르지 않은 숫자입니다!")
                        return true
                    }
                    if (value <= 0) {
                        if (sender.isOp) {
                            sender.sendMessage(ChatColor.YELLOW.toString() + "어디에... 쓰시려구요...?")
                        }
                        else {
                            sender.sendMessage(ChatColor.YELLOW.toString() + "대출 안돼요")
                            return true
                        }
                    }

                    if (value >= money) {
                        sender.sendMessage(ChatColor.RED.toString() + "돈이 부족합니다! " + playerDisplay(sender) + ChatColor.RED + "님은 현재 계좌에 " + moneyDisplay(money) + ChatColor.RED + "원 있습니다.")
                        return true
                    }

                    if (Functions.validInventoryIndex(sender, Check(value, sender)) == null) {
                        sender.sendMessage(ChatColor.RED.toString() + "인벤토리가 가득 찼습니다!")
                        return true
                    }


                    config["money"] = money - value
                    FileManager.savePlayerData(sender, config)
                    sender.inventory.addItem(Check(value, sender))
                    sender.sendMessage("수표가 발행되었습니다.")
                    sender.playSound(sender, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1F, 1.3F)
                    return true
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial check <금액>")
                    return true
                }
            }


            //  /fc send <player> <Long>
            if (args[0].equals("send", true)) {
                if (args.size == 3) {
                    val targetName: String = args[1]
                    try {
                        val target: Player = FileManager.findPlayerByName(targetName)!!
                        val targetConfig: FileConfiguration = FileManager.getPlayerData(target)

                        if (target == sender) {
                            sender.sendMessage(ChatColor.RED.toString() + "안돼 돌아가")
                            return true
                        }

                        val value: Long
                        try {
                            value = args[2].toLong()
                        } catch (e: Exception) {
                            sender.sendMessage(ChatColor.YELLOW.toString() + args[2] + ChatColor.RED + "는 올바르지 않은 숫자입니다!")
                            return true
                        }

                        if ((value * 1.05).toLong() > money) {
                            sender.sendMessage(ChatColor.RED.toString() + "돈이 부족합니다! " + playerDisplay(sender) + ChatColor.RED + "님은 현재 계좌에 " + moneyDisplay(money) + ChatColor.RED + "원 있습니다.")
                            sender.sendMessage(ChatColor.RED.toString() + "송금 시에는 5%의 수수료가 송금자에게로부터 추가로 부과됩니다.")
                            return true
                        }

                        if (value <= 0) {
                            if (sender.isOp) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + "나쁜 사람...")
                            }
                            else {
                                sender.sendMessage(ChatColor.YELLOW.toString() + "그러면 못써요")
                                return true
                            }
                        }

                        config["money"] = money - (value * 1.05).toLong()
                        targetConfig["money"] = targetConfig.getLong("money") + value
                        FileManager.savePlayerData(sender, config)
                        FileManager.savePlayerData(target, targetConfig)
                        sender.sendMessage("${playerDisplay(target)}님에게 ${moneyDisplay(value)}원을 보냈습니다.")
                        sender.sendMessage("수수료 5%(${moneyDisplay((value * 1.05).toLong())}원)가 추가로 차감되었습니다.")
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F)
                        target.sendMessage("${playerDisplay(sender)}님이 ${playerDisplay(target)}님에게 ${moneyDisplay(value)}원을 송금했습니다.")
                        target.playSound(target, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                        return true

                    } catch (e: Exception) {
                        sender.sendMessage(ChatColor.RED.toString() + "해당 플레이어를 찾을 수 없습니다!")
                        return true
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial send <플레이어> <금액>")
                }
            }


            //  /fc set <player> <Long>
            if (args[0].equals("set", true)) {
                if (!sender.isOp) {
                    sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                    return true
                }
                if (args.size == 3) {
                    val targetName: String = args[1]
                    try {
                        val target: Player = FileManager.findPlayerByName(targetName)!!
                        val targetConfig: FileConfiguration = FileManager.getPlayerData(target)

                        val value: Long
                        try {
                            value = args[2].toLong()
                        } catch (e: Exception) {
                            sender.sendMessage(ChatColor.YELLOW.toString() + args[2] + ChatColor.RED + "는 올바르지 않은 숫자입니다!")
                            return true
                        }

                        if (value < 0) {
                            sender.sendMessage(ChatColor.YELLOW.toString() + "나쁜 사람...")
                        }

                        targetConfig["money"] = value
                        FileManager.savePlayerData(target, targetConfig)
                        sender.sendMessage("${playerDisplay(target)}님의 계좌를 ${moneyDisplay(value)}원으로 설정했습니다.")
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F)
                        target.sendMessage("${playerDisplay(target)}님의 계좌가 ${moneyDisplay(value)}원으로 설정되었습니다.")
                        target.playSound(target, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                        return true

                    } catch (e: Exception) {
                        sender.sendMessage(ChatColor.RED.toString() + "해당 플레이어를 찾을 수 없습니다!")
                        return true
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial set <플레이어> <금액>")
                }
            }


            //  /fc add <player> <Long>
            if (args[0].equals("add", true)) {
                if (!sender.isOp) {
                    sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                    return true
                }
                if (args.size == 3) {
                    val targetName: String = args[1]
                    try {
                        val target: Player = FileManager.findPlayerByName(targetName)!!
                        val targetConfig: FileConfiguration = FileManager.getPlayerData(target)

                        val value: Long
                        try {
                            value = args[2].toLong()
                        } catch (e: Exception) {
                            sender.sendMessage(ChatColor.YELLOW.toString() + args[2] + ChatColor.RED + "는 올바르지 않은 숫자입니다!")
                            return true
                        }

                        targetConfig["money"] = value + targetConfig.getLong("money")
                        FileManager.savePlayerData(target, targetConfig)
                        sender.sendMessage("${playerDisplay(target)}님의 계좌에 ${moneyDisplay(value.absoluteValue)}원을 ${if (value > 0) "추가" else "차감"}했습니다.")
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F)
                        target.sendMessage("${playerDisplay(target)}님의 계좌에 ${moneyDisplay(value.absoluteValue)}원이 ${if (value > 0) "추가" else "차감"}되었습니다.")
                        target.playSound(target, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                        return true

                    } catch (e: Exception) {
                        sender.sendMessage(ChatColor.RED.toString() + "해당 플레이어를 찾을 수 없습니다!")
                        return true
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial add <플레이어> <금액>")
                }
            }


            //  /fc sell
            if (args[0].equals("sell", true)) {
                if (args.size == 1) {
                    SellEvents.openSellGUI(sender)
                    sellTask(sender, sender.openInventory.topInventory)
                    return true
                }
                else if (args[1].equals("set", true)) {
                    if (sender.isOp) {
                        if (args.size == 4) {
                            val material = Functions.toMaterial(args[2])
                            if (material == Material.AIR) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + args[2] + ChatColor.RED + "은(는) 올바르지 않은 아이템입니다!")
                                return true
                            }
                            val price: Long
                            try {
                                price = args[3].toLong()
                            } catch (e: Exception) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + args[3] + ChatColor.RED + "는 올바르지 않은 숫자입니다!")
                                return true
                            }
                            FileManager.getSellPriceConfig()[material.name.lowercase(Locale.getDefault())] = price
                            FileManager.saveSellPrice()
                            sender.sendMessage("${material.name.lowercase(Locale.getDefault())}의 판매가격을 ${moneyDisplay(price)}원으로 설정했습니다.")
                            return true
                            }
                        else {
                            sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial sell set <아이템> <금액>")
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                        return true
                    }
                }
                else if (args[1].equals("remove", true)) {
                    if (sender.isOp) {
                        if (args.size == 3) {
                            val material = Functions.toMaterial(args[2])
                            if (material == Material.AIR) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + args[2] + ChatColor.RED + "은(는) 올바르지 않은 아이템입니다!")
                                return true
                            }
                            FileManager.getSellPriceConfig()[material.name.lowercase(Locale.getDefault())] = null
                            FileManager.saveSellPrice()
                            sender.sendMessage("${material.name.lowercase(Locale.getDefault())}의 판매가격을 삭제했습니다.")
                            return true
                        }
                        else {
                            sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial sell remove <아이템>")
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                        return true
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial sell [set/remove]")
                    return true
                }
            }


            //  /fc shop
            if (args[0].equals("shop", true)) {
                if (args.size == 1) {
                    ShopEvents.openShopGUI(sender)
                    return true
                }
                else if (args[1].equals("set", true)) {
                    if (sender.isOp) {
                        if (args.size == 4) {
                            val material = Functions.toMaterial(args[2])
                            if (material == Material.AIR) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + args[2] + ChatColor.RED + "은(는) 올바르지 않은 아이템입니다!")
                                return true
                            }
                            val ratio: Double
                            try {
                                ratio = args[3].toDouble()
                            } catch (e: Exception) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + args[3] + ChatColor.RED + "는 올바르지 않은 숫자입니다!")
                                return true
                            }
                            FileManager.getShopRatioConfig()[material.name.lowercase(Locale.getDefault())] = ratio
                            FileManager.saveShopRatio()
                            sender.sendMessage("${material.name.lowercase(Locale.getDefault())}의 구매가격비율을 ${moneyDisplay(ratio)}배로 설정했습니다.")
                            return true
                        }
                        else {
                            sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial shop set <아이템> <비율>")
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                        return true
                    }
                }
                else if (args[1].equals("remove", true)) {
                    if (sender.isOp) {
                        if (args.size == 3) {
                            val material = Functions.toMaterial(args[2])
                            if (material == Material.AIR) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + args[2] + ChatColor.RED + "은(는) 올바르지 않은 아이템입니다!")
                                return true
                            }
                            FileManager.getShopRatioConfig()[material.name.lowercase(Locale.getDefault())] = null
                            FileManager.saveShopRatio()
                            sender.sendMessage("${material.name.lowercase(Locale.getDefault())}의 구매가격비율을 삭제했습니다.")
                            return true
                        }
                        else {
                            sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial shop remove <아이템>")
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                        return true
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial shop [set/remove]")
                    return true
                }
            }

            //  /fc stock
            if (args[0].equals("stock", true)) {
                if (args.size == 1) {
                    StockEvents.openStockGUI(sender)
                    return true
                }

                if (args[1].equals("show", true)) {
                    if (args.size == 2) {
                        StockEvents.showBook(sender)
                        return true
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial stock show")
                        return true
                    }
                }

                var stockConfig = FileManager.getStockConfig()
                if (args[1].equals("add", true)) {
                    if (!sender.isOp) {
                        sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                        return true
                    }
                    if (args.size == 6) {
                        if (stockConfig.getKeys(false).size >= 54) {
                            sender.sendMessage(ChatColor.RED.toString() + "주식은 최대 54개까지 생성 가능합니다.")
                            return true
                        }

                        val name = args[2]
                        if (stockConfig.getKeys(false).contains(name)) {
                            sender.sendMessage(ChatColor.RED.toString() + "이미 존재하는 주식입니다.")
                            return true
                        }
                        if (name == "all") {
                            sender.sendMessage(ChatColor.RED.toString() + "시스템 예약어는 사용할 수 없습니다.")
                            return true
                        }

                        val code = args[3]
                        if (!Pattern.matches("[0-9A-Z]{4}", code)) {
                            sender.sendMessage(ChatColor.RED.toString() + "코드는 대문자 알파벳과 숫자로만 이루어진 4글자로 입력해주세요.")
                            return true
                        }
                        if(stockConfig.getKeys(false).any { stockConfig.getString("$it.code") == code }) {
                            sender.sendMessage(ChatColor.RED.toString() + "이미 존재하는 코드입니다.")
                            return true
                        }

                        val item = Functions.toMaterial(args[4])
                        if (item == Material.AIR) {
                            sender.sendMessage(ChatColor.RED.toString() + "올바르지 않은 아이템입니다.")
                            return true
                        }

                        val price: Int
                        try {
                            price = args[5].toInt()
                            if (price <= 0) {
                                throw Exception()
                            }
                        } catch (e: Exception) {
                            sender.sendMessage(ChatColor.RED.toString() + "올바르지 않은 가격입니다.")
                            return true
                        }

                        stockConfig["$name.code"] = code
                        stockConfig["$name.material"] = item.name
                        stockConfig["$name.initialPrice"] = price
                        stockConfig["$name.currentPrice"] = price
                        FileManager.saveStock()

                        val miscConfig = FileManager.getMiscConfig()
                        miscConfig["lastStockStand"] = Functions.getDateTime()
                        FileManager.saveMisc()

                        sender.sendMessage("주식 ${ChatColor.AQUA}$name(${code})${ChatColor.WHITE}을(를) 추가했습니다.")
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial stock add <이름> <코드> <아이템> <가격>")
                        return true
                    }
                }

                if (args[1].equals("remove", true)) {
                    if (!sender.isOp) {
                        sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                        return true
                    }
                    if (args.size == 3) {
                        val name = args[2]
                        if (name == "all") {
                            stockConfig = FileManager.getStockConfig()
                            for (stock in stockConfig.getKeys(false)) {
                                stockConfig.set(stock, null)
                            }
                            FileManager.saveStock()
                            for (player in Functions.getAllPlayers()) {
                                val playerConfig = FileManager.getPlayerData(player)
                                playerConfig.set("stock", null)
                                FileManager.savePlayerData(player, playerConfig)
                            }

                            val miscConfig = FileManager.getMiscConfig()
                            miscConfig["lastStockStand"] = Functions.getDateTime()
                            FileManager.saveMisc()

                            sender.sendMessage("모든 주식을 삭제했습니다.")
                            return true
                        }
                        if (stockConfig.getKeys(false).contains(name)) {
                            stockConfig.set(name, null)
                            FileManager.saveStock()
                            for (player in Functions.getAllPlayers()) {
                                val playerConfig = FileManager.getPlayerData(player)
                                playerConfig.set("stock.$name", null)
                                FileManager.savePlayerData(player, playerConfig)
                            }

                            val miscConfig = FileManager.getMiscConfig()
                            miscConfig["lastStockStand"] = Functions.getDateTime()
                            FileManager.saveMisc()

                            sender.sendMessage("주식 ${ChatColor.AQUA}$name${ChatColor.WHITE}을(를) 삭제했습니다.")
                            return true
                        }
                        else {
                            sender.sendMessage(ChatColor.RED.toString() + "존재하지 않는 주식입니다.")
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial stock remove [<이름>/all]")
                        return true
                    }
                }

                if (args[1].equals("refix", true)) {
                    if (!sender.isOp) {
                        sender.sendMessage(ChatColor.RED.toString() + "권한이 부족합니다!")
                        return true
                    }

                    if (args.size == 3) {
                        val name = args[2]
                        if (name == "all") {
                            stockConfig = FileManager.getStockConfig()
                            for (stock in stockConfig.getKeys(false)) {
                                stockConfig["${stock}.initialPrice"] = stockConfig.getInt("${stock}.currentPrice")
                            }
                            FileManager.saveStock()

                            val miscConfig = FileManager.getMiscConfig()
                            miscConfig["lastStockStand"] = Functions.getDateTime()
                            FileManager.saveMisc()

                            sender.sendMessage("모든 주식의 현재 가격을 초기 가격으로 설정했습니다.")
                            return true
                        }
                        if (stockConfig.getKeys(false).contains(name)) {
                            stockConfig["${name}.initialPrice"] = stockConfig.getInt("${name}.currentPrice")
                            FileManager.saveStock()

                            val miscConfig = FileManager.getMiscConfig()
                            miscConfig["lastStockStand"] = Functions.getDateTime()
                            FileManager.saveMisc()

                            sender.sendMessage("주식 ${ChatColor.AQUA}${name}${ChatColor.WHITE}의 현재 가격을 초기 가격으로 설정했습니다.")
                            return true
                        }
                        else {
                            sender.sendMessage(ChatColor.RED.toString() + "존재하지 않는 주식입니다.")
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial stock refix [<이름>/all]")
                        return true
                    }
                }
            }


            //  /fc market
            if (args[0].equals("market", true)) {
                if (args.size == 1) {
                    MarketEvents.openMarketGUI(sender)
                    return true
                }
                else if (args[1].equals("add", true)) {
                    if (args.size != 4) {
                        sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial market add <코드> <가격>")
                        return true
                    }
                    val code = args[2]
                    if (!Pattern.matches("[0-9A-Z]{4}", code)) {
                        sender.sendMessage(ChatColor.RED.toString() + "코드는 대문자 알파벳과 숫자로만 이루어진 4글자로 입력해주세요.")
                        return true
                    }
                    val price: Int
                    try {
                        price = args[3].toInt()
                        if (price < 0) {
                            throw Exception()
                        }
                    } catch (e: Exception) {
                        sender.sendMessage(ChatColor.RED.toString() + "올바르지 않은 가격입니다.")
                        return true
                    }

                    if (sender.inventory.itemInMainHand.type == Material.AIR) {
                        sender.sendMessage(ChatColor.RED.toString() + "손에 아이템을 들고 있어야 합니다.")
                        return true
                    }

                    val marketConfig = FileManager.getMarketConfig()

                    if (marketConfig.getKeys(false).size >= 54) {
                        sender.sendMessage(ChatColor.RED.toString() + "장터에 아이템이 가득 찼습니다. (54개 이상은 업데이트 예정)")
                        return true
                    }

                    if (marketConfig.getKeys(false).contains(code)) {
                        sender.sendMessage(ChatColor.RED.toString() + "이미 존재하는 코드입니다.")
                        return true
                    }

                    val item = sender.inventory.itemInMainHand
                    var name = item.itemMeta?.displayName
                    if (name == null || name == "") {
                        name = item.type.name
                    }

                    marketConfig["$code.item"] = item
                    marketConfig["$code.price"] = price
                    marketConfig["$code.seller"] = sender.name
                    sender.inventory.setItemInMainHand(ItemStack(Material.AIR))
                    sender.sendMessage("${ChatColor.GREEN}${name}을(를) 장터에 추가했습니다.")
                    FileManager.saveMarket()
                }
                else if (args[1].equals("remove", true)) {
                    if (args.size != 3) {
                        sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial market remove <코드>")
                        return true
                    }
                    val code = args[2]
                    val marketConfig = FileManager.getMarketConfig()
                    if (marketConfig.getKeys(false).contains(code)) {
                        if (marketConfig.getString("$code.seller") != sender.name) {
                            if (sender.isOp) {
                                sender.sendMessage(ChatColor.YELLOW.toString() + "관리자 권한으로 삭제합니다.")
                            }
                            else {
                                sender.sendMessage(ChatColor.RED.toString() + "다른 사람이 등록한 아이템은 삭제할 수 없습니다.")
                                return true
                            }
                        }
                        val item = marketConfig.getItemStack("$code.item")!!
                        var name = ""
                        try {
                            var name = item.itemMeta?.displayName
                            if (name == null || name == "") {
                                name = item.type.name
                            }
                        }
                        catch (e: Exception) {
                            name = item.type.name
                        }
                        if (marketConfig.getString("$code.seller") == sender.name) {
                            sender.inventory.addItem(item)
                        }
                        marketConfig.set(code, null)
                        sender.sendMessage("${ChatColor.GREEN}${name}을(를) 장터에서 삭제했습니다.")
                        FileManager.saveMarket()
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "존재하지 않는 코드입니다.")
                    }
                }
            }
        }





        else {
            if (sender.isOp) {
                TODO("something that can be used by non-player.")
            }
            else {
                return true
            }
        }

        return true
    }
}