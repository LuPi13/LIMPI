package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.Financial
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
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

        val marketGUIName = Component.text("LIMPI: 장터", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)

        fun openMarketGUI(player: Player) {
            val marketGUI: Inventory = Bukkit.createInventory(player, 54, marketGUIName)
            val marketConfig = FileManager.getMarketConfig()
            var i = 0
            for (code in marketConfig.getKeys(false)) {
                val item = marketConfig.getItemStack("${code}.item")!!.clone()
                val price = marketConfig.getInt("${code}.price")
                val sellerName = marketConfig.getString("${code}.seller")!!
                val meta = item.itemMeta
                val lore = meta?.lore()?.toMutableList() ?: mutableListOf()
                lore.add(Component.text("가격: ", NamedTextColor.WHITE)
                    .append(Financial.moneyDisplay(price))
                    .append(Component.text("원, 판매자: ", NamedTextColor.WHITE))
                    .append(Financial.playerDisplay(sellerName).decoration(TextDecoration.ITALIC, false)))
                lore.add(Component.text("좌클릭하여 구매, 우클릭하여 흥정을 요청합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                meta?.lore(lore)
                item.itemMeta = meta
                marketGUI.setItem(i, item)
                i += 1
            }
            player.openInventory(marketGUI)
        }

        fun reopenMarketGUI() {
            val playerList = Functions.getOnlinePlayers()
            for (player in playerList) {
                if (player.openInventory.title() == marketGUIName) {
                    openMarketGUI(player)
                }
            }
        }


        fun getRandomPrefix(): String { val prefixList = listOf(
            "돈없는 ", "거지왕 ", "주식 떡락한 ", "패가망신한 ", "부채에 시달리는 ", "롯데리아 케찹도둑 ",
            "산와머니 백댄서 ", "도박쟁이 ", "먹을게 없어서 흙 퍼먹는 ", "롯데마트 시식코너 마스터 ", "길고양이 밥 뺏어먹는 ", "자판기 잔돈 슬롯 점검 전문가 ",
            "공짜 와이파이 추적자 ", "하수구에 500원 동전 빠뜨린 ", "버스비 없어서 버스기사와 눈싸움 하다가 쫒겨난 ", "편의점 폐기 헌터 ", "사기 수표에 당한 "
        )
        return if (Math.random() < 0.9999) {
            prefixList.random()
        } else {
            ("이 접두어는 0.01% 확률로 등장합니다. 행운아에게 자비를 베풀어 보시는 것은 어떠신가요? 0.01%를 뚫은 ")
        }
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() == marketGUIName) {
            val marketGUI = event.view.topInventory
            val player = event.whoClicked as Player
            val marketConfig = FileManager.getMarketConfig()

            if (event.currentItem != null && event.clickedInventory === marketGUI) {
                val config = FileManager.getPlayerData(player)
                val money = config["money"] as Int
                val index = event.slot
                val code = marketConfig.getKeys(false).toList()[index]
                val item = marketConfig.getItemStack("${code}.item")!!
                var itemName: String
                try {
                    itemName = PlainTextComponentSerializer.plainText().serialize(item.displayName())
                    itemName = itemName.substring(1, itemName.length - 1)
                    if (itemName == "") {
                        itemName = item.type.name.lowercase().replace("_", " ")
                    }
                }
                catch (e: Exception) {
                    itemName = item.type.name
                }
                val price = marketConfig.getInt("${code}.price")
                val sellerName = marketConfig.getString("${code}.seller")!!


                if (event.click.isLeftClick) {
                    if (money >= price) {
                        if (Functions.validInventoryIndex(player, item) != null) {
                            if (sellerName != player.name) {
                                config["money"] = money - price
                                FileManager.savePlayerData(player, config)

                                val sellerUUID = FileManager.getUUIDByName(sellerName)
                                val sellerConfig = FileManager.getPlayerData(sellerUUID!!)
                                sellerConfig["money"] = sellerConfig["money"] as Int + (price * 0.95).toInt()
                                FileManager.savePlayerData(sellerUUID, sellerConfig)

                                marketConfig.set(code, null)
                                FileManager.saveMarket()
                                player.inventory.addItem(item)
                                player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                                player.sendMessage(Component.text("${itemName}(${code})", NamedTextColor.AQUA)
                                    .append(Component.text("을(를) 구매했습니다.", NamedTextColor.WHITE)))

                                val onlineSeller = FileManager.findPlayerByName(sellerName)
                                onlineSeller?.playSound(player, Sound.BLOCK_CHAIN_BREAK, 1F, 1.3F)
                                onlineSeller?.sendMessage(Financial.playerDisplay(player)
                                    .append(Component.text("님이 ", NamedTextColor.WHITE))
                                    .append(Component.text("${itemName}(${code})", NamedTextColor.AQUA))
                                    .append(Component.text("을(를) 구매했습니다.", NamedTextColor.WHITE)))
                                onlineSeller?.sendMessage(Component.text("판매 수수료 5%(", NamedTextColor.WHITE)
                                    .append(Financial.moneyDisplay((price * 0.05).toInt()))
                                    .append(Component.text("원)를 제외하고 계좌에 입금되었습니다.", NamedTextColor.WHITE)))
                                event.isCancelled = true
                                reopenMarketGUI()
                            }
                            else {
                                player.sendMessage(Component.text("자신이 올린 물품은 구매할 수 없습니다.", NamedTextColor.RED)
                                    .append(Component.text("\n장터에 올린 아이템을 환수하고 싶다면 ", NamedTextColor.RED))
                                    .append(Component.text("/market remove <code>", NamedTextColor.RED))
                                    .append(Component.text(" 명령어를 사용하세요.", NamedTextColor.RED)))
                                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                            }
                        }
                        else {
                            player.sendMessage(Component.text("인벤토리가 가득 찼습니다", NamedTextColor.RED))
                            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                        }
                    }
                    else {
                        player.sendMessage(Component.text("돈이 부족합니다! ", NamedTextColor.RED)
                            .append(Financial.playerDisplay(player))
                            .append(Component.text("님은 현재 계좌에 ", NamedTextColor.RED))
                            .append(Financial.moneyDisplay(money))
                            .append(Component.text("원 있습니다.", NamedTextColor.RED)))
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                    }
                }
                else if (event.click.isRightClick) {
                    val onlineSeller = FileManager.findPlayerByName(sellerName)
                    onlineSeller?.sendMessage(Component.text(getRandomPrefix(), NamedTextColor.WHITE)
                        .append(Financial.playerDisplay(player))
                        .append(Component.text("님이 ", NamedTextColor.WHITE))
                        .append(Component.text("${itemName}(${code})", NamedTextColor.AQUA))
                        .append(Component.text("을(를) 깎아달라고 조릅니다.", NamedTextColor.WHITE)))
                    player.sendMessage(Financial.playerDisplay(sellerName)
                        .append(Component.text("님에게 ", NamedTextColor.WHITE))
                        .append(Component.text("${itemName}(${code})", NamedTextColor.AQUA))
                        .append(Component.text("을(를) 깎아달라고 졸랐습니다.", NamedTextColor.WHITE)))
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
