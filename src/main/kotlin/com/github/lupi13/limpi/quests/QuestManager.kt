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
        return FileManager.players.read(player.uniqueId) { it.getStringList("clearedQuests").contains(quest.codeName) }
    }

    /**
     * 플레이어가 퀘스트를 완료했을 때 호출되는 메소드입니다.
     * 퀘스트를 완료하면 해당 퀘스트의 보상을 지급합니다.
     * @param player 퀘스트를 완료한 플레이어
     * @param quest 완료한 퀘스트
     */
    fun clearQuests(player: Player, quest: Quest) {
        // Already cleared with the reward unlocked: completing again would only open a transaction to replay it.
        if (settled(player.uniqueId, quest)) return
        val result = FileManager.quest.complete(player.uniqueId, quest.codeName, quest.rewardAbility.codeName)
        notify(player, quest, result)
    }

    /** Cleared and its reward unlocked, read from the profile cache. */
    private fun settled(owner: java.util.UUID, quest: Quest): Boolean = runCatching {
        FileManager.players.read(owner) {
            quest.codeName in it.getStringList("clearedQuests") && quest.rewardAbility.codeName in it.getStringList("unlockedAbilities")
        }
    }.getOrDefault(false)

    /*
     * Counted quests are bumped on every shot, dodge and hit. Each bump used to be one transaction (profile rewrite,
     * ledger row, fsync) on the main thread. Bumps now gather in memory and are written as one amount when the count
     * would reach its target (so completion and its message stay immediate), every FLUSH_TICKS, before /ability quest,
     * on quit and on shutdown. A crash can lose at most the bumps since the last write.
     */
    private sealed interface Count { val owner: java.util.UUID }
    private data class Progress(override val owner: java.util.UUID, val quest: Quest, val counter: String, val target: Long) : Count
    private data class Harmony(override val owner: java.util.UUID, val branch: String) : Count
    private data class Usage(override val owner: java.util.UUID, val counter: String, val cap: Long) : Count
    private val pending = LinkedHashMap<Count, Long>()
    const val FLUSH_TICKS = 20L * 60

    /** The stored counter from the profile cache: 0 when absent, null when malformed (the transaction reports it). */
    private fun stored(owner: java.util.UUID, counter: String): Long? = runCatching {
        FileManager.players.read(owner) { profile ->
            when (val raw = profile.get("quest.$counter")) {
                null -> 0L
                is Int, is Long -> (raw as Number).toLong().takeIf { it >= 0 }
                else -> null
            }
        }
    }.getOrNull()

    /** Add [amount] to [key]; write now when the stored value plus everything gathered reaches [limit]. */
    private fun gather(key: Count, counter: String, limit: Long, amount: Long, done: () -> Boolean = { false }) {
        val current = stored(key.owner, counter)
        if (current != null && current >= limit && done()) { pending.remove(key); return }
        val total = (pending.remove(key) ?: 0L) + amount
        if (current == null || current + total >= limit) write(key, total) else pending[key] = total
    }

    private fun write(key: Count, amount: Long) {
        val player = org.bukkit.Bukkit.getPlayer(key.owner)
        val request = java.util.UUID.randomUUID()
        when (key) {
            is Progress -> {
                val result = FileManager.quest.progress(request, key.owner, key.counter, amount, key.target, key.quest.codeName, key.quest.rewardAbility.codeName)
                if (player != null) notify(player, key.quest, result)
            }
            is Harmony -> {
                val result = FileManager.quest.harmony(request, key.owner, key.branch, amount)
                if (player != null) notify(player, LineBetweenSwordAndArrow, result)
            }
            is Usage -> FileManager.quest.count(request, key.owner, key.counter, key.cap, amount)
        }
    }

    /** Write everything gathered for [owner] (null: everyone). A failed write is logged and dropped. */
    fun flush(owner: java.util.UUID? = null) {
        val keys = pending.keys.filter { owner == null || it.owner == owner }
        for (key in keys) {
            val amount = pending.remove(key) ?: continue
            try { write(key, amount) } catch (failure: Exception) {
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(QuestManager::class.java).logger.log(java.util.logging.Level.WARNING,
                    "Unable to record $amount quest count(s) for $key", failure)
            }
        }
    }

    /** Count one step of a counted quest (e.g. "use dodge 100 times"); completes it when [target] is reached. */
    fun progress(player: Player, quest: Quest, counter: String, target: Long) = progress(player, quest, counter, 1, target)

    fun progress(player: Player, quest: Quest, amount: Long = 1) {
        val definition = requireNotNull(quest.progress)
        progress(player, quest, definition.counter, amount, definition.target)
    }

    fun showProgress(player: Player) {
        flush(player.uniqueId)
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
        gather(Progress(player.uniqueId, quest, counter, target), counter, target, amount) { settled(player.uniqueId, quest) }
    }

    /**
     * Count one use of a base ability in the durable counter `quest.<counter>` (capped at [cap]) for hidden-quest
     * hints. Gathered like progress and skipped once the cached profile already reached [cap]; a failure is logged, never thrown.
     */
    fun recordUse(player: Player, counter: String, cap: Long) {
        try {
            gather(Usage(player.uniqueId, counter, cap), counter, cap, 1) { true }
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
        val counters = QuestProgressDefinitions.HARMONY.counters
        val target = counters.single { it.counter == branch }.target
        val other = counters.single { it.counter != branch }
        // A full branch only matters again once the other one is full too; that branch's own write completes the quest.
        gather(Harmony(player.uniqueId, branch), branch, target, 1) {
            settled(player.uniqueId, LineBetweenSwordAndArrow) || (stored(player.uniqueId, other.counter) ?: 0L) < other.target
        }
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
