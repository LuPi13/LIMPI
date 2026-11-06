package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.ability.CollisionBox
import com.github.lupi13.limpi.ability.GroundPath
import com.github.lupi13.limpi.ability.GroundPoint
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.YourDodge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.min

object EndDodge : DodgeVariant(
    grade = Grade.EPIC,
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

    override val details: List<Component> by settingsCached {
        listOf(
            inheritLine(),
            Component.text("지상에서 빠르게 두 번 웅크리면 잠시 후", NamedTextColor.WHITE),
            Component.text("이동 방향으로 최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("distance")}", NamedTextColor.GREEN))
                .append(Component.text("블록 순간이동합니다.", NamedTextColor.WHITE)),
            Component.text("빠르게 이동할수록 더 멀리 이동합니다. (최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("max_distance")}", NamedTextColor.GREEN))
                .append(Component.text("블록)", NamedTextColor.WHITE)),
            Component.text("걸어서 넘을 수 있는 턱은 넘어가고, 발 밑이 끊기거나", NamedTextColor.WHITE),
            Component.text("벽에 막히면 그 앞에서 멈춥니다.", NamedTextColor.WHITE),
            Component.text("이동할 수 없으면 쿨타임이 돌지 않습니다.", NamedTextColor.WHITE),
            Component.text("쿨타임이 아닐 때 도착 예정 위치가 자신에게만 표시됩니다.", NamedTextColor.WHITE),
            cooldownLine()
        )
    }

    private const val STEPS = 100
    private const val PREVIEW_PERIOD = 3L

    /** 이보다 가까우면(0.5블록 제곱) 이동할 수 없는 것으로 봅니다. */
    private const val MIN_MOVE_SQUARED = 0.25

    /** 블록의 실제 충돌 모양으로 걷는 경로. 불러오지 않은 청크는 읽지 않고 막힌 것으로 봅니다. */
    private fun ground(world: World) = GroundPath({ x, y, z ->
        when {
            !world.isChunkLoaded(x shr 4, z shr 4) -> null
            y < world.minHeight || y >= world.maxHeight -> emptyList()
            else -> world.getBlockAt(x, y, z).collisionShape.boundingBoxes.map {
                CollisionBox(it.minX, it.minY, it.minZ, it.maxX, it.maxY, it.maxZ)
            }
        }
    })

    /**
     * 회피했을 때 순간이동할 위치. 회피([performDodge])와 미리보기([start])가 같은 계산을 씁니다.
     * 이동 방향으로 [STEPS]번 나누어 나아가며 지면을 따라 턱을 넘고, 발 밑이 끊기거나 벽에 막히면 그 앞에서 멈춥니다.
     */
    private fun destinationOf(player: Player): Location {
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
        val end = ground(origin.world).walk(GroundPoint(origin.x, origin.y, origin.z), step.x, step.z, STEPS)
        return origin.clone().apply { x = end.x; y = end.y; z = end.z }
    }

    /** 미리보기가 보이지 않는 경우와 같습니다. 쿨타임을 쓰지 않고 웅크린 사람에게만 알립니다. */
    override fun canDodge(player: Player): Boolean {
        if (destinationOf(player).distanceSquared(player.location) >= MIN_MOVE_SQUARED) return true
        player.sendActionBar(Component.text("이동할 수 없습니다.", NamedTextColor.RED))
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 2f)
        return false
    }

    override fun performDodge(player: Player) {
        val origin = player.location
        val destination = destinationOf(player)

        val world = player.world
        world.spawnParticle(Particle.SMOKE, origin.clone().add(0.0, 0.9, 0.0), 30, 0.2, 0.4, 0.2, 0.0)
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0.0, 0.9, 0.0), 30, 0.2, 0.4, 0.2, 0.0)
        world.playSound(origin, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 1f)

        val effect = AbilityRuntime.begin(player, codeName)
        effect.repeat(delay = config!!.getInt("delay_ticks").toLong()) {
            it.close()
            val target = it.player() ?: return@repeat
            if (!standable(destination)) return@repeat
            target.teleport(destination.clone().setRotation(target.location.yaw, target.location.pitch), PlayerTeleportEvent.TeleportCause.PLUGIN)
            target.world.spawnParticle(Particle.GLOW, target.location.add(0.0, 0.9, 0.0), 30, 0.2, 0.4, 0.2, 0.0)
            target.world.playSound(target.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 2f)
        }
    }

    /** 미리보기 표시 색(엔드 보라). */
    private val previewDust by lazy { Particle.DustOptions(Color.fromRGB(170, 60, 255), 1.2f) }

    /**
     * 쿨타임이 아닐 때 회피 도착 예정 위치를 소유자에게만 보여 줍니다. 회피할 수 있는 상태(지상, 살아 있음, 지급 차단 아님)에서
     * 실제로 이동할 수 있는 위치(원래 자리와 다르고 서 있을 수 있음)일 때만 표시합니다.
     * 전역 작업 하나가 이 능력 사용자만 순회하므로 사망/퇴장/능력 변경 시 자동으로 표시가 멈춥니다.
     */
    override fun start() {
        AbilityRuntime.global(PREVIEW_PERIOD) {
            val cooldown = config!!.getInt("cooldown")
            val now = System.currentTimeMillis()
            for (player in plugin.server.onlinePlayers) {
                if (player.ability !== this || player.isDead || !(player as Entity).isOnGround) continue
                if (FileManager.delivery.blocked(player.uniqueId)) continue
                val last = lastDodgeTime[player.uniqueId]
                if (last != null && now - last <= cooldown) continue
                val destination = destinationOf(player)
                if (destination.distanceSquared(player.location) < MIN_MOVE_SQUARED) continue
                val center = destination.add(0.0, 0.1, 0.0)
                for (i in 0 until 8) {
                    val angle = Math.PI / 4 * i
                    player.spawnParticle(Particle.DUST, center.x + 0.4 * cos(angle), center.y, center.z + 0.4 * sin(angle), 1,
                        0.0, 0.0, 0.0, 0.0, previewDust)
                }
                player.spawnParticle(Particle.PORTAL, center.x, center.y + 0.8, center.z, 3, 0.15, 0.4, 0.15, 0.0)
            }
        }
    }

    /** 바로 발 밑에 지면이 있고 플레이어가 들어갈 자리가 있는지. */
    private fun standable(location: Location): Boolean =
        ground(location.world).standable(GroundPoint(location.x, location.y, location.z))
}
