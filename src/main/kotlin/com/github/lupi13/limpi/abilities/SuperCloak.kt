package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.MoebiusReactor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent

object SuperCloak : CloakAbility(
    grade = Grade.MYTHIC,
    element = Element.UTILITY,
    displayName = Component.text("초은폐", NamedTextColor.DARK_AQUA),
    codeName = "super_cloak",
    material = Material.MUSIC_DISC_CAT,
    description = listOf(
        Component.text("", NamedTextColor.WHITE)
            .append(Component.text("은폐", NamedTextColor.DARK_GREEN))
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("투명화 중 받는 피해가 감소하고,", NamedTextColor.WHITE),
        Component.text("움직이지 않으면 게이지를 소모하지 않습니다.", NamedTextColor.WHITE),
        Component.text("적을 처치하면 게이지를 회복합니다.", NamedTextColor.WHITE),
    ),
    acquisition = AcquisitionType.QUEST,
) {
    override val stillSaves = true
    override val label = "SuperCloak"

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(MoebiusReactor.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("", NamedTextColor.WHITE)
                .append(Component.text("은폐", NamedTextColor.DARK_GREEN))
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
            Component.text("빠르게 두 번 웅크리면 ", NamedTextColor.WHITE).append(Component.text("투명화", NamedTextColor.BLUE)).append(Component.text("를 시작/해제합니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE).append(Component.text(" 중에는 갑옷이 보이지 않고 방어력이 적용되지 않습니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE).append(Component.text(" 시작 시 게이지 ", NamedTextColor.WHITE))
                .append(Component.text(format(config!!.getDouble("activation_cost")), NamedTextColor.GREEN))
                .append(Component.text(", 매 초 ", NamedTextColor.WHITE))
                .append(Component.text(format(config!!.getDouble("drain_per_second")), NamedTextColor.GREEN))
                .append(Component.text("을(를) 소모하며, 움직이지 않으면 소모하지 않습니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE).append(Component.text(" 중 받는 피해가 ", NamedTextColor.WHITE))
                .append(Component.text("${format(config!!.getDouble("damage_reduction") * 100)}", NamedTextColor.GREEN).append(Component.text("%", NamedTextColor.WHITE)))
                .append(Component.text(" 감소합니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE).append(Component.text(" 중이 아닐 때 게이지가 매 초 ", NamedTextColor.WHITE))
                .append(Component.text(format(config!!.getDouble("regen_per_second")), NamedTextColor.GREEN))
                .append(Component.text("씩 회복됩니다. (최대 ", NamedTextColor.WHITE))
                .append(Component.text(format(config!!.getDouble("max_gauge")), NamedTextColor.GREEN))
                .append(Component.text(")", NamedTextColor.WHITE)),
            Component.text("개체를 처치하면 게이지 ", NamedTextColor.WHITE)
                .append(Component.text(format(config!!.getDouble("kill_gauge")), NamedTextColor.GREEN))
                .append(Component.text("을(를) 회복합니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE).append(Component.text(" 중에는 주변 몹이 나를 노리지 못합니다.", NamedTextColor.WHITE))
        )
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamaged(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability !== this || !isCloaked(player.uniqueId)) return
        event.damage *= (1.0 - config!!.getDouble("damage_reduction"))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKill(event: EntityDeathEvent) {
        val player = event.entity.killer ?: return
        if (player.uniqueId == event.entity.uniqueId || player.ability !== this) return
        addGauge(player.uniqueId, points("kill_gauge"))
    }
}
