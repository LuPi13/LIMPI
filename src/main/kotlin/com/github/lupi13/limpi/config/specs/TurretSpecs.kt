package com.github.lupi13.limpi.config.specs

import com.github.lupi13.limpi.config.*
import com.github.lupi13.limpi.turret.CopperTurretKind
import com.github.lupi13.limpi.turret.GoldTurretKind
import com.github.lupi13.limpi.turret.IronTurretKind
import com.github.lupi13.limpi.turret.TurretKind

/**
 * 포탑 능력 설정. 공통 키(쿨타임·사유지) 뒤에 종류별 수치를 붙입니다. 종류가 하나인 능력은 접두사 없이,
 * 여러 종류를 가진 상위 능력은 종류 id 접두사(예: `gold_damage`)로 씁니다([com.github.lupi13.limpi.abilities.TurretAbility.prefix]와 같게).
 */
object TurretSpecs {
    fun spec(kinds: List<TurretKind>): AbilitySpec = AbilitySpec(listOf(
        integer("cooldown_ticks", 20, 0, 72000), // 설치 쿨타임
        flag("allow_in_others_estate", false), // 내가 지을 수 없는 남의 사유지 안에 설치 허용
    ) + kinds.flatMap { kind -> kind.fields(prefix(kinds, kind)) })

    fun prefix(kinds: List<TurretKind>, kind: TurretKind): String = if (kinds.size == 1) "" else kind.id

    val all: Map<String, AbilitySpec> = linkedMapOf(
        "turret" to spec(listOf(IronTurretKind)),
        "guard_expert" to spec(listOf(IronTurretKind, CopperTurretKind, GoldTurretKind))
    )
}
