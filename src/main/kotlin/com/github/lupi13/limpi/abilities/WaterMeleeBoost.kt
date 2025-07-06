package com.github.lupi13.limpi.abilities

import io.papermc.paper.tag.EntitySetTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Bogged
import org.bukkit.entity.Creeper
import org.bukkit.entity.Drowned
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityCategory
import org.bukkit.entity.EntityType
import org.bukkit.entity.Husk
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Phantom
import org.bukkit.entity.PigZombie
import org.bukkit.entity.Player
import org.bukkit.entity.Skeleton
import org.bukkit.entity.SkeletonHorse
import org.bukkit.entity.Stray
import org.bukkit.entity.Wither
import org.bukkit.entity.WitherSkeleton
import org.bukkit.entity.Zoglin
import org.bukkit.entity.Zombie
import org.bukkit.entity.ZombieHorse
import org.bukkit.entity.ZombieVillager
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerExpChangeEvent

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