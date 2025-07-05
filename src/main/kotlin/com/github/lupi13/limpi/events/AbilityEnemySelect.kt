package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.events.EnemyCategory.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

enum class EnemyCategory(val displayName: Component) {
    TAMED(Component.text("길들여진 몹", NamedTextColor.LIGHT_PURPLE)),
    NON_AGGRESSIVE(Component.text("비공격적", NamedTextColor.GREEN)),
    NEUTRAL(Component.text("중립적", NamedTextColor.YELLOW)),
    AGGRESSIVE(Component.text("공격적", NamedTextColor.RED)),
    PLAYER(Component.text("플레이어", NamedTextColor.BLUE)) // 플레이어는 특별한 범주로 분리
}

class AbilityEnemySelect: Listener {
    companion object {
        private val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        val enemySelectGUIName = Component.text("LIMPI: 적 범위 선택", NamedTextColor.DARK_RED, TextDecoration.BOLD)


        /**
         * 적 선택 GUI의 아이템을 생성합니다.
         * 플레이어 파일에 의해 아이템의 로어가 표시됩니다.
         * @param player 플레이어
         * @return 생성된 ItemStack
         */
        fun getEnemySelectItem(player: Player, category: EnemyCategory): ItemStack {
            val material = when (category) {
                TAMED -> Material.WOLF_SPAWN_EGG
                NON_AGGRESSIVE -> Material.CHICKEN_SPAWN_EGG
                NEUTRAL -> Material.BEE_SPAWN_EGG
                AGGRESSIVE -> Material.ZOMBIE_SPAWN_EGG
                PLAYER -> Material.PLAYER_HEAD
            }
            val item = ItemStack(material, 1)
            val meta = item.itemMeta
            val lore = mutableListOf<Component>()
            if (category == TAMED) {
                lore.add(Component.text("아무 플레이어가 길들인 몹은 공격하지 않음", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("나 또는 같은 Team이 길들인 몹은 공격하지 않음", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("항상 공격", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("길들여진 몹에 대해 예외처리를 할 수 있습니다.", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("예를들어 ",NamedTextColor.WHITE).append(Component.text("중립적", NamedTextColor.YELLOW))
                    .append(Component.text("에 속하는 늑대가 공격 대상이어도,", NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("길들인 늑대는 피해를 주지 않게 할 수 있습니다.", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
            }
            else if (category == PLAYER) {
                lore.add(Component.text("공격하지 않음", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("같은 Team만 공격하지 않음", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("항상 공격", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("Team은 서버 관리자가 마인크래프트 명령어", NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("/team을 통해 설정할 수 있습니다.", NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                (meta as SkullMeta).owningPlayer = player
            }
            else {
                lore.add(Component.text("공격하지 않음", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("나를 적대시할 때만 공격", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("항상 공격", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                when (category) {
                    NON_AGGRESSIVE -> {
                        lore.add(
                            Component.text("일반적으로 플레이어를 공격하지 않는 몹입니다.", NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                        lore.add(
                            Component.text("닭, 소, 눈골렘, 주민 등이 해당됩니다.", NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                    }

                    NEUTRAL -> {
                        lore.add(
                            Component.text("특정 조건에서 플레이어를 공격하는 몹입니다.", NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                        lore.add(
                            Component.text("벌, 거미, 엔더맨, 철골렘 등이 해당됩니다.", NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                    }

                    AGGRESSIVE -> {
                        lore.add(
                            Component.text("일반적으로 플레이어를 선공하는 몹입니다.", NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                        lore.add(
                            Component.text("좀비, 크리퍼, 우민, 피글린 등이 해당됩니다.", NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                    }

                    else -> {}
                }
            }
            lore.add(Component.text("클릭하여 범위를 변경합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))

            val playerConfig = FileManager.getPlayerData(player)
            val targetLevel = playerConfig.getInt("AbilityEnemy.${category.name}", 2)
            for (i in 0..2) {
                if (i == targetLevel) {
                    lore[i] = lore[i].color(NamedTextColor.GREEN).decoration(TextDecoration.STRIKETHROUGH, false)
                } else {
                    lore[i] = lore[i].color(NamedTextColor.GRAY).decoration(TextDecoration.STRIKETHROUGH, true)
                }
            }

            meta.lore(lore)
            meta.displayName(category.displayName.decoration(TextDecoration.ITALIC, false))
            item.itemMeta = meta
            return item
        }


        /**
         * 적 선택 GUI를 생성합니다.
         * @param player 플레이어
         */
        fun openEnemySelectGUI(player: Player) {
            val enemySelectGUI: Inventory = Bukkit.createInventory(player, 9, enemySelectGUIName)

            enemySelectGUI.setItem(0, getEnemySelectItem(player, NON_AGGRESSIVE))
            enemySelectGUI.setItem(2, getEnemySelectItem(player, NEUTRAL))
            enemySelectGUI.setItem(4, getEnemySelectItem(player, TAMED))
            enemySelectGUI.setItem(6, getEnemySelectItem(player, AGGRESSIVE))
            enemySelectGUI.setItem(8, getEnemySelectItem(player, PLAYER))

            player.openInventory(enemySelectGUI)
        }
    }


    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != enemySelectGUIName) return
        event.isCancelled = true
        val enemySelectGUI = event.view.topInventory
        val player = event.whoClicked as Player
        if (event.currentItem == null || event.clickedInventory !== enemySelectGUI) return

        val item = event.currentItem!!
        event.isCancelled = true

        if (item.type == Material.AIR) return // 빈 슬롯 클릭

        val category = when (event.slot) {
            0 -> NON_AGGRESSIVE
            2 -> NEUTRAL
            4 -> TAMED
            6 -> AGGRESSIVE
            8 -> PLAYER
            else -> return // 잘못된 슬롯 클릭
        }

        val playerConfig = FileManager.getPlayerData(player)
        val currentLevel = playerConfig.getInt("AbilityEnemy.${category.name}", 2)
        val upDown = if (event.isLeftClick) 1 else -1
        var newLevel = currentLevel + upDown
        if (newLevel > 2) newLevel = 0
        if (newLevel < 0) newLevel = 2

        playerConfig.set("AbilityEnemy.${category.name}", newLevel)
        FileManager.savePlayerData(player, playerConfig)

        when (newLevel) {
            0 -> player.playSound(player.location, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f, 1.5f)
            1 -> player.playSound(player.location, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 1.5f)
            2 -> player.playSound(player.location, Sound.ENTITY_VEX_CHARGE, 1f, 1.5f)
        }
        openEnemySelectGUI(player)
    }
}