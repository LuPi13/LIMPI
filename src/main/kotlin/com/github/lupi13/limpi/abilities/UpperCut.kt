package com.github.lupi13.limpi.abilities

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent
import io.papermc.paper.tag.EntitySetTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
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
import org.bukkit.entity.Snowball
import org.bukkit.entity.SpectralArrow
import org.bukkit.entity.Stray
import org.bukkit.entity.TippedArrow
import org.bukkit.entity.Wither
import org.bukkit.entity.WitherSkeleton
import org.bukkit.entity.Zoglin
import org.bukkit.entity.Zombie
import org.bukkit.entity.ZombieHorse
import org.bukkit.entity.ZombieVillager
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector

object UpperCut : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("어퍼컷", NamedTextColor.DARK_BLUE),
    codeName = "upper_cut",
    material = Material.IRON_GOLEM_SPAWN_EGG,
    description = listOf(
        Component.text("근접 공격의 넉백이 약간 증가합니다.", NamedTextColor.WHITE),
        Component.text("근접 공격의 넉백 방향을", NamedTextColor.WHITE),
        Component.text("수직방향으로 바꿉니다.", NamedTextColor.WHITE),
        Component.text("밀치기", NamedTextColor.AQUA)
            .append(Component.text(" 마법부여에 영향을 받습니다.", NamedTextColor.WHITE))
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("근접 공격의 넉백이 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getDouble("knockBackAmplifier"), NamedTextColor.GREEN))
                .append(Component.text("배 증가합니다.", NamedTextColor.WHITE)),
            Component.text("근접 공격의 넉백 방향을", NamedTextColor.WHITE),
            Component.text("수직방향으로 바꿉니다.", NamedTextColor.WHITE),
            Component.text("밀치기", NamedTextColor.AQUA)
                .append(Component.text(" 마법부여에 영향을 받습니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("knockBackAmplifier", 1.1)
        }
        saveConfig()
    }


    @EventHandler
    fun onKnockBack(event: EntityKnockbackByEntityEvent) {
        if (event.pushedBy !is Player) return
        val player = event.pushedBy as Player
        if (player.ability != this) return
        event.knockback = Vector(
            0.0,
            event.knockback.length() * config!!.getDouble("knockBackAmplifier"),
            0.0
        )
    }
}