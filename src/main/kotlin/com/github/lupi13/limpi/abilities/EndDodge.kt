package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.YourDodge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.util.Vector
import kotlin.math.hypot
import kotlin.math.min

object EndDodge : DodgeVariant(
    grade = Grade.EPIC,
    element = Element.MAGIC,
    displayName = Component.text("엔드의 회피", NamedTextColor.DARK_PURPLE),
    codeName = "end_dodge",
    material = Material.ENDER_PEARL,
    description = listOf(
        Component.text("회피", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("회피가 이동 방향으로의 순간이동으로 바뀝니다.", NamedTextColor.WHITE)
    )
) {
    override val unlockQuest: Quest get() = YourDodge

    override val details: List<Component> by lazy {
        listOf(
            inheritLine(),
            Component.text("지상에서 빠르게 두 번 웅크리면 잠시 후", NamedTextColor.WHITE),
            Component.text("이동 방향으로 최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("distance")}", NamedTextColor.GREEN))
                .append(Component.text("블록 순간이동합니다.", NamedTextColor.WHITE)),
            Component.text("빠르게 이동할수록 더 멀리 이동합니다. (최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("max_distance")}", NamedTextColor.GREEN))
                .append(Component.text("블록)", NamedTextColor.WHITE)),
            Component.text("발 밑이 끊기거나 벽에 막히면 그 앞에서 멈춥니다.", NamedTextColor.WHITE),
            cooldownLine()
        )
    }

    private const val STEPS = 100

    override fun performDodge(player: Player) {
        val config = config!!
        val origin = player.location
        val movement = (Dodge.velocityMap[player.uniqueId]?.clone() ?: Vector()).setY(0)
        val speed = hypot(movement.x, movement.z)
        val direction = if (speed > 0.000001) movement.multiply(1.0 / speed)
            else origin.direction.setY(0).let { if (it.lengthSquared() > 0.000001) it.normalize() else Vector(0, 0, 1) }
        // 원본: 이동속도 0.1 이상이면 속도 × 200 블록, 아니면 기본 거리
        val distance = min(config.getDouble("max_distance"),
            if (speed >= 0.1) speed * config.getDouble("speed_distance_multiplier") else config.getDouble("distance"))
        val step = direction.multiply(distance / STEPS)

        val destination = origin.clone()
        for (i in 1..STEPS) {
            val candidate = destination.clone().add(step)
            if (!standable(candidate)) break
            destination.add(step)
        }

        val world = player.world
        world.spawnParticle(Particle.SMOKE, origin.clone().add(0.0, 0.9, 0.0), 30, 0.2, 0.4, 0.2, 0.0)
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0.0, 0.9, 0.0), 30, 0.2, 0.4, 0.2, 0.0)
        world.playSound(origin, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 1f)

        val effect = AbilityRuntime.begin(player, codeName)
        effect.repeat(delay = config.getInt("delay_ticks").toLong()) {
            it.close()
            val target = it.player() ?: return@repeat
            if (!standable(destination)) return@repeat
            target.teleport(destination.clone().setRotation(target.location.yaw, target.location.pitch), PlayerTeleportEvent.TeleportCause.PLUGIN)
            target.world.spawnParticle(Particle.GLOW, target.location.add(0.0, 0.9, 0.0), 30, 0.2, 0.4, 0.2, 0.0)
            target.world.playSound(target.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 2f)
        }
    }

    /** 발 밑이 막혀 있고 발/머리 위치가 통과 가능한지. */
    private fun standable(location: Location): Boolean =
        !location.clone().subtract(0.0, 0.1, 0.0).block.isPassable &&
            location.block.isPassable && location.clone().add(0.0, 1.0, 0.0).block.isPassable
}
