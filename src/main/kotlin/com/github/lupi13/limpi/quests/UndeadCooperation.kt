package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.abilities.ExpRich
import com.github.lupi13.limpi.abilities.ZombieKing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object UndeadCooperation : Quest(
    displayText = Component.text("언데드 협공", NamedTextColor.DARK_GREEN),
    codeName = "undead_cooperation",
    isHidden = false,
    howToGet = Component.text("우호적 언데드", NamedTextColor.DARK_GREEN)
    .append(Component.text(" 능력을 적용중인 상태로\n다른 언데드가 공격하려는 대상을 처치하세요.", NamedTextColor.WHITE)),
    rewardAbility = ZombieKing
)