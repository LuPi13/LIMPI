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
        ExpAbsorber, DoEndermansDreamOfFreeingEnd, UndeadCooperation, LineBetweenSwordAndArrow,
        DodgeMaster, YourDodge, EnoughDodge, ShouldDodge, DodgeCliche, ColdDodge,
        PerfectDistance, FireStrike, MasterSmith, MoebiusReactor, IntoTheDark,
        GiveAll, NogadaKing, TriggerFinger, NoHurt, BoldForBoom, LightTime, DarkTime, Lotto, WhyDidYouDoThat
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

    /** Count one step of a counted quest (e.g. "use dodge 100 times"); completes it when [target] is reached. */
    fun progress(player: Player, quest: Quest, counter: String, target: Long) {
        notify(player, quest, FileManager.quest.progress(java.util.UUID.randomUUID(), player.uniqueId, counter, target,
            quest.codeName, quest.rewardAbility.codeName))
    }

    fun progress(player: Player, quest: Quest, amount: Long = 1) {
        val definition = requireNotNull(quest.progress)
        progress(player, quest, definition.counter, amount, definition.target)
    }

    fun showProgress(player: Player) {
        val profile = FileManager.getPlayerData(player)
        val cleared = profile.getStringList("clearedQuests")
        quests.forEach { quest ->
            val done = quest.codeName in cleared
            player.sendMessage(quest.displayText.append(Component.text(if (done) " [달성]" else " [미달성]", if (done) NamedTextColor.GREEN else NamedTextColor.GRAY))
                .append(Component.text(" / 보상: ", NamedTextColor.WHITE)).append(quest.rewardAbility.displayName))
            player.sendMessage(quest.visibleCondition(profile, done))
            quest.visibleProgress(profile, done).forEach { text ->
                player.sendMessage(Component.text(text, NamedTextColor.WHITE))
            }
        }
    }

    /** Add [amount] (non-negative) to an accumulated quest counter; completes it when [target] is reached. */
    fun progress(player: Player, quest: Quest, counter: String, amount: Long, target: Long) {
        if (amount <= 0) return
        notify(player, quest, FileManager.quest.progress(java.util.UUID.randomUUID(), player.uniqueId, counter, amount, target,
            quest.codeName, quest.rewardAbility.codeName))
    }

    /**
     * Count one use of a base ability in the durable counter `quest.<counter>` (capped at [cap]) for hidden-quest
     * hints. Skips the DB write once the cached profile already reached [cap]; a failure is logged, never thrown.
     */
    fun recordUse(player: Player, counter: String, cap: Long) {
        try {
            val raw = FileManager.getPlayerData(player).get("quest.$counter")
            if ((raw is Int || raw is Long) && (raw as Number).toLong() >= cap) return
            FileManager.quest.count(java.util.UUID.randomUUID(), player.uniqueId, counter, cap)
        } catch (failure: Exception) {
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(QuestManager::class.java).logger.log(java.util.logging.Level.WARNING,
                "Unable to record quest counter $counter for ${player.uniqueId}", failure)
        }
    }

    /** [recordUse] for the counter and threshold of [quest]'s hint. */
    fun recordUse(player: Player, quest: Quest) {
        val hint = quest.hint ?: return
        recordUse(player, hint.counter, hint.threshold)
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
