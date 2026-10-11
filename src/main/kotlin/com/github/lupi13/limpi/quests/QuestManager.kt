package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.AbilityManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.entity.Player

object QuestManager {
    /**
     * 퀘스트들의 리스트입니다.
     */
    val quests = listOf(
        ExpAbsorber, DoEndermansDreamOfFreeingEnd, UndeadCooperation, LineBetweenSwordAndArrow
    )


    /**
     * codeName으로 퀘스트를 찾습니다.
     * @param codeName 퀘스트의 코드네임
     * @return 해당 코드네임을 가진 Quest, 없으면 null
     */
    fun getQuestByCodeName(codeName: String): Quest? {
        return quests.find { it.codeName == codeName }
    }

    /**
     * 플레이어가 완료한 퀘스트를 확인합니다.
     * @param player 퀘스트를 확인할 플레이어
     * @param quest 확인할 퀘스트
     * @return 해당 퀘스트가 완료되었는지 여부
     */
    fun isClearedQuest(player: Player, quest: Quest): Boolean {
        val config = FileManager.getPlayerData(player)
        val clearedQuests = config.getStringList("clearedQuests")
        return clearedQuests.contains(quest.codeName)
    }

    /**
     * 플레이어가 퀘스트를 완료했을 때 호출되는 메소드입니다.
     * 퀘스트를 완료하면 해당 퀘스트의 보상을 지급합니다.
     * @param player 퀘스트를 완료한 플레이어
     * @param quest 완료한 퀘스트
     */
    fun clearQuests(player: Player, quest: Quest) {
        val result = FileManager.quest.complete(player.uniqueId, quest.codeName, quest.rewardAbility.codeName)
        notify(player, quest, result)
    }

    fun harmonyHit(player: Player, branch: String) {
        notify(player, LineBetweenSwordAndArrow, FileManager.quest.harmony(java.util.UUID.randomUUID(), player.uniqueId, branch))
    }

    private fun notify(player: Player, quest: Quest, result: com.github.lupi13.limpi.quest.QuestRewardResult) {
        if (result.replayed) return
        if (!result.newCompletion && !result.newUnlock) return
        if (result.newCompletion) player.sendMessage(Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(quest.displayName))
        if (result.newUnlock) player.sendMessage(Component.text("능력 ", NamedTextColor.WHITE)
            .append(quest.rewardAbility.displayName)
            .append(Component.text("이(가) 해금되었습니다!", NamedTextColor.WHITE)))
        if (result.newCompletion) player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
    }

    /**
     * 이 능력이 어떤 퀘스트의 보상인지 확인합니다.
     * @param ability 능력
     */
    fun getQuestByRewardAbility(ability: Ability): Quest? {
        return quests.find { it.rewardAbility == ability }
    }
}
