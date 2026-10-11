package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.combat.HitKind
import com.github.lupi13.limpi.platform.AbilityDamage
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.DarkTime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.logging.Level
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object Chronobreak : TimeLeapAbility(
    grade = Grade.LEGENDARY,
    displayName = Component.text("시공간 붕괴", NamedTextColor.DARK_PURPLE),
    codeName = "chronobreak",
    material = Material.MUSIC_DISC_5,
    description = listOf(
        Component.text("시간 역행", NamedTextColor.AQUA)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("시간 역행 후 도착 지점 주변의 모든 ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("에게", NamedTextColor.WHITE)),
        Component.text("최대 체력에 비례한 피해를 주고,", NamedTextColor.WHITE),
        Component.text("준 피해에 비례하여 흡수 효과를 얻습니다.", NamedTextColor.WHITE),
        Component.text("쿨타임이 감소합니다.", NamedTextColor.WHITE)
    ),
    acquisition = AcquisitionType.QUEST
) {
    override val activeName: Component = Component.text("시공간 붕괴", NamedTextColor.DARK_PURPLE)

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(DarkTime.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("시간 역행", NamedTextColor.AQUA)
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE))
        ) + rewindLines() + listOf(
            Component.text("도착 후 주변 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("radius")}", NamedTextColor.GREEN))
                .append(Component.text("블록 안의 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 피해를 주고,", NamedTextColor.WHITE)),
            Component.text("준 피해에 비례해 ", NamedTextColor.WHITE)
                .append(Component.text(seconds(config!!.getInt("absorption_ticks")), NamedTextColor.GREEN))
                .append(Component.text("초 동안 ", NamedTextColor.WHITE))
                .append(Component.text("흡수", NamedTextColor.BLUE))
                .append(Component.text(" 효과를 얻습니다.", NamedTextColor.WHITE)),
            cooldownLine()
        )
    }

    private val absorption by lazy { PotionEffectType.ABSORPTION }

    override fun afterRewind(player: Player, before: Double, after: Double) {
        val location = player.location
        val world = player.world
        for (pitch in floatArrayOf(2f, 1.7f, 1.4f)) world.playSound(location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, pitch)
        repeat(360) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            world.spawnParticle(Particle.SCULK_CHARGE_POP, location, 0, cos(angle), 0.0, sin(angle), 1.0, null, true)
        }

        val radius = config!!.getDouble("radius")
        val ratio = config!!.getDouble("max_health_damage_ratio")
        var dealt = 0.0
        for (entity in player.getNearbyEntities(radius, radius, radius)) {
            if (entity !is LivingEntity || !AbilityManager.isEnemy(player, entity)) continue
            val damage = (entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: continue) * ratio
            if (!damage.isFinite() || damage <= 0.0) continue
            if (AbilityDamage.hit(player, entity, codeName, damage, HitKind.ABILITY)) dealt += damage
            if (!player.isValid || player.isDead) return
        }

        val maxLevel = config!!.getInt("max_absorption_level")
        if (dealt <= 0.0 || maxLevel <= 0) return
        val amplifier = minOf(maxLevel - 1, (dealt / config!!.getDouble("damage_per_absorption_level")).toInt().coerceAtLeast(0))
        val ticks = config!!.getInt("absorption_ticks")
        try {
            // A separate scope keeps the absorption after the rewind ends; it is removed on death/quit/ability change.
            val buff = AbilityRuntime.begin(player, codeName)
            buff.potion(player, PotionEffect(absorption, ticks, amplifier, true, true, true))
            var elapsed = 0
            buff.repeat { if (++elapsed >= ticks) it.close() }
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Unable to grant $codeName absorption to ${player.uniqueId}", failure)
        }
    }
}
