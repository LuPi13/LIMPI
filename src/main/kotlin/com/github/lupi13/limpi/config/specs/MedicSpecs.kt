package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*

/** 응급처치·전투 의무관 설정. */
object MedicSpecs {
    private fun firstAid(): List<AbilityField> = listOf(
        number("heal_amount", 4.0, 0.0, 1024.0), // 한 번에 회복하는 체력
        number("heal_seconds", 4.0, 0.0, 60.0), // 회복이 나뉘어 진행되는 시간(초, 0이면 즉시)
        integer("cooldown_ticks", 100, 0, 72000) // 쿨타임(틱)
    )

    val all: Map<String, AbilitySpec> = linkedMapOf(
        "first_aid" to AbilitySpec(firstAid()),
        "combat_medic" to AbilitySpec(firstAid() + listOf(
            number("throw_speed_multiplier", 2.0, 0.0, 10.0) // 던진 종이 속도 배수
        ))
    )
}
