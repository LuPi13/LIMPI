package com.github.lupi13.limpi.quests

import org.bukkit.configuration.file.FileConfiguration
import java.math.BigDecimal

data class QuestCounter(val counter: String, val target: Long, val unitsPerDisplay: Long = 1, val label: String = "") {
    init { require(counter.matches(Regex("[a-z0-9_]+")) && target > 0 && unitsPerDisplay > 0) }
    fun current(profile: FileConfiguration): Long {
        val raw = profile.get("quest.$counter") ?: return 0
        require(raw is Int || raw is Long) { "퀘스트 진행도 확인 필요" }
        return (raw as Number).toLong().also { require(it >= 0) { "퀘스트 진행도 확인 필요" } }.coerceAtMost(target)
    }
    fun format(value: Long) = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(unitsPerDisplay)).stripTrailingZeros().toPlainString()
}

data class QuestProgress(val counters: List<QuestCounter>) {
    init { require(counters.isNotEmpty() && counters.map { it.counter }.toSet().size == counters.size) }
    val counter get() = counters.single().counter
    val target get() = counters.single().target
}

/** Shared by quest metadata and durable progress rules, without initializing the ability registry. */
object QuestProgressDefinitions {
    val DODGE = QuestProgress(listOf(QuestCounter("dodge_count", 100)))
    val HAWK_EYE = QuestProgress(listOf(QuestCounter("hawk_eye_extra_items", 100)))
    val CANNON_KILLS = QuestProgress(listOf(QuestCounter("hand_cannon_kills", 100)))
    val CANNON_SHOTS = QuestProgress(listOf(QuestCounter("hand_cannon_shots", 3000)))
    val TIME_HEALED = QuestProgress(listOf(QuestCounter("time_leaper_healed", 2000, 10, "체력")))
    val TIME_LOST = QuestProgress(listOf(QuestCounter("time_leaper_lost", 500, 10, "체력")))
    val HARMONY = QuestProgress(listOf(QuestCounter("harmony_sword", 10, label = "검"), QuestCounter("harmony_arrow", 10, label = "화살")))
}
