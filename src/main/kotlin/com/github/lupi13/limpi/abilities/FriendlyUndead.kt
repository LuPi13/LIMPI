package com.github.lupi13.limpi.abilities

import io.papermc.paper.tag.EntitySetTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Bogged
import org.bukkit.entity.Drowned
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityCategory
import org.bukkit.entity.Husk
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
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
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerExpChangeEvent

object FriendlyUndead : Ability(
    grade = Grade.COMMON,
    element = Element.UNDEAD,
    displayName = Component.text("우호적 언데드", NamedTextColor.DARK_GREEN),
    codeName = "friendly_undead",
    material = Material.ZOMBIE_HEAD,
    description = listOf(
        Component.text("언데드 몹이 나를 적대하지 않습니다.", NamedTextColor.WHITE))
) {
    val undead: List<Class<out Entity>> = listOf(
        Zombie::class.java, ZombieVillager::class.java, Husk::class.java, Drowned::class.java,
        Skeleton::class.java, Stray::class.java, WitherSkeleton::class.java, Bogged::class.java,
        Phantom::class.java, PigZombie::class.java, Zoglin::class.java, SkeletonHorse::class.java,
        ZombieHorse::class.java, Wither::class.java
    )
    @EventHandler
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        val target = event.target
        if (target == null || (event.target !is Player)) return
        val player = target as Player
        if (player.ability != this) return

        val entity = event.entity
        if (!(undead.any { it.isInstance(entity)})) return
        event.isCancelled = true
    }
}