package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.ExpAbsorber
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLevelChangeEvent

object TerraBurning : Ability(
    grade = Grade.LEGENDARY,
    element = Element.NONE,
    displayName = Component.text("테라버닝", NamedTextColor.YELLOW),
    codeName = "terra_burning",
    material = Material.NETHER_STAR,
    description = listOf(
        Component.text("레벨업 시 2레벨을 추가로 상승합니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
    ),
    relatedQuest = listOf(ExpAbsorber)
){


    @EventHandler
    fun onLevelChange(event: PlayerLevelChangeEvent) {
        val player = event.player
        if (player.ability != this) return

        // 레벨업 시 2레벨을 추가로 상승
        player.level = event.newLevel + 2
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1f)

        // 퀘스트 ExpMaster 완료
        if (player.level >= 100) {
            QuestManager.clearQuests(player, ExpAbsorber)
        }
    }
}