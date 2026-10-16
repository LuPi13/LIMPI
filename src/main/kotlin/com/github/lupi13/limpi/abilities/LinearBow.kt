package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.SpectralArrow
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.util.Vector
import java.util.UUID

object LinearBow : Ability(
    grade = Grade.RARE,
    displayName = Component.text("직선 화살", NamedTextColor.WHITE),
    codeName = "linear_bow",
    material = Material.ARROW,
    description = listOf(
        Component.text("최대로 당겨 쏜 화살이 잠시 후 직선 방향의", NamedTextColor.WHITE),
        Component.text("히트스캔 방식으로 발사됩니다.", NamedTextColor.WHITE),
        Component.text("벽에 부딪히지 않으면 사라집니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("최대로 당겨 쏜 ", NamedTextColor.WHITE).append(Component.text("화살", NamedTextColor.YELLOW)).append(Component.text("이 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("delayTicks") / 20.0}", NamedTextColor.GREEN).append(Component.text("초", NamedTextColor.WHITE)))
                .append(Component.text(" 후 직선 방향의", NamedTextColor.WHITE)),
            Component.text("히트스캔 방식으로 발사됩니다.", NamedTextColor.WHITE),
            Component.text("피해량은 일반 ", NamedTextColor.WHITE).append(Component.text("화살", NamedTextColor.YELLOW)).append(Component.text("과 같으며,", NamedTextColor.WHITE)),
            Component.text("벽에 부딪히지 않으면 사라집니다.", NamedTextColor.WHITE)
        )
    }

    /** 발사된 직선 화살 → (주인, 원래 발사 속도). 명중 시 피해 계산을 원래 속도로 되돌리는 데 씁니다. */
    private val linear: MutableMap<UUID, Pair<UUID, Vector>> = mutableMapOf()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLaunch(event: ProjectileLaunchEvent) {
        val arrow = event.entity as? AbstractArrow ?: return
        if (arrow !is Arrow && arrow !is SpectralArrow) return
        val player = arrow.shooter as? Player ?: return
        if (player.ability != this) return
        val original = arrow.velocity.clone()
        if (original.length() < config!!.getDouble("minimumSpeed")) return // 최대로 당긴 활(또는 쇠뇌)만

        val delay = config!!.getInt("delayTicks")
        val lifetime = config!!.getInt("lifeTicks")
        val multiplier = config!!.getDouble("speedMultiplier")
        // 화살을 제자리에 멈춰 두었다가 같은 엔티티를 발사하므로 피해/관통/효과/회수 상태가 그대로 유지됩니다.
        // Hold the arrow with a negligible velocity along its flight direction instead of zero: arrows derive (and
        // smoothly interpolate) their facing from their velocity every tick, so a zero velocity turned them sideways.
        val hold = original.clone().normalize().multiply(HOLD_SPEED)
        arrow.velocity = hold
        face(arrow, original)
        arrow.setGravity(false)
        linear[arrow.uniqueId] = player.uniqueId to original
        object : com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
            var timer = 0
            init {
                // 주인이 죽거나 나가면 공중에 남은 화살을 지웁니다. 벽에 박힌 화살은 원래처럼 남습니다.
                own {
                    linear.remove(arrow.uniqueId)
                    if (arrow.isValid && !arrow.isInBlock) arrow.remove()
                }
            }
            override fun tick() {
                if (!arrow.isValid) { cancel(); return }
                if (timer < delay) { arrow.velocity = hold; face(arrow, original) }
                if (timer == delay) { arrow.velocity = original.clone().multiply(multiplier); face(arrow, original) }
                if (timer >= delay + lifetime) { cancel(); return }
                timer++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private const val HOLD_SPEED = 1.0e-3

    /** Vanilla projectile facing: yaw = atan2(x, z), pitch = atan2(y, horizontal), in degrees. */
    private fun face(arrow: AbstractArrow, direction: Vector) {
        if (direction.lengthSquared() == 0.0) return
        val horizontal = kotlin.math.sqrt(direction.x * direction.x + direction.z * direction.z)
        arrow.setRotation(Math.toDegrees(kotlin.math.atan2(direction.x, direction.z)).toFloat(),
            Math.toDegrees(kotlin.math.atan2(direction.y, horizontal)).toFloat())
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHit(event: ProjectileHitEvent) {
        val arrow = event.entity as? AbstractArrow ?: return
        val (owner, original) = linear[arrow.uniqueId] ?: return
        val target = event.hitEntity as? LivingEntity ?: return
        // 화살 피해는 속도에 비례하므로 원래 발사 속도로 되돌린 뒤 명중시킵니다.
        val speed = original.length()
        arrow.velocity = arrow.velocity.clone().let { if (it.lengthSquared() > 0) it.normalize() else original.clone().normalize() }.multiply(speed)
        if (target.noDamageTicks >= 1) {
            event.isCancelled = true
            arrow.remove()
            return
        }
        // 관통하는 쇠뇌 화살은 다음 틱에 다시 가속합니다.
        if (arrow.weapon?.type == Material.CROSSBOW) {
            val player = plugin.server.getPlayer(owner) ?: return
            val multiplier = config!!.getDouble("speedMultiplier")
            object : com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
                override fun tick() {
                    if (arrow.isValid && !arrow.isInBlock && arrow.uniqueId in linear) arrow.velocity = arrow.velocity.multiply(multiplier)
                }
            }.runTaskLater(plugin, 1L)
        }
    }

    override fun release(owner: UUID) { linear.entries.removeIf { it.value.first == owner } }
    override fun stop() { linear.clear() }
}
