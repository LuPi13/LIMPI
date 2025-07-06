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
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerExpChangeEvent

object FriendlyCreeper : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("우호적 크리퍼", NamedTextColor.GREEN),
    codeName = "friendly_creeper",
    material = Material.CREEPER_HEAD,
    description = listOf(
        Component.text("크리퍼가 나를 적대하지 않습니다.", NamedTextColor.WHITE))
) {
    @EventHandler
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        val target = event.target
        if (target == null || (event.target !is Player)) return
        val player = target as Player
        if (player.ability != this) return

        val entity = event.entity
        if (entity.type != EntityType.CREEPER) return
        event.isCancelled = true
    }
}