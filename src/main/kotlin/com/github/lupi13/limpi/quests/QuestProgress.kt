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
    val HAWK_EYE = QuestProgress(listOf(QuestCounter("hawk_eye_extra_items", 50)))
    val CANNON_KILLS = QuestProgress(listOf(QuestCounter("hand_cannon_kills", 50)))
    val CANNON_SHOTS = QuestProgress(listOf(QuestCounter("hand_cannon_shots", 1000)))
    val TIME_HEALED = QuestProgress(listOf(QuestCounter("time_leaper_healed", 2000, 10, "체력")))
    val TIME_LOST = QuestProgress(listOf(QuestCounter("time_leaper_lost", 500, 10, "체력")))
    val HARMONY = QuestProgress(listOf(QuestCounter("harmony_sword", 10, label = "검"), QuestCounter("harmony_arrow", 10, label = "화살")))
}

/** Pure quest conditions shared by triggers and tests, without initializing the ability registry. */
object QuestConditions {
    /** 경험치 흡수기: 테라버닝 적용 중 도달해야 하는 레벨. */
    const val EXP_ABSORBER_LEVEL = 50
    /** 바람처럼 차갑게: 회피 직전 수평 이동속도(블록/틱). 원본 기준 2.65 / 10. */
    const val COLD_DODGE_SPEED = 0.265
    /** 바람처럼 차갑게: 신속 II 이상(amplifier 1 이상)이어야 합니다. */
    const val COLD_DODGE_SPEED_AMPLIFIER = 1

    /** [speedAmplifier] is the active SPEED effect's amplifier, or null without the effect. */
    fun coldDodge(horizontalSpeed: Double, speedAmplifier: Int?): Boolean =
        speedAmplifier != null && speedAmplifier >= COLD_DODGE_SPEED_AMPLIFIER && horizontalSpeed >= COLD_DODGE_SPEED
}

/** Durable usage counters (`quest.<counter>`) that reveal hidden-quest hints; they never complete a quest. */
object QuestHints {
    const val DODGE_THRESHOLD = 30L
    const val OVER_BALANCE_USES = "over_balance_uses"
    const val CLOAK_USES = "cloak_uses"
    const val INVISIBLE_DODGE_USES = "invisible_dodge_uses"
    const val TIME_LEAPER_USES = "time_leaper_uses"
}
