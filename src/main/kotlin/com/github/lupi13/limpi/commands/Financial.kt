package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.items.Check
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Financial: CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            var config = FileManager.getPlayerData(sender)
            var money = config.getLong("money")

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
                    sender.sendMessage(ChatColor.GREEN.toString() + "/financial add <플레이어> <금액>" + ChatColor.WHITE + ": 대상의 계좌에 입력한 금액을 추가합니다.")
                    sender.sendMessage(ChatColor.GREEN.toString() + "/financial subtract <플레이어> <금액>" + ChatColor.WHITE + ": 대상의 계좌에 입력한 금액을 차감합니다.")
                }
                return true
            }


            //  /fc me
            if (args[0].equals("me", true)) {
                sender.sendMessage(ChatColor.GREEN.toString() + sender.displayName + ChatColor.WHITE + "님의 계좌에는 ${money}원이 있습니다.")
            }


            //  /fc check <Long>
            if (args[0].equals("check", true) || args[0].equals("cheque", true)) {
                if (args.size == 2) {
                    var value: Long
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
                        sender.sendMessage(ChatColor.RED.toString() + "돈이 부족합니다! " + ChatColor.GREEN + sender.displayName + ChatColor.RED + "님은 현재 계좌에 " + ChatColor.GOLD + money + ChatColor.RED + "원 있습니다.")
                        return true
                    }


                    config["money"] = money - value
                    FileManager.savePlayerData(sender, config)
                    sender.inventory.addItem(Check(value, sender))
                    sender.sendMessage("수표가 발행되었습니다.")
                    sender.playSound(sender.location, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1F, 1.3F)
                }
                else {
                    sender.sendMessage(ChatColor.RED.toString() + "잘못된 형식입니다. /financial check <금액>")
                    return true
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