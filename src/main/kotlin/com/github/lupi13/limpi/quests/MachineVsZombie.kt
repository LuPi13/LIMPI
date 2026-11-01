package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.GuardExpert
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 기계 vs 좀비: 자신의 포탑만으로 1분 안에 좀비 계열 10마리를 처치합니다. 판정은 [com.github.lupi13.limpi.turret.TurretRuntime]
 * (포탑 피해가 마지막 타격이고, 주인이 포탑 말고 다른 방법으로 피해를 준 적 없는 좀비만 셈).
 */
object MachineVsZombie : Quest(
    displayText = Component.text("기계 vs 좀비", NamedTextColor.YELLOW),
    codeName = "machine_vs_zombie",
    isHidden = false,
    howToGet = Component.text("오직 자신의 ", NamedTextColor.WHITE)
        .append(Component.text("포탑", NamedTextColor.GRAY))
        .append(Component.text("만으로 1분 안에 좀비 10마리를 처치하세요.", NamedTextColor.WHITE)),
    rewardAbility = GuardExpert
)
