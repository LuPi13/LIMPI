package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Damageable
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object Titan : Ability(
    grade = Grade.TROLL,
    element = Element.EXPLOSIVE,
    displayName = Component.text("타이탄", NamedTextColor.BLACK),
    codeName = "titan",
    material = Material.BLAZE_POWDER,
    description = listOf(
        Component.text("", NamedTextColor.WHITE)
            .append(Component.text("혜성강타", NamedTextColor.AQUA))
            .append(Component.text(" 능력을 계승합니다?", NamedTextColor.WHITE)),
        Component.text("그 가 폭발 합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("타이탄은 수호자 군대에서 무지막지한 외상을 입 는 직업으로, 그 가 폭발 합니다.", NamedTextColor.WHITE),
            Component.text("공격 을 후려 쳐서 자신과 화력팀을 바닥에, 타이탄은 속 았습니다.", NamedTextColor.WHITE),
        )
    }

}