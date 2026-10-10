package com.github.lupi13.limpi.quest

import com.github.lupi13.limpi.economy.Accounts
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.storage.*
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class QuestRewardResult(val newCompletion: Boolean, val newUnlock: Boolean, val replayed: Boolean)

/** Progress, completion, unlock and reward audit are one transaction, with no Bukkit callbacks. */
class QuestRewards(private val store: StateStore, private val players: PlayerRepository) {
    fun complete(owner: UUID, quest: String, ability: String): QuestRewardResult {
        require(quest.isNotBlank() && ability.isNotBlank())
        val id = UUID.nameUUIDFromBytes("quest-reward:$owner:$quest".toByteArray(Charsets.UTF_8))
        return result(store.execute(id, BankService.fingerprint("QUEST_REWARD", owner.toString(), quest, ability)) { tx ->
            val flags = reward(tx, owner, quest, ability)
            receipt(owner, quest, ability, flags.first, flags.second)
        })
    }
    fun harmony(request: UUID, owner: UUID, branch: String): QuestRewardResult {
        require(branch in setOf("harmony_sword", "harmony_arrow"))
        return result(store.execute(request, BankService.fingerprint("HARMONY_PROGRESS", owner.toString(), branch)) { tx ->
            val profile = Accounts.read(tx, owner)
            fun count(path: String): Long {
                val raw = profile.get(path) ?: return 0
                require(raw is Int || raw is Long) { "Non-integral quest progress" }
                return (raw as Number).toLong().also { require(it >= 0) }
            }
            val sword = count("quest.harmony_sword"); val arrow = count("quest.harmony_arrow")
            val old = if (branch == "harmony_sword") sword else arrow
            val updated = if (old >= 10) old else old + 1
            profile.set("quest.$branch", updated)
            tx.put(SqliteProfilePersistence.key(owner), profile.saveToString())
            val finished = if (branch == "harmony_sword") updated >= 10 && arrow >= 10 else sword >= 10 && updated >= 10
            val flags = if (finished) reward(tx, owner, HARMONY_QUEST, HARMONY_REWARD) else false to false
            YamlFiles.parse(receipt(owner, HARMONY_QUEST, HARMONY_REWARD, flags.first, flags.second)).apply {
                set("branch", branch); set("previous", old); set("current", updated)
            }.saveToString()
        })
    }
    private fun reward(tx: StateTransaction, owner: UUID, quest: String, ability: String): Pair<Boolean, Boolean> {
        val key = "quest_reward/$owner/$quest"
        tx.get(key)?.let {
            val previous = YamlFiles.parse(it)
            require(previous.get("schemaVersion") == 1 && previous.get("owner") == owner.toString() &&
                previous.get("quest") == quest && previous.get("ability") == ability) { "Quest reward identity conflict" }
            return false to false
        }
        val profile = Accounts.read(tx, owner)
        fun strings(path: String): MutableList<String> {
            val raw = profile.get(path) ?: return mutableListOf()
            require(raw is List<*> && raw.all { it is String }) { "Malformed $path" }
            return raw.map { it as String }.toMutableList()
        }
        val cleared = strings("clearedQuests"); val unlocked = strings("unlockedAbilities")
        val newCompletion = quest !in cleared; val newUnlock = ability !in unlocked
        if (newCompletion) cleared.add(quest)
        if (newUnlock) unlocked.add(ability)
        profile.set("clearedQuests", cleared); profile.set("unlockedAbilities", unlocked)
        tx.put(SqliteProfilePersistence.key(owner), profile.saveToString())
        tx.put(key, receipt(owner, quest, ability, newCompletion, newUnlock))
        return newCompletion to newUnlock
    }
    private fun receipt(owner: UUID, quest: String, ability: String, cleared: Boolean, unlocked: Boolean) = YamlConfiguration().apply {
        set("schemaVersion", 1); set("owner", owner.toString()); set("quest", quest); set("ability", ability)
        set("newCompletion", cleared); set("newUnlock", unlocked)
    }.saveToString()
    private fun result(result: CommittedTransaction): QuestRewardResult {
        players.invalidate(result.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        val receipt = YamlFiles.parse(result.entry.receipt)
        return QuestRewardResult(receipt.getBoolean("newCompletion"), receipt.getBoolean("newUnlock"), result.replayed)
    }
    companion object {
        const val HARMONY_QUEST = "line_between_sword_and_arrow"
        const val HARMONY_REWARD = "transcendence"
    }
}
