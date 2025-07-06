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
import org.bukkit.entity.Projectile
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

object DefenseAllIn : Ability(
    grade = Grade.RARE,
    element = Element.DEFENSIVE,
    displayName = Component.text("아픈 건 싫으니까 방어력에 올인하려고 합니다.", NamedTextColor.LIGHT_PURPLE),
    codeName = "defense_all_in",
    material = Material.NETHERITE_CHESTPLATE,
    description = listOf(
        Component.text("피격 후 무적시간이 증가합니다.", NamedTextColor.WHITE),
        Component.text("받는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
        Component.text("가하는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("피격 후 무적시간이 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getInt("noDamageMilliseconds") / 1000.0, NamedTextColor.GREEN))
                .append(Component.text("초로 증가합니다.", NamedTextColor.WHITE)),
            Component.text("받는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
            Component.text("가하는 모든 피해량을 1로 바꿉니다.", NamedTextColor.WHITE),
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("noDamageMilliseconds", 1000)
        }
        saveConfig()
    }


    val lastDamageTime = mutableMapOf<Player, Long>()
    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (player.ability != this) return
        if (lastDamageTime.containsKey(player) && (System.currentTimeMillis() - lastDamageTime[player]!! <= config!!.getInt("noDamageMilliseconds"))) {
            event.isCancelled = true
            return
        }

        event.setDamage(1.0)
        lastDamageTime[player] = System.currentTimeMillis()
    }


    @EventHandler
    fun onAttack(event: EntityDamageByEntityEvent) {
        if (event.damager is Player) {
            val player = event.damager as Player
            if (player.ability != this) return

            event.damage = 1.0
            event.setDamage(1.0)
        }

        else if (event.damager is Projectile) {
            if ((event.damager as Projectile).shooter !is Player) return
            val player = (event.damager as Projectile).shooter as Player
            if (player.ability != this) return

            event.damage = 1.0
            event.setDamage(1.0)
        }
    }
}