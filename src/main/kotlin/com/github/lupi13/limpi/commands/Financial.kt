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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
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

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
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

                page = Component.text("/financial send <플레이어> <금액>", NamedTextColor.GREEN)
                page = page.append(Component.text(": 입력한 금액을 대상 플레이어에게 송금합니다. 수수료 5%가 부과됩니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial sell", NamedTextColor.GREEN))
                page = page.append(Component.text(": 아이템을 판매하는 창을 엽니다. 판매할 아이템을 넣고 오른쪽 아래 금을 클릭하면 판매됩니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial shop", NamedTextColor.GREEN))
                page = page.append(Component.text(": 아이템을 구매하는 창을 엽니다.\n\n", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/financial stock", NamedTextColor.GREEN)
                page = page.append(Component.text(": 주식 시장을 엽니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial market", NamedTextColor.GREEN))
                page = page.append(Component.text(": 유저간 거래가 가능한 장터 창을 엽니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/financial market add <코드> <가격>", NamedTextColor.GREEN))
                page = page.append(Component.text(": 손에 든 아이템을 장터에 추가합니다. 코드는 대문자 알파벳, 숫자로 이루어진 4글자입니다.\n\n", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/financial market remove <코드>", NamedTextColor.GREEN)
                page = page.append(Component.text(": 장터에서 아이템을 삭제합니다.\n\n", NamedTextColor.BLACK))
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
                    page = page.append(Component.text(": 다른 사람이 장터에 올린 아이템을 강제로 제거합니다.\n\n", NamedTextColor.BLACK))
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


            //  /fc send <player> <Long>
            if (args[0].equals("send", true)) {
                if (args.size == 3) {
                    val targetName: String = args[1]
                    try {
                        val target: Player = FileManager.findPlayerByName(targetName)!!
                        val targetConfig: FileConfiguration = FileManager.getPlayerData(target)

                        if (target == sender) {
                            sender.sendMessage(Component.text("자기 자신에게 송금할 수 없습니다!", NamedTextColor.RED))
                            return true
                        }

                        val value: Long
                        try {
                            value = args[2].toLong()
                        } catch (e: Exception) {
                            sender.sendMessage(Component.text(args[2], NamedTextColor.YELLOW)
                                .append(Component.text("은(는) 올바르지 않은 숫자입니다!", NamedTextColor.RED)))
                            return true
                        }

                        if ((value * 1.05).toLong() > money) {
                            sender.sendMessage(Component.text("돈이 부족합니다! ", NamedTextColor.RED)
                                .append(playerDisplay(sender))
                                .append(Component.text("님은 현재 계좌에 ", NamedTextColor.RED))
                                .append(moneyDisplay(money))
                                .append(Component.text("원이 있습니다.", NamedTextColor.RED)))
                            sender.sendMessage(Component.text("송금 시에는 5%의 수수료가 송금자에게로부터 추가로 부과됩니다.", NamedTextColor.RED))
                            return true
                        }

                        if (value <= 0) {
                            if (sender.isOp) {
                                sender.sendMessage(Component.text("나쁜 사람...", NamedTextColor.YELLOW))
                            }
                            else {
                                sender.sendMessage(Component.text("그러면 못써요", NamedTextColor.YELLOW))
                                return true
                            }
                        }

                        config["money"] = money - (value * 1.05).toLong()
                        targetConfig["money"] = targetConfig.getLong("money") + value
                        FileManager.savePlayerData(sender, config)
                        FileManager.savePlayerData(target, targetConfig)
                        sender.sendMessage(playerDisplay(target)
                            .append(Component.text("님에게 ", NamedTextColor.WHITE))
                            .append(moneyDisplay(value))
                            .append(Component.text("원을 송금했습니다.", NamedTextColor.WHITE)))
                        sender.sendMessage(Component.text("수수료 5%(", NamedTextColor.WHITE)
                            .append(moneyDisplay((value * 0.05).toLong()))
                            .append(Component.text("원)가 추가로 차감되었습니다.", NamedTextColor.WHITE)))
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F)
                        target.sendMessage(playerDisplay(sender)
                            .append(Component.text("님이 ", NamedTextColor.WHITE))
                            .append(playerDisplay(target))
                            .append(Component.text("님에게 ", NamedTextColor.WHITE))
                            .append(moneyDisplay(value))
                            .append(Component.text("원을 송금했습니다.", NamedTextColor.WHITE)))
                        target.playSound(target, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                        return true

                    } catch (e: Exception) {
                        sender.sendMessage(Component.text("해당 플레이어를 찾을 수 없습니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else {
                    sender.sendMessage(Component.text("잘못된 형식입니다. /financial send <플레이어> <금액>", NamedTextColor.RED))
                }
            }


            //  /fc set <player> <Long>
            if (args[0].equals("set", true)) {
                if (!sender.isOp) {
                    sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
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
                            sender.sendMessage(Component.text(args[2], NamedTextColor.YELLOW)
                                .append(Component.text("은(는) 올바르지 않은 숫자입니다!", NamedTextColor.RED)))
                            return true
                        }

                        if (value < 0) {
                            sender.sendMessage(Component.text("나쁜 사람...", NamedTextColor.YELLOW))
                        }

                        targetConfig["money"] = value
                        FileManager.savePlayerData(target, targetConfig)
                        sender.sendMessage(playerDisplay(target)
                            .append(Component.text("님의 계좌를 ", NamedTextColor.WHITE))
                            .append(moneyDisplay(value))
                            .append(Component.text("원으로 설정했습니다.", NamedTextColor.WHITE)))
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F)

                        target.sendMessage(playerDisplay(target)
                            .append(Component.text("님의 계좌가 ", NamedTextColor.WHITE))
                            .append(moneyDisplay(value)
                            .append(Component.text("원으로 설정되었습니다.", NamedTextColor.WHITE))))
                        target.playSound(target, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                        return true

                    } catch (e: Exception) {
                        sender.sendMessage(Component.text("해당 플레이어를 찾을 수 없습니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else {
                    sender.sendMessage(Component.text("잘못된 형식입니다. /financial set <플레이어> <금액>", NamedTextColor.RED))
                }
            }


            //  /fc add <player> <Long>
            if (args[0].equals("add", true)) {
                if (!sender.isOp) {
                    sender.sendMessage(Component.text("권한이 부족합니다!", NamedTextColor.RED))
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
                            sender.sendMessage(Component.text(args[2], NamedTextColor.YELLOW)
                                .append(Component.text("은(는) 올바르지 않은 숫자입니다!", NamedTextColor.RED)))
                            return true
                        }

                        targetConfig["money"] = value + targetConfig.getLong("money")
                        FileManager.savePlayerData(target, targetConfig)
                        sender.sendMessage(playerDisplay(target)
                            .append(Component.text("님의 계좌에 ", NamedTextColor.WHITE))
                            .append(moneyDisplay(value.absoluteValue))
                            .append(Component.text("원을 ", NamedTextColor.WHITE))
                            .append(Component.text(if (value > 0) "추가" else "차감", NamedTextColor.YELLOW))
                            .append(Component.text("했습니다.", NamedTextColor.WHITE)))
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1F, 2F)

                        target.sendMessage(playerDisplay(target)
                            .append(Component.text("님의 계좌에 ", NamedTextColor.WHITE))
                            .append(moneyDisplay(value.absoluteValue))
                            .append(Component.text("원이 ", NamedTextColor.WHITE))
                            .append(Component.text(if (value > 0) "추가" else "차감", NamedTextColor.YELLOW))
                            .append(Component.text("되었습니다.", NamedTextColor.WHITE)))
                        target.playSound(target, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                        return true

                    } catch (e: Exception) {
                        sender.sendMessage(Component.text("해당 플레이어를 찾을 수 없습니다!", NamedTextColor.RED))
                        return true
                    }
                }
                else {
                    sender.sendMessage(Component.text("잘못된 형식입니다. /financial add <플레이어> <금액>", NamedTextColor.RED))
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
                        if (stockConfig.getKeys(false).size >= 54) {
                            sender.sendMessage(Component.text("주식은 최대 54개까지 생성 가능합니다.", NamedTextColor.RED))
                            return true
                        }

                        val name = args[2]
                        if (stockConfig.getKeys(false).contains(name)) {
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

                        val price: Int
                        try {
                            price = args[5].toInt()
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

                            sender.sendMessage(Component.text("모든 주식을 삭제했습니다.", NamedTextColor.WHITE))
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
                            for (stock in stockConfig.getKeys(false)) {
                                stockConfig["${stock}.initialPrice"] = stockConfig.getInt("${stock}.currentPrice")
                            }
                            FileManager.saveStock()

                            stockConfig["lastStockStand"] = Functions.getDateTime()

                            sender.sendMessage(Component.text("모든 주식의 현재 가격을 초기 가격으로 설정했습니다.", NamedTextColor.WHITE))
                            return true
                        }
                        if (stockConfig.getKeys(false).contains(name)) {
                            stockConfig["${name}.initialPrice"] = stockConfig.getInt("${name}.currentPrice")
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


            //  /fc market
            if (args[0].equals("market", true)) {
                if (args.size == 1) {
                    MarketEvents.openMarketGUI(sender)
                    return true
                }
                else if (args[1].equals("add", true)) {
                    if (args.size != 4) {
                        sender.sendMessage(Component.text("잘못된 형식입니다. /financial market add <코드> <가격>", NamedTextColor.RED))
                        return true
                    }
                    val code = args[2]
                    if (!Pattern.matches("[0-9A-Z]{4}", code)) {
                        sender.sendMessage(Component.text("코드는 대문자 알파벳과 숫자로만 이루어진 4글자로 입력해주세요.", NamedTextColor.RED))
                        return true
                    }
                    val price: Int
                    try {
                        price = args[3].toInt()
                        if (price < 0) {
                            throw Exception()
                        }
                    } catch (e: Exception) {
                        sender.sendMessage(Component.text(args[3], NamedTextColor.YELLOW)
                            .append(Component.text("은(는) 올바르지 않은 가격입니다!", NamedTextColor.RED)))
                        return true
                    }

                    if (sender.inventory.itemInMainHand.type == Material.AIR) {
                        sender.sendMessage(Component.text("손에 아이템을 들고 있어야 합니다.", NamedTextColor.RED))
                        return true
                    }

                    val marketConfig = FileManager.getMarketConfig()

                    if (marketConfig.getKeys(false).size >= 54) {
                        sender.sendMessage(Component.text("장터에 아이템이 가득 찼습니다. (54개 이상은 업데이트 예정)", NamedTextColor.RED))
                        return true
                    }

                    if (marketConfig.getKeys(false).contains(code)) {
                        sender.sendMessage(Component.text("이미 존재하는 코드입니다.", NamedTextColor.RED))
                        return true
                    }

                    val item = sender.inventory.itemInMainHand
                    var name = PlainTextComponentSerializer.plainText().serialize(item.displayName())
                    name = name.substring(1, name.length - 1)
                    if (name == "") {
                        name = item.type.name
                    }

                    marketConfig["$code.item"] = item
                    marketConfig["$code.price"] = price
                    marketConfig["$code.seller"] = sender.name
                    sender.sendMessage(Component.text(name, NamedTextColor.GREEN)
                        .append(Component.text("을(를) 장터에 추가했습니다.", NamedTextColor.WHITE)))
                    sender.inventory.setItemInMainHand(ItemStack(Material.AIR))
                    FileManager.saveMarket()
                }
                else if (args[1].equals("remove", true)) {
                    if (args.size != 3) {
                        sender.sendMessage(Component.text("잘못된 형식입니다. /financial market remove <코드>", NamedTextColor.RED))
                        return true
                    }
                    val code = args[2]
                    val marketConfig = FileManager.getMarketConfig()
                    if (marketConfig.getKeys(false).contains(code)) {
                        if (marketConfig.getString("$code.seller") != sender.name) {
                            if (sender.isOp) {
                                sender.sendMessage(Component.text("다른 사람의 아이템을 관리자 권한으로 삭제합니다.", NamedTextColor.YELLOW))
                            }
                            else {
                                sender.sendMessage(Component.text("다른 사람이 등록한 아이템은 삭제할 수 없습니다.", NamedTextColor.RED))
                                return true
                            }
                        }
                        val item = marketConfig.getItemStack("$code.item")!!
                        var name: String
                        try {
                            name = PlainTextComponentSerializer.plainText().serialize(item.displayName())
                            name = name.substring(1, name.length - 1)
                            if (name == "") {
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
                        sender.sendMessage(Component.text(name, NamedTextColor.GREEN)
                            .append(Component.text("을(를) 장터에서 삭제했습니다.", NamedTextColor.WHITE)))
                        FileManager.saveMarket()
                    }
                    else {
                        sender.sendMessage(Component.text("존재하지 않는 코드입니다.", NamedTextColor.RED))
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