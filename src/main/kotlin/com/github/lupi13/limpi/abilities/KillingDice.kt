package com.github.lupi13.limpi.abilities

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent
import io.papermc.paper.command.brigadier.argument.ArgumentTypes.world
import io.papermc.paper.tag.EntitySetTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

object KillingDice : Ability(
    grade = Grade.EPIC,
    element = Element.MAGIC,
    displayName = Component.text("죽음의 주사위", NamedTextColor.DARK_RED),
    codeName = "killing_dice",
    material = Material.END_CRYSTAL,
    description = listOf(
        Component.text("가하는 모든 피해가 감소하는 대신,", NamedTextColor.WHITE),
        Component.text("대상이 잃은 체력에 비례하여", NamedTextColor.WHITE),
                Component.text("확률적으로 즉사시킵니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("가하는 모든 피해가 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getDouble("damageReduction"), NamedTextColor.GREEN))
                .append(Component.text("배로 감소합니다.", NamedTextColor.WHITE)),
            Component.text("대상이 잃은 체력에 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getDouble("instantKillDegree"), NamedTextColor.GREEN))
                .append(Component.text("제곱으로 즉사 확률이 발생합니다.", NamedTextColor.WHITE)),
            Component.text("예를 들어, 대상이 70%(=0.7)의 체력을 잃었을 때,", NamedTextColor.WHITE),
            Component.text("즉사 확률은 0.7^", NamedTextColor.WHITE)
                .append(Component.text(config!!.getDouble("instantKillDegree"), NamedTextColor.GREEN))
                .append(Component.text(" = ", NamedTextColor.WHITE))
                .append(Component.text(String.format("%.2f", (0.7).pow(config!!.getDouble("instantKillDegree"))), NamedTextColor.GREEN))
                .append(Component.text("(=${String.format("%.2f", (0.7).pow(config!!.getDouble("instantKillDegree")) * 100)}%)", NamedTextColor.WHITE))
                .append(Component.text("입니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("damageReduction", 0.5)
            config?.set("instantKillDegree", 2.0)
            config?.set("KillDelay", 10)
        }
        saveConfig()
    }

    /**
     * 대상의 잃은 체력에 비례하여 즉사시킵니다.
     * @param player 즉사시킬 플레이어
     * @param target 즉사시킬 대상
     */
    fun killFromDice(player: Player, target: LivingEntity) {
        if (target.isDead || !target.isValid) return

        // 대상의 잃은 체력에 비례하여 즉사 확률 계산
        val instantKillProbability = ((1 - target.health / target.getAttribute(Attribute.MAX_HEALTH)!!.baseValue).pow(config!!.getDouble("instantKillDegree")))
        val killDelay = config!!.getInt("KillDelay")
        if (Random.nextDouble() <= instantKillProbability) {
            target.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, max(killDelay, 20), 0, true, false, false))
            player.world.playSound(target.location, Sound.ENTITY_VEX_DEATH, 1.0F, 2.0F)

            // 이펙트를 위한 랜덤 벡터
            val randomVector = Vector(
                Random.nextDouble(-1.0, 1.0),
                Random.nextDouble(-1.0, 1.0),
                Random.nextDouble(-1.0, 1.0)
            ).normalize()
            val effectLength = (
                    target.boundingBox.widthX.pow(2)
                    + target.boundingBox.widthZ.pow(2)
                    + target.boundingBox.height.pow(2)
                    ).pow(0.5)

            object: BukkitRunnable() {
                override fun run() {
                    if (target.isDead) return

                    val center = target.boundingBox.center.toLocation(target.world)

                    for (i in -100..100) {
                        val dustTransition = Particle.DustTransition(
                            Color.fromRGB(
                                Random.nextInt(10, 100),
                                Random.nextInt(0, 30),
                                Random.nextInt(10, 100)
                            ),
                            Color.BLACK,
                            Random.nextDouble(0.1, 1.0).toFloat())

                        target.world.spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            center.clone().add(randomVector.clone().multiply(effectLength * i / 100.0)),
                            3, 0.01, 0.01, 0.01, 0.1, dustTransition)
                    }
                    target.world.spawnParticle(
                        Particle.SOUL,
                        center.clone(),
                        50, 0.2, 0.2, 0.2, 0.1
                    )
                    target.damage(Double.MAX_VALUE, player)
                    target.health = 0.0

                    target.world.playSound(target.location, Sound.ENTITY_ALLAY_DEATH, 1.0F, 2.0F)
                }
            }.runTaskLater(plugin, killDelay.toLong())
        }
    }


    // 데미지 감소
    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (event.damager is Player) {
            val player = event.damager as Player
            if (player.ability != this) return

            event.damage = event.damage * config!!.getDouble("damageReduction")
            event.setDamage(event.damage)
            killFromDice(player, event.entity as LivingEntity)
        }
        else if (event.damager is Projectile) {
            if ((event.damager as Projectile).shooter !is Player) return
            val player = (event.damager as Projectile).shooter as Player
            if (player.ability != this) return

            event.damage = event.damage * config!!.getDouble("damageReduction")
            event.setDamage(event.damage)
            killFromDice(player, event.entity as LivingEntity)
        }
    }
}