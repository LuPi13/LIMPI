package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.TurretChampion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 타워 디펜스: 경비 전문가 능력으로 마을 습격에서 승리하되, 그 습격의 습격자를 포탑 말고 직접 한 번도 때리지 않고
 * 자기 포탑으로 습격자를 하나 이상 쓰러뜨려야 합니다. 판정은 [com.github.lupi13.limpi.turret.TurretRuntime].
 */
object TowerDefense : Quest(
    displayText = Component.text("타워 디펜스", NamedTextColor.YELLOW),
    codeName = "tower_defense",
    isHidden = false,
    howToGet = Component.text("경비 전문가", NamedTextColor.GRAY)
        .append(Component.text(" 능력으로, 마을 습격을 포탑만으로(플레이어가 직접 타격하지 않고) 클리어하세요.", NamedTextColor.WHITE)),
    rewardAbility = TurretChampion
)
