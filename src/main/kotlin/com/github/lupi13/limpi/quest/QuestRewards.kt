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
    /** Count [amount] (default one) hits of [branch]; completes the quest when both branches are full. */
    fun harmony(request: UUID, owner: UUID, branch: String, amount: Long = 1): QuestRewardResult {
        require(amount > 0)
        val counters = com.github.lupi13.limpi.quests.QuestProgressDefinitions.HARMONY.counters
        require(branch in counters.map { it.counter })
        val swordTarget = counters[0].target; val arrowTarget = counters[1].target
        // This branch is full: counting changes nothing unless it would finish an unrewarded quest.
        if (store.entry(request) == null) {
            val other = counters.single { it.counter != branch }
            val pair = runCatching { players.read(owner) { count(it, branch) to count(it, other.counter) } }.getOrNull()
            val mine = pair?.first; val theirs = pair?.second
            val target = counters.single { it.counter == branch }.target
            if (mine != null && theirs != null && mine >= target && (theirs < other.target || rewarded(owner, HARMONY_QUEST))) return UNCHANGED
        }
        val fields = listOf("HARMONY_PROGRESS", owner.toString(), branch) + if (amount == 1L) emptyList() else listOf(amount.toString())
        return result(store.execute(request, BankService.fingerprint(*fields.toTypedArray())) { tx ->
            val profile = Accounts.read(tx, owner)
            fun count(path: String): Long {
                val raw = profile.get(path) ?: return 0
                require(raw is Int || raw is Long) { "Non-integral quest progress" }
                return (raw as Number).toLong().also { require(it >= 0) }
            }
            val sword = count("quest.${counters[0].counter}"); val arrow = count("quest.${counters[1].counter}")
            val old = if (branch == counters[0].counter) sword else arrow
            val target = counters.single { it.counter == branch }.target
            val updated = if (old >= target) old else Math.addExact(old, minOf(amount, target - old))
            profile.set("quest.$branch", updated)
            tx.put(SqliteProfilePersistence.key(owner), profile.saveToString())
            val finished = if (branch == counters[0].counter) updated >= swordTarget && arrow >= arrowTarget else sword >= swordTarget && updated >= arrowTarget
            val flags = if (finished) reward(tx, owner, HARMONY_QUEST, HARMONY_REWARD) else false to false
            YamlFiles.parse(receipt(owner, HARMONY_QUEST, HARMONY_REWARD, flags.first, flags.second)).apply {
                set("branch", branch); set("previous", old); set("current", updated)
            }.saveToString()
        })
    }
    /**
     * Add one to the durable counter `quest.<counter>` (capped at [target]); reaching [target] completes [quest] and
     * unlocks [ability] in the same transaction. Replaying [request] never counts twice.
     */
    fun progress(request: UUID, owner: UUID, counter: String, target: Long, quest: String, ability: String): QuestRewardResult {
        require(counter.matches(Regex("[a-z0-9_]+")) && target > 0 && quest.isNotBlank() && ability.isNotBlank())
        if (store.entry(request) == null && settled(owner, counter, target, quest)) return UNCHANGED
        return result(store.execute(request, BankService.fingerprint("QUEST_PROGRESS", owner.toString(), counter, target.toString(), quest, ability)) { tx ->
            val profile = Accounts.read(tx, owner)
            val raw = profile.get("quest.$counter")
            require(raw == null || raw is Int || raw is Long) { "Non-integral quest progress" }
            val old = (raw as Number?)?.toLong() ?: 0L
            require(old >= 0)
            val updated = if (old >= target) old else old + 1
            profile.set("quest.$counter", updated)
            tx.put(SqliteProfilePersistence.key(owner), profile.saveToString())
            val flags = if (updated >= target) reward(tx, owner, quest, ability) else false to false
            YamlFiles.parse(receipt(owner, quest, ability, flags.first, flags.second)).apply {
                set("counter", counter); set("previous", old); set("current", updated)
            }.saveToString()
        })
    }

    /**
     * Add a non-negative [amount] to the durable counter `quest.<counter>` (capped at [target]); reaching [target]
     * completes [quest] and unlocks [ability] in the same transaction. Replaying [request] never counts twice.
     */
    fun progress(request: UUID, owner: UUID, counter: String, amount: Long, target: Long, quest: String, ability: String): QuestRewardResult {
        require(counter.matches(Regex("[a-z0-9_]+")) && amount >= 0 && target > 0 && quest.isNotBlank() && ability.isNotBlank())
        if (store.entry(request) == null && settled(owner, counter, target, quest)) return UNCHANGED
        return result(store.execute(request, BankService.fingerprint("QUEST_PROGRESS_AMOUNT", owner.toString(), counter,
            amount.toString(), target.toString(), quest, ability)) { tx ->
            val profile = Accounts.read(tx, owner)
            val raw = profile.get("quest.$counter")
            require(raw == null || raw is Int || raw is Long) { "Non-integral quest progress" }
            val old = (raw as Number?)?.toLong() ?: 0L
            require(old >= 0)
            val updated = if (old >= target) old else Math.addExact(old, minOf(amount, target - old))
            profile.set("quest.$counter", updated)
            tx.put(SqliteProfilePersistence.key(owner), profile.saveToString())
            val flags = if (updated >= target) reward(tx, owner, quest, ability) else false to false
            YamlFiles.parse(receipt(owner, quest, ability, flags.first, flags.second)).apply {
                set("counter", counter); set("amount", amount); set("previous", old); set("current", updated)
            }.saveToString()
        })
    }

    /**
     * Add one to the durable usage counter `quest.<counter>`, capped at [cap]; never completes a quest or unlocks
     * anything. Returns the stored value after the transaction (an existing value above [cap] is kept as is).
     * Replaying [request] never counts twice.
     */
    fun count(request: UUID, owner: UUID, counter: String, cap: Long, amount: Long = 1): Long {
        require(counter.matches(Regex("[a-z0-9_]+")) && cap > 0 && amount > 0)
        val fields = listOf("QUEST_COUNT", owner.toString(), counter, cap.toString()) + if (amount == 1L) emptyList() else listOf(amount.toString())
        val committed = store.execute(request, BankService.fingerprint(*fields.toTypedArray())) { tx ->
            val profile = Accounts.read(tx, owner)
            val raw = profile.get("quest.$counter")
            require(raw == null || raw is Int || raw is Long) { "Non-integral quest counter" }
            val old = (raw as Number?)?.toLong() ?: 0L
            require(old >= 0)
            val updated = if (old >= cap) old else Math.addExact(old, minOf(amount, cap - old))
            if (updated != old) {
                profile.set("quest.$counter", updated)
                tx.put(SqliteProfilePersistence.key(owner), profile.saveToString())
            }
            YamlConfiguration().apply {
                set("schemaVersion", 1); set("owner", owner.toString()); set("counter", counter)
                set("previous", old); set("current", updated)
            }.saveToString()
        }
        players.invalidate(committed.entry.changedKeys.filter { it.startsWith("player/") }.map { UUID.fromString(it.removePrefix("player/")) })
        return YamlFiles.parse(committed.entry.receipt).getLong("current")
    }

    /*
     * Counting is called on every dodge, hand cannon shot, harmony hit, ... A finished counter whose reward is recorded
     * would only add a ledger row and rewrite the same profile (with an fsync) on the main thread each time, so these
     * read-only checks skip that write. Anything unusual (missing or malformed data) still takes the transaction path,
     * which reports it exactly as before. A request that is already recorded is never skipped, so its replay still
     * returns the original receipt.
     */
    /** The stored counter, 0 when absent, or null when it is malformed. */
    private fun count(profile: org.bukkit.configuration.ConfigurationSection, counter: String): Long? {
        val raw = profile.get("quest.$counter") ?: return 0
        if (raw !is Int && raw !is Long) return null
        return (raw as Number).toLong().takeIf { it >= 0 }
    }

    /** A reward record is never removed, so a positive answer is remembered. */
    private val rewardedQuests = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private fun rewarded(owner: UUID, quest: String): Boolean {
        val key = "quest_reward/$owner/$quest"
        if (key in rewardedQuests) return true
        return (store.get(key) != null).also { if (it) rewardedQuests.add(key) }
    }

    /**
     * [counter] already reached [target] and [quest]'s reward is recorded, so counting would change nothing. The
     * profile is read from the repository cache, which every transaction here invalidates after committing.
     */
    private fun settled(owner: UUID, counter: String, target: Long, quest: String): Boolean {
        val current = runCatching { players.read(owner) { count(it, counter) } }.getOrNull() ?: return false
        return current >= target && rewarded(owner, quest)
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
        /** Counting skipped because nothing would change; no ledger row is written (callers notify nothing). */
        val UNCHANGED = QuestRewardResult(newCompletion = false, newUnlock = false, replayed = false)
    }
}
