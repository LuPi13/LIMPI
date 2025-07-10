package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent

object WaterMeleeBoost : Ability(
    grade = Grade.COMMON,
    element = Element.WATER,
    displayName = Component.text("수중 강타", NamedTextColor.AQUA),
    codeName = "water_melee_boost",
    material = Material.WATER_BUCKET,
    description = listOf(
        Component.text("물에서 근접공격 시 더 강한 피해를 줍니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("물에서 근접공격 시 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("damageBoost")}", NamedTextColor.GREEN))
                .append(Component.text("배의 피해를 줍니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("damageBoost", 1.5)
        }
        saveConfig()
    }


    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager
        if (attacker !is Player || attacker.ability != this) return

        if (!attacker.isInWater) return

        event.damage *= config!!.getDouble("damageBoost")
    }
}