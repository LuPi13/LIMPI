package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.items.Check
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
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
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            val config = FileManager.getPlayerData(sender)
            val money = config.getLong("money")

            if (args.isEmpty() || args[0].equals("help", true)) {
                sender.sendMessage(ChatColor.AQUA.toString() + ChatColor.BOLD + "[/financial 도움말]")
                sender.sendMessage("/fi, /fc 로도 입력할 수 있습니다.")
                sender.sendMessage(ChatColor.GREEN.toString() + "/financial help" + ChatColor.WHITE + ": 이 도움말을 보여줍니다.")
                sender.sendMessage(ChatColor.GREEN.toString() + "/financial me" + ChatColor.WHITE + ": 현재 본인 계좌의 돈을 보여줍니다.")
                sender.sendMessage(ChatColor.GREEN.toString() + "/financial check(or cheque) <금액>" + ChatColor.WHITE + ": 입력한 금액의 수표를 작성합니다. 우클릭하여 사용 가능합니다.")
                sender.sendMessage(ChatColor.GREEN.toString() + "/financial wire(or send) <플레이어> <금액>" + ChatColor.WHITE + ": 입력한 금액을 대상 플레이어에게 송금합니다. 수수료 5%가 부과됩니다.")

                if (sender.isOp) {
                    sender.sendMessage(ChatColor.RED.toString() + "- - - 이하 관리자 전용 명령어 - - -")
                    sender.sendMessage(ChatColor.GREEN.toString() + "/financial spy <플레이어>" + ChatColor.WHITE + ": 대상의 계좌의 돈을 보여줍니다.")
                    sender.sendMessage(ChatColor.GREEN.toString() + "/financial set <플레이어> <금액>" + ChatColor.WHITE + ": 대상의 계좌의 돈을 입력한 금액으로 설정합니다.")
                    sender.sendMessage(ChatColor.GREEN.toString() + "/financial add <플레이어> <금액>" + ChatColor.WHITE + ": 대상의 계좌에 입력한 금액을 추가합니다. 금액에 음수를 적을 시 돈을 차감합니다.")
                }
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
            if (args[0].equals("check", true) || args[0].equals("cheque", true)) {
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


            //  /fc wire <player> <Long>
            if (args[0].equals("wire", true) || args[0].equals("send", true)) {
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
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial wire <플레이어> <금액>")
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