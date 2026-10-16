package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.QuestManager
import com.github.lupi13.limpi.quests.UndeadCooperation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent

object FriendlyUndead : Ability(
    grade = Grade.COMMON,
    displayName = Component.text("우호적 언데드", NamedTextColor.DARK_GREEN),
    codeName = "friendly_undead",
    material = Material.ZOMBIE_HEAD,
    description = listOf(
        Component.text("언데드 몹이 나를 적대하지 않습니다.", NamedTextColor.WHITE)),
    relatedQuest = listOf(UndeadCooperation)
) {
    val undead: List<Class<out Entity>> = listOf(
        Zombie::class.java, ZombieVillager::class.java, Husk::class.java, Drowned::class.java,
        Skeleton::class.java, Stray::class.java, WitherSkeleton::class.java, Bogged::class.java,
        Phantom::class.java, PigZombie::class.java, Zoglin::class.java, SkeletonHorse::class.java,
        ZombieHorse::class.java, Wither::class.java
    )
    @EventHandler(ignoreCancelled = true)
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        val target = event.target
        if (target == null || (event.target !is Player)) return
        val player = target as Player
        if (player.ability != this && player.ability != ZombieKing) return

        val entity = event.entity
        if (!(undead.any { it.isInstance(entity)})) return
        event.isCancelled = true
    }


    // UndeadCooperation 퀘스트: 취소된 사망은 처치가 아니므로 최종 결과만 관측한다.
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: EntityDeathEvent) {
        if (event.entity.killer !is Player) return
        val player = event.entity.killer as Player
        val entity = event.entity

        if (player.ability != this) return

        for (targeter in entity.getNearbyEntities(32.0, 32.0, 32.0)) {
            if (!undead.any { it.isInstance(targeter) }) continue
            if (targeter !is org.bukkit.entity.Mob) continue
            if (targeter.target?.uniqueId == entity.uniqueId) {
                QuestManager.clearQuests(player, UndeadCooperation)
                break
            }
        }
    }
}
