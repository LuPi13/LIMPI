package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.GhostStab
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent

object StealthBackStab : BackStabVariant(
    grade = Grade.RARE,
    displayName = Component.text("스텔스 백스탭", NamedTextColor.DARK_GRAY),
    codeName = "stealth_back_stab",
    acquisition = AcquisitionType.QUEST,
    description = listOf(
        Component.text("백스탭", NamedTextColor.GOLD)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("웅크리면 몹이 가까이 오기 전까지", NamedTextColor.WHITE),
        Component.text("당신을 알아채지 못합니다.", NamedTextColor.WHITE)
    )
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(GhostStab.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("백스탭", NamedTextColor.GOLD)
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE))
        ) + backStabLines() + listOf(
            Component.text("웅크리고 있으면 몹의 감지 범위가 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("detect_range")}", NamedTextColor.GREEN))
                .append(Component.text("블록으로 줄어듭니다.", NamedTextColor.WHITE))
        )
    }

    /** 웅크린 동안 감지 범위 밖의 몹은 가장 가까운 플레이어로 당신을 새로 노리지 않습니다. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        if (event.reason != EntityTargetEvent.TargetReason.CLOSEST_PLAYER) return
        val player = event.target as? Player ?: return
        if (player.ability != this || !player.isSneaking) return
        val monster = event.entity
        if (monster.world.uid != player.world.uid) return
        val range = config!!.getDouble("detect_range")
        if (monster.location.distanceSquared(player.location) >= range * range) event.isCancelled = true
    }
}
