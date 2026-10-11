package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.EnoughDodge
import com.github.lupi13.limpi.quests.Quest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent

object LightReflex : DodgeVariant(
    grade = Grade.EPIC,
    element = Element.ELECTRIC,
    displayName = Component.text("번개와 같은 반사신경", NamedTextColor.YELLOW),
    codeName = "light_reflex",
    material = Material.SCULK_VEIN,
    description = listOf(
        Component.text("회피", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("회피 중에 받는 피해가 크게 감소합니다.", NamedTextColor.WHITE)
    )
) {
    override val unlockQuest: Quest get() = EnoughDodge

    override val details: List<Component> by settingsCached {
        listOf(
            inheritLine(),
            Component.text("회피 후 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("window") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초 동안 받는 피해가 ", NamedTextColor.WHITE))
                .append(Component.text("${Math.round((1 - config!!.getDouble("damage_multiplier")) * 1000) / 10.0}", NamedTextColor.GREEN).append(Component.text("%", NamedTextColor.WHITE)))
                .append(Component.text(" 감소합니다.", NamedTextColor.WHITE)),
            cooldownLine()
        )
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability != this) return
        if (!dodgedWithin(player.uniqueId, config!!.getInt("window"))) return
        event.damage *= config!!.getDouble("damage_multiplier")
        player.world.spawnParticle(Particle.ELECTRIC_SPARK, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.1)
    }
}
