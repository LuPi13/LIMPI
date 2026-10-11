package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.ColdDodge
import com.github.lupi13.limpi.quests.Quest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.random.Random

object WinterShroud : DodgeVariant(
    grade = Grade.LEGENDARY,
    element = Element.WATER,
    displayName = Component.text("겨울의 장막", NamedTextColor.AQUA),
    codeName = "winter_shroud",
    material = Material.WHITE_DYE,
    description = listOf(
        Component.text("회피", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("회피 시 주변 ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("들을 얼립니다.", NamedTextColor.WHITE))
    )
) {
    override val unlockQuest: Quest get() = ColdDodge

    override val details: List<Component> by settingsCached {
        listOf(
            inheritLine(),
            Component.text("회피 시 주변 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("horizontal_range")}", NamedTextColor.GREEN))
                .append(Component.text("블록 내의 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("들을", NamedTextColor.WHITE)),
            Component.text("", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("freeze_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초 동안 얼립니다.", NamedTextColor.WHITE)),
            cooldownLine()
        )
    }

    override fun afterDodge(player: Player) {
        val horizontal = config!!.getDouble("horizontal_range")
        val vertical = config!!.getDouble("vertical_range")
        val freezeTicks = config!!.getInt("freeze_ticks")
        val world = player.world
        val center = player.location
        repeat(PARTICLES) {
            val snow = Vector(Random.nextDouble() - 0.5, Random.nextDouble() - 0.5, Random.nextDouble() - 0.5)
            if (snow.lengthSquared() < 0.000001) return@repeat
            snow.normalize()
            world.spawnParticle(Particle.SNOWFLAKE, center, 0, snow.x, abs(snow.y), snow.z, 1.0, null, true)
        }
        for (entity in player.getNearbyEntities(horizontal, vertical, horizontal)) {
            if (entity !is Damageable || !AbilityManager.isEnemy(player, entity)) continue
            // Outside powder snow the freeze value drops by 2 per tick, and the entity is fully frozen only at or above
            // maxFreezeTicks; start high enough that it stays fully frozen for exactly freeze_ticks.
            val frozen = Math.addExact(entity.maxFreezeTicks, Math.multiplyExact(2, freezeTicks))
            entity.freezeTicks = maxOf(entity.freezeTicks, frozen)
        }
        world.playSound(center, Sound.ENTITY_ALLAY_DEATH, 1f, 1f)
    }

    private const val PARTICLES = 1000
}
