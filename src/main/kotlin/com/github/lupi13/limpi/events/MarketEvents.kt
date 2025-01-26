package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.Financial
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin


class MarketEvents : Listener {
    companion object {
        private val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        val marketGUIName = "${ChatColor.DARK_PURPLE}${ChatColor.BOLD}LIMPI: 장터"

        fun openMarketGUI(player: Player) {
            val marketGUI: Inventory = Bukkit.createInventory(player, 54, marketGUIName)
            val marketConfig = FileManager.getMarketConfig()
            var i = 0
            for (code in marketConfig.getKeys(false)) {
                val item = marketConfig.getItemStack("${code}.item")!!.clone()
                val price = marketConfig.getInt("${code}.price")
                val sellername = marketConfig.getString("${code}.seller")!!
                val meta = item.itemMeta
                val lore = meta?.lore?.toMutableSet() ?: ArrayList()
                lore.add(
                    "${ChatColor.WHITE}가격: ${Financial.moneyDisplay(price)}원, 판매자: ${
                        Financial.playerDisplay(
                            sellername
                        )
                    }"
                )
                lore.add("${ChatColor.WHITE}좌클릭하여 구매, 우클릭하여 흥정을 요청합니다.")
                meta?.lore = lore.toMutableList()
                item.itemMeta = meta
                marketGUI.setItem(i, item)
                i += 1
            }
            player.openInventory(marketGUI)
        }

        fun reopenMarketGUI() {
            val playerList = Functions.getOnlinePlayers()
            for (player in playerList) {
                if (player.openInventory.title == marketGUIName) {
                    openMarketGUI(player)
                }
            }
        }


        fun getRandomPrefix(): String { val prefixList = listOf(
            "돈없는 ", "거지왕 ", "주식 떡락한 ", "패가망신한 ", "부채에 시달리는 ", "롯데리아 케찹도둑 ",
            "산와머니 백댄서 ", "도박쟁이 ", "먹을게 없어서 흙 퍼먹는 ", "롯데마트 시식코너 마스터 ", "길고양이 밥 뺏어먹는 ", "자판기 잔돈 슬롯 점검 전문가 ",
            "공짜 와이파이 추적자 ", "하수구에 500원 동전 빠뜨린 ", "버스비 없어서 버스기사와 눈싸움 하다가 쫒겨난 ", "편의점 폐기 헌터 "
        )
        return if (Math.random() < 0.999) {
            prefixList.random()
        } else {
            ("옘병 땀병에 갈아버릴 속병에 걸려가지고 땀통이 끊어지면은 끝나는 거고 " +
                    "이 시베리아 벌판에서 얼어죽을 년 같으니! 십장생 같은 년! 옘병 땀병에 그냥, " +
                    "땀통 끊어지면은 그냥 죽는 거야, 이 년아. 이런 개나리를 봤나! 야, 이 십장생아! " +
                    "귤 까라 그래! 이 시베리아야! 에라이 쌍화차야! 이 시베리아 벌판에서 귤이나 깔 ")
        }
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title == marketGUIName) {
            val marketGUI = event.view.topInventory
            val player = event.whoClicked as Player
            val marketConfig = FileManager.getMarketConfig()

            if (event.currentItem != null && event.clickedInventory === marketGUI) {
                val config = FileManager.getPlayerData(player)
                val money = config["money"] as Int
                val index = event.slot
                val code = marketConfig.getKeys(false).toList()[index]
                val item = marketConfig.getItemStack("${code}.item")!!
                var itemName = ""
                try {
                    itemName = item.itemMeta?.displayName.toString()
                    if (itemName == "") {
                        itemName = item.type.name.lowercase().replace("_", " ")
                    }
                }
                catch (e: Exception) {
                    itemName = item.type.name
                }
                val price = marketConfig.getInt("${code}.price")
                val sellername = marketConfig.getString("${code}.seller")!!


                if (event.click.isLeftClick) {
                    if (money >= price) {
                        if (Functions.validInventoryIndex(player, item) != null) {
                            if (sellername != player.displayName) {
                                config["money"] = money - price
                                FileManager.savePlayerData(player, config)

                                val sellerUUID = FileManager.getUUIDByName(sellername)
                                val sellerConfig = FileManager.getPlayerData(sellerUUID!!)
                                sellerConfig["money"] = sellerConfig["money"] as Int + (price * 0.95).toInt()
                                FileManager.savePlayerData(sellerUUID, sellerConfig)

                                marketConfig.set(code, null)
                                FileManager.saveMarket()
                                player.inventory.addItem(item)
                                player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                                player.sendMessage("${ChatColor.AQUA}${itemName}(${code})${ChatColor.GREEN} 구매 완료.")

                                val onlineSeller = FileManager.findPlayerByName(sellername)
                                onlineSeller?.playSound(player, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                                onlineSeller?.sendMessage("${Financial.playerDisplay(player)}님이 ${price}원에 ${ChatColor.AQUA}${itemName}(${code})${ChatColor.WHITE}을(를) 구매했습니다.")
                                event.isCancelled = true
                                reopenMarketGUI()
                            }
                            else {
                                player.sendMessage("${ChatColor.RED}자신이 올린 물품은 구매할 수 없습니다.\n장터에 올린 아이템을 환수하고 싶다면 /market remove <code> 명령어를 사용하세요.")
                                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                            }
                        }
                        else {
                            player.sendMessage("${ChatColor.RED}인벤토리가 가득 찼습니다.")
                            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                        }
                    }
                    else {
                        player.sendMessage("${ChatColor.RED}돈이 부족합니다! ${ChatColor.GREEN}${player.displayName}${ChatColor.RED}님은 현재 계좌에 ${ChatColor.GOLD}$money${ChatColor.RED}원 있습니다.")
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                    }
                }
                else if (event.click.isRightClick) {
                    val onlineSeller = FileManager.findPlayerByName(sellername)
                    onlineSeller?.sendMessage("${getRandomPrefix()}${Financial.playerDisplay(player)}님이 ${ChatColor.AQUA}${itemName}(${code})${ChatColor.WHITE}을(를) 깎아달라고 조릅니다.")
                    player.sendMessage("${Financial.playerDisplay(sellername)}님에게 ${ChatColor.AQUA}${itemName}(${code})${ChatColor.WHITE}을(를) 깎아달라고 졸랐습니다.")
                    event.isCancelled = true
                }
                else {
                    event.isCancelled = true
                }
                event.isCancelled = true

            }
            else {
                event.isCancelled = true
            }
            event.isCancelled = true
        }
    }

}
