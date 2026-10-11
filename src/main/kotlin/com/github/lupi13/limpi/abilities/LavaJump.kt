package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.normalizeSafely
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.util.Vector
import kotlin.random.Random

object LavaJump : Ability(
    grade = Grade.COMMON,
    element = Element.FIRE,
    displayName = Component.text("용암수제비", NamedTextColor.GOLD),
    codeName = "lava_jump",
    material = Material.LAVA_BUCKET,
    description = listOf(
        Component.text("용암에 닿으면 위로 강하게 튀어오릅니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("용암에 닿으면 피해를 받지 않고", NamedTextColor.WHITE),
            Component.text("위로 강하게 튀어오릅니다. (속도 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("launch_speed")}", NamedTextColor.GREEN))
                .append(Component.text(")", NamedTextColor.WHITE)),
            Component.text("용암에 붙은 불에 의한 피해는 막지 않습니다.", NamedTextColor.YELLOW)
        )
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (event.cause != EntityDamageEvent.DamageCause.LAVA || player.ability != this) return
        if (FileManager.delivery.blocked(player.uniqueId)) return
        event.isCancelled = true
        val spread = config!!.getDouble("horizontal_spread")
        val horizontal = { if (spread > 0.0) Random.nextDouble(-spread, spread) else 0.0 }
        player.velocity = Vector(horizontal(), 2.0, horizontal()).normalizeSafely().multiply(config!!.getDouble("launch_speed"))
    }
}
