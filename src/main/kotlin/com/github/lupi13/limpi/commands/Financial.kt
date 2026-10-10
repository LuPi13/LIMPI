package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.Functions.Companion.moneyDisplay
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.events.*
import com.github.lupi13.limpi.items.Check
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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

class Financial(private val accountCommands: AccountCommands, private val deliveryCommands: DeliveryCommands, private val enabled: Boolean, private val marketCommands: MarketCommands): CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (deliveryCommands.handles(args)) return deliveryCommands.execute(sender, args)
        if (args.firstOrNull()?.equals("market", true) == true &&
            args.getOrNull(1)?.lowercase(Locale.ROOT) in setOf("escrow", "resolve", "owner")) return marketCommands.execute(sender, args)
        if (!enabled) {
            sender.sendMessage(Component.text("금융 시스템이 비활성화되어 있습니다. 미수령 수령·지급 기록 명령은 사용할 수 있습니다.", NamedTextColor.RED))
            return true
        }
        if (args.firstOrNull()?.equals("market", true) == true) return marketCommands.execute(sender, args)
        if (accountCommands.handles(args)) return accountCommands.execute(sender, args)
        if (sender is Player) {
            val config = FileManager.getPlayerData(sender)
            val money = config.getLong("money")

            if (args.isEmpty() || args[0].equals("help", true)) {
                val book = ItemStack(Material.WRITTEN_BOOK)
                val meta = book.itemMeta as BookMeta
                meta.title = "/financial 도움말"
                meta.author = "LIMPI"

                // 책 작성
                var page = Component.text("\n\n[/financial 도움말]\n", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
                page = page.append(Component.text("/fi, /fc 로도 입력할 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                meta.addPages( page)

                page = Component.text("/financial help", NamedTextColor.GREEN)
                page = page.append(Component.text(": 이 도움말을 보여줍니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial me", NamedTextColor.GREEN))
                page = page.append(Component.text(": 현재 본인 계좌의 돈을 보여줍니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial check <금액>", NamedTextColor.GREEN))
                page = page.append(Component.text(": 입력한 금액의 수표를 작성합니다. 우클릭하여 사용 가능합니다. ", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/financial send <이름 또는 UUID> <금액> [거래 ID]", NamedTextColor.GREEN)
                page = page.append(Component.text(": 오프라인 계좌에도 송금합니다. 수수료 5%는 원 단위로 버립니다. 결과가 불확실하면 같은 거래 ID로 재시도하세요.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial sell", NamedTextColor.GREEN))
                page = page.append(Component.text(": 아이템을 판매하는 창을 엽니다. 판매할 아이템을 넣고 오른쪽 아래 금을 클릭하면 판매됩니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial shop", NamedTextColor.GREEN))
                page = page.append(Component.text(": 아이템을 구매하는 창을 엽니다.\n\n", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/financial claim", NamedTextColor.GREEN)
                page = page.append(Component.text(": 미수령 물품을 받습니다. 전체 수량이 들어갈 공간을 확보하세요.\n\n", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/financial stock", NamedTextColor.GREEN)
                page = page.append(Component.text(": 주식 시장을 엽니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial market", NamedTextColor.GREEN))
                page = page.append(Component.text(": 유저간 거래가 가능한 장터 창을 엽니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial market add <코드> <가격>", NamedTextColor.GREEN))
                page = page.append(Component.text(": 손에 든 아이템을 장터에 추가합니다. 코드는 대문자 알파벳, 숫자로 이루어진 4글자입니다.\n\n", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/financial market remove <코드>", NamedTextColor.GREEN)
                page = page.append(Component.text(": 장터에서 회수하고 소유자의 미수령 물품으로 반환합니다.\n\n", NamedTextColor.BLACK))
                meta.addPages(page)

                if (sender.isOp) {
                    page = Component.text("이하 관리자 전용 명령어\n", NamedTextColor.RED)
                    page = page.append(Component.text("/financial spy <플레이어>", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                    page = page.append(Component.text(": 대상의 계좌의 돈을 보여줍니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/financial set <플레이어> <금액>", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 대상의 계좌의 돈을 입력한 금액으로 설정합니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/financial add <플레이어> <금액>", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 대상의 계좌에 입력한 금액을 추가합니다. 금액에 음수를 적을 시 돈을 차감합니다.\n\n", NamedTextColor.BLACK))
                    meta.addPages(page)

                    page = Component.text("/financial sell set <아이템> <금액>", NamedTextColor.GREEN)
                    page = page.append(Component.text(": 아이템의 판매가격을 설정합니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/financial sell remove <아이템>", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 아이템의 판매가격을 삭제합니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/financial shop set <아이템> <비율>", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 아이템의 구매가격비율을 설정합니다. sell 가격 * 비율이 구매가격이 됩니다.\n\n", NamedTextColor.BLACK))
                    meta.addPages(page)

                    page = Component.text("/financial shop remove <아이템>", NamedTextColor.GREEN)
                    page = page.append(Component.text(": 아이템의 구매가격비율을 삭제합니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/financial stock add <이름> <코드> <아이템> <가격>", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 주식을 추가합니다. 이름은 주식의 이름, 코드는 주식의 코드, 아이템은 주식의 아이템, 가격은 주식의 기준 가격입니다.\n\n", NamedTextColor.BLACK))
                    meta.addPages(page)

                    page = Component.text("/financial stock refix <이름> <코드> <아이템> <가격>", NamedTextColor.GREEN)
                    page = page.append(Component.text(": 주식의 기준 가격을 현재 가격으로 재설정합니다. 주식의 가격은 기준 가격을 향하게 미세하게 조정됩니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/financial stock remove <이름>", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 주식을 삭제합니다.\n\n", NamedTextColor.BLACK))
                    meta.addPages(page)
                    page = Component.text("/financial market remove <코드>", NamedTextColor.GREEN)
                    page = page.append(Component.text(": 관리자 권한으로 회수해 원래 소유자의 미수령 물품으로 반환합니다.\n\n", NamedTextColor.BLACK))
                    meta.addPages(page)
                }

                book.itemMeta = meta
                sender.openBook(book)
                return true
            }


            //  /fc me
            if (args[0].equals("me", true)) {
                sender.sendMessage(playerDisplay(sender)
                    .append(Component.text("님의 계좌에는 ", NamedTextColor.WHITE))
                    .append(moneyDisplay(money))
                    .append(Component.text("원이 있습니다.", NamedTextColor.WHITE)))

            }


            //  /fc spy <player>
            if (args[0].equals("spy", true)) {
                if (!sender.isOp) {
                    sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                    return true
                }
                if (args.size == 2) {
                    val targetName: String = args[1]
                    try {
                        val target: Player = FileManager.findPlayerByName(targetName)!!
                        val targetConfig: FileConfiguration = FileManager.getPlayerData(target)
                        sender.sendMessage(playerDisplay(target)
                            .append(Component.text("님의 계좌에는 ", NamedTextColor.WHITE))
                            .append(moneyDisplay(targetConfig.getLong("money")))
                            .append(Component.text("원이 있습니다.", NamedTextColor.WHITE)))
                    } catch (e: Exception) {
                        sender.sendMessage(Component.text("해당 플레이어를 찾을 수 없습니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else {
                    sender.sendMessage(Component.text("잘못된 형식입니다. /financial spy <플레이어>", NamedTextColor.RED))
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
                        sender.sendMessage(Component.text(args[1], NamedTextColor.YELLOW)
                            .append(Component.text("는 올바르지 않은 숫자입니다!", NamedTextColor.RED)))
                        return true
                    }
                    if (value <= 0) {
                        if (sender.isOp) {
                            sender.sendMessage(Component.text("어디에... 쓰시려구요...?", NamedTextColor.YELLOW))
                        }
                        else {
                            sender.sendMessage(Component.text("대출 안돼요", NamedTextColor.YELLOW))
                            return true
                        }
                    }

                    if (value >= money) {
                        sender.sendMessage(Component.text("돈이 부족합니다! ", NamedTextColor.RED)
                            .append(playerDisplay(sender))
                            .append(Component.text("님은 현재 계좌에 ", NamedTextColor.RED))
                            .append(moneyDisplay(money))
                            .append(Component.text("원이 있습니다.", NamedTextColor.RED)))
                        return true
                    }

                    if (Functions.validInventoryIndex(sender, Check.getCheck(value, sender)) == null) {
                        sender.sendMessage(Component.text("인벤토리가 가득 찼습니다!", NamedTextColor.RED))
                        return true
                    }


                    config["money"] = money - value
                    FileManager.savePlayerData(sender, config)
                    sender.inventory.addItem(Check.getCheck(value, sender))
                    sender.sendMessage(Component.text("수표가 발행되었습니다."))
                    sender.playSound(sender, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1F, 1.3F)
                    return true
                }
                else {
                    sender.sendMessage(Component.text("잘못된 형식입니다. /financial check <금액>", NamedTextColor.RED))
                    return true
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
                                sender.sendMessage(Component.text(args[2], NamedTextColor.YELLOW)
                                    .append(Component.text("은(는) 올바르지 않은 아이템입니다!", NamedTextColor.RED)))
                                return true
                            }
                            val price: Long
                            try {
                                price = args[3].toLong()
                            } catch (e: Exception) {
                                sender.sendMessage(Component.text(args[3], NamedTextColor.YELLOW)
                                    .append(Component.text("은(는) 올바르지 않은 숫자입니다!", NamedTextColor.RED)))
                                return true
                            }
                            FileManager.getSellPriceConfig()[material.name.lowercase(Locale.getDefault())] = price
                            FileManager.saveSellPrice()
                            sender.sendMessage(Component.text(material.name.lowercase(Locale.getDefault()), NamedTextColor.YELLOW)
                                .append(Component.text("의 판매가격을 ", NamedTextColor.WHITE))
                                .append(moneyDisplay(price))
                                .append(Component.text("원으로 설정했습니다.", NamedTextColor.WHITE)))
                            return true
                            }
                        else {
                            sender.sendMessage(Component.text("잘못된 형식입니다. /financial sell set <아이템> <금액>", NamedTextColor.RED))
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else if (args[1].equals("remove", true)) {
                    if (sender.isOp) {
                        if (args.size == 3) {
                            val material = Functions.toMaterial(args[2])
                            if (material == Material.AIR) {
                                sender.sendMessage(Component.text(args[2], NamedTextColor.YELLOW)
                                    .append(Component.text("은(는) 올바르지 않은 아이템입니다!", NamedTextColor.RED)))
                                return true
                            }
                            FileManager.getSellPriceConfig()[material.name.lowercase(Locale.getDefault())] = null
                            FileManager.saveSellPrice()
                            sender.sendMessage(Component.text(material.name.lowercase(Locale.getDefault()), NamedTextColor.YELLOW)
                                .append(Component.text("의 판매가격을 삭제했습니다.", NamedTextColor.WHITE)))
                            return true
                        }
                        else {
                            sender.sendMessage(Component.text("잘못된 형식입니다. /financial sell remove <아이템>", NamedTextColor.RED))
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else {
                    sender.sendMessage(Component.text("잘못된 형식입니다. /financial sell [set/remove]", NamedTextColor.RED))
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
                                sender.sendMessage(Component.text(args[2], NamedTextColor.YELLOW)
                                    .append(Component.text("은(는) 올바르지 않은 아이템입니다!", NamedTextColor.RED)))
                                return true
                            }
                            val ratio: Double
                            try {
                                ratio = args[3].toDouble()
                            } catch (e: Exception) {
                                sender.sendMessage(Component.text(args[3], NamedTextColor.YELLOW)
                                    .append(Component.text("은(는) 올바르지 않은 숫자입니다!", NamedTextColor.RED)))
                                return true
                            }
                            FileManager.getShopRatioConfig()[material.name.lowercase(Locale.getDefault())] = ratio
                            FileManager.saveShopRatio()
                            sender.sendMessage(Component.text(material.name.lowercase(Locale.getDefault()), NamedTextColor.YELLOW)
                                .append(Component.text("의 구매가격비율을 ", NamedTextColor.WHITE))
                                .append(moneyDisplay(ratio))
                                .append(Component.text("배로 설정했습니다.", NamedTextColor.WHITE)))
                            return true
                        }
                        else {
                            sender.sendMessage(Component.text("잘못된 형식입니다. /financial shop set <아이템> <비율>", NamedTextColor.RED))
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else if (args[1].equals("remove", true)) {
                    if (sender.isOp) {
                        if (args.size == 3) {
                            val material = Functions.toMaterial(args[2])
                            if (material == Material.AIR) {
                                sender.sendMessage(Component.text(args[2], NamedTextColor.YELLOW)
                                    .append(Component.text("은(는) 올바르지 않은 아이템입니다!", NamedTextColor.RED)))
                                return true
                            }
                            FileManager.getShopRatioConfig()[material.name.lowercase(Locale.getDefault())] = null
                            FileManager.saveShopRatio()
                            sender.sendMessage(Component.text(material.name.lowercase(Locale.getDefault()), NamedTextColor.YELLOW)
                                .append(Component.text("의 구매가격비율을 삭제했습니다.", NamedTextColor.WHITE)))
                            return true
                        }
                        else {
                            sender.sendMessage(Component.text("잘못된 형식입니다. /financial shop remove <아이템>", NamedTextColor.RED))
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else {
                    sender.sendMessage(Component.text("잘못된 형식입니다. /financial shop [set/remove]", NamedTextColor.RED))
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
                        sender.sendMessage(Component.text("잘못된 형식입니다. /financial stock show", NamedTextColor.RED))
                        return true
                    }
                }

                var stockConfig = FileManager.getStockConfig()
                if (args[1].equals("add", true)) {
                    if (!sender.isOp) {
                        sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                        return true
                    }
                    if (args.size == 6) {
                        val name = args[2]
                        if (StockEvents.codes(stockConfig).any { stockConfig.getString("$it.name") == name }) {
                            sender.sendMessage(Component.text("이미 존재하는 주식입니다.", NamedTextColor.RED))
                            return true
                        }
                        if (name == "all") {
                            sender.sendMessage(Component.text("주식 이름으로 'all'은 사용할 수 없습니다.", NamedTextColor.RED))
                            return true
                        }

                        val code = args[3]
                        if (!Pattern.matches("[0-9A-Z]{4}", code)) {
                            sender.sendMessage(Component.text("코드는 대문자 알파벳과 숫자로만 이루어진 4글자로 입력해주세요.", NamedTextColor.RED))
                            return true
                        }
                        if (stockConfig.getKeys(false).contains(code)) {
                            sender.sendMessage(Component.text("이미 존재하는 코드입니다.", NamedTextColor.RED))
                            return true
                        }

                        val item = Functions.toMaterial(args[4])
                        if (item == Material.AIR) {
                            sender.sendMessage(Component.text("올바르지 않은 아이템입니다.", NamedTextColor.RED))
                            return true
                        }

                        val price: Long
                        try {
                            price = args[5].toLong()
                            if (price <= 0) {
                                throw Exception()
                            }
                        } catch (e: Exception) {
                            sender.sendMessage(Component.text(args[5], NamedTextColor.YELLOW)
                                .append(Component.text("은(는) 올바르지 않은 가격입니다! 양수의 정수로 입력해주세요.", NamedTextColor.RED)))
                            return true
                        }

                        stockConfig["$code.name"] = name
                        stockConfig["$code.material"] = item.name
                        stockConfig["$code.initialPrice"] = price
                        stockConfig["$code.currentPrice"] = price
                        FileManager.saveStock()

                        val miscConfig = FileManager.getMiscConfig()
                        miscConfig["lastStockStand"] = Functions.getDateTime()
                        FileManager.saveMisc()

                        sender.sendMessage(Component.text("주식 ", NamedTextColor.WHITE)
                            .append(Component.text("${name}(${code})", NamedTextColor.AQUA))
                            .append(Component.text("을(를) 추가했습니다.", NamedTextColor.WHITE)))
                    }
                    else {
                        sender.sendMessage(Component.text("잘못된 형식입니다. /financial stock add <이름> <코드> <아이템> <가격>", NamedTextColor.RED))
                        return true
                    }
                }

                if (args[1].equals("remove", true)) {
                    if (!sender.isOp) {
                        sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                        return true
                    }
                    if (args.size == 3) {
                        val name = args[2]
                        if (name == "all") {
                            stockConfig = FileManager.getStockConfig()
                            for (stock in StockEvents.codes(stockConfig)) {
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

                            sender.sendMessage(Component.text("모든 주식을 삭제했습니다.", NamedTextColor.WHITE))
                            return true
                        }
                        if (name in StockEvents.codes(stockConfig)) {
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

                            sender.sendMessage(Component.text("주식 ", NamedTextColor.WHITE)
                                .append(Component.text(name, NamedTextColor.AQUA))
                                .append(Component.text("을(를) 삭제했습니다.", NamedTextColor.WHITE)))
                            return true
                        }
                        else {
                            sender.sendMessage(Component.text("존재하지 않는 주식입니다.", NamedTextColor.RED))
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(Component.text("잘못된 형식입니다. /financial stock remove [<이름>/all]", NamedTextColor.RED))
                        return true
                    }
                }

                if (args[1].equals("refix", true)) {
                    if (!sender.isOp) {
                        sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
                        return true
                    }

                    if (args.size == 3) {
                        val name = args[2]
                        if (name == "all") {
                            stockConfig = FileManager.getStockConfig()
                            for (stock in StockEvents.codes(stockConfig)) {
                                stockConfig["${stock}.initialPrice"] = stockConfig.getLong("${stock}.currentPrice")
                            }
                            FileManager.saveStock()

                            val miscConfig = FileManager.getMiscConfig()
                            miscConfig["lastStockStand"] = Functions.getDateTime()
                            FileManager.saveMisc()

                            sender.sendMessage(Component.text("모든 주식의 현재 가격을 초기 가격으로 설정했습니다.", NamedTextColor.WHITE))
                            return true
                        }
                        if (name in StockEvents.codes(stockConfig)) {
                            stockConfig["${name}.initialPrice"] = stockConfig.getLong("${name}.currentPrice")
                            FileManager.saveStock()

                            val miscConfig = FileManager.getMiscConfig()
                            miscConfig["lastStockStand"] = Functions.getDateTime()
                            FileManager.saveMisc()

                            sender.sendMessage(Component.text("주식 ", NamedTextColor.WHITE)
                                .append(Component.text(name, NamedTextColor.AQUA))
                                .append(Component.text("의 현재 가격을 초기 가격으로 설정했습니다.", NamedTextColor.WHITE)))
                            return true
                        }
                        else {
                            sender.sendMessage(Component.text("존재하지 않는 주식입니다.", NamedTextColor.RED))
                            return true
                        }
                    }
                    else {
                        sender.sendMessage(Component.text("잘못된 형식입니다. /financial stock refix [<이름>/all]", NamedTextColor.RED))
                        return true
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
