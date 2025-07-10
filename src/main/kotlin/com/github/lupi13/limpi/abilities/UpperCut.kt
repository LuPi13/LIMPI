package com.github.lupi13.limpi.abilities

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.util.Vector

object UpperCut : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("어퍼컷", NamedTextColor.DARK_BLUE),
    codeName = "upper_cut",
    material = Material.IRON_GOLEM_SPAWN_EGG,
    description = listOf(
        Component.text("근접 공격의 넉백 방향을", NamedTextColor.WHITE),
        Component.text("수직방향으로 바꿉니다.", NamedTextColor.WHITE),
        Component.text("밀치기", NamedTextColor.AQUA)
            .append(Component.text(" 마법부여에 영향을 받습니다.", NamedTextColor.WHITE))
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("근접 공격의 넉백이 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getDouble("knockBackAmplifier"), NamedTextColor.GREEN))
                .append(Component.text("배 증가합니다.", NamedTextColor.WHITE)),
            Component.text("근접 공격의 넉백 방향을", NamedTextColor.WHITE),
            Component.text("수직방향으로 바꿉니다.", NamedTextColor.WHITE),
            Component.text("밀치기", NamedTextColor.AQUA)
                .append(Component.text(" 마법부여에 영향을 받습니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("knockBackAmplifier", 1.1)
        }
        saveConfig()
    }


    @EventHandler
    fun onKnockBack(event: EntityKnockbackByEntityEvent) {
        if (event.pushedBy !is Player) return
        val player = event.pushedBy as Player
        if (player.ability != this) return
        event.knockback = Vector(
            0.0,
            event.knockback.length() * config!!.getDouble("knockBackAmplifier"),
            0.0
        )
    }
}