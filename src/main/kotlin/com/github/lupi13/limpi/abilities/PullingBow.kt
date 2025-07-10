package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector

object PullingBow : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("갈고리 화살", NamedTextColor.LIGHT_PURPLE),
    codeName = "pulling_bow",
    material = Material.TRIPWIRE_HOOK,
    description = listOf(
        Component.text("자신이 쏜 화살의 넉백 방향을", NamedTextColor.WHITE),
        Component.text("끌어당기는 방향으로 바꿉니다.", NamedTextColor.WHITE),
        Component.text("밀어내기", NamedTextColor.AQUA)
            .append(Component.text(" 마법부여에 영향을 받습니다.", NamedTextColor.WHITE))
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("화살의 넉백이 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getDouble("knockBackAmplifier"), NamedTextColor.GREEN))
                .append(Component.text("배 증가합니다.", NamedTextColor.WHITE)),
            Component.text("자신이 쏜 화살의 넉백 방향을", NamedTextColor.WHITE),
            Component.text("끌어당기는 방향으로 바꿉니다.", NamedTextColor.WHITE),
            Component.text("밀어내기", NamedTextColor.AQUA)
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
    fun onArrowHit(event: ProjectileHitEvent) {
        if (event.entity !is AbstractArrow) return
        if (event.entity.shooter !is Player) return

        val player = event.entity.shooter as Player
        if (player.ability != this) return

        val hitEntity = event.hitEntity ?: return

        val amp = config!!.getDouble("knockBackAmplifier")
        object: BukkitRunnable() {
            override fun run() {
                hitEntity.velocity = Vector(
                    hitEntity.velocity.x * -amp,
                    hitEntity.velocity.y * amp,
                    hitEntity.velocity.z * -amp
                )
            }
        }.runTaskLater(plugin, 1L)
    }
}