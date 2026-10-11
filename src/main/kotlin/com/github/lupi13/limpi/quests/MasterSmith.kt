package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.abilities.MetalSmith
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

object MasterSmith : Quest(
    displayText = Component.text("마스터 대장장이", NamedTextColor.AQUA),
    codeName = "master_smith",
    isHidden = false,
    howToGet = Component.text("연금술", NamedTextColor.GOLD)
        .append(Component.text(", ", NamedTextColor.WHITE))
        .append(Component.text("뜨거운 곡괭이", NamedTextColor.DARK_RED))
        .append(Component.text(", ", NamedTextColor.WHITE))
        .append(Component.text("에메랄드 수리", NamedTextColor.GREEN))
        .append(Component.text("를 모두 해금하세요.", NamedTextColor.WHITE)),
    rewardAbility = MetalSmith
) {
    /** 이 세 능력이 모두 해금되어 있으면 퀘스트를 달성합니다. */
    val required = listOf("alchemy", "hot_pickaxe", "emerald_fix")

    /**
     * 해금 목록은 DB(뽑기 정산, 퀘스트 보상)에서 커밋되므로 접속, 뽑기 공개, 선택 적용 뒤에 확인합니다.
     * 이미 달성했거나 프로필을 읽을 수 없으면 아무것도 하지 않습니다.
     */
    fun check(player: Player) {
        if (!player.isOnline) return
        try {
            val profile = FileManager.getPlayerData(player)
            if (codeName in profile.getStringList("clearedQuests")) return
            if (!profile.getStringList("unlockedAbilities").containsAll(required)) return
            QuestManager.clearQuests(player, this)
        } catch (failure: Exception) {
            org.bukkit.plugin.java.JavaPlugin.getPlugin(com.github.lupi13.limpi.LIMPI::class.java).logger
                .log(java.util.logging.Level.WARNING, "Master smith quest check failed for ${player.uniqueId}", failure)
        }
    }
}
