package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.ExplosionMove
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object NogadaKing : Quest(
    displayText = Component.text("노가다의 왕", NamedTextColor.WHITE),
    codeName = "nogada_king",
    isHidden = false,
    howToGet = Component.text("핸드캐논", NamedTextColor.GRAY)
        .append(Component.text("으로 100킬 하세요.", NamedTextColor.WHITE)),
    rewardAbility = ExplosionMove,
    progress = QuestProgressDefinitions.CANNON_KILLS
)
