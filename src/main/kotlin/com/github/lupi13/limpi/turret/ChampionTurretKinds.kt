package com.github.lupi13.limpi.turret

import com.github.lupi13.limpi.config.AbilityField
import com.github.lupi13.limpi.config.integer
import com.github.lupi13.limpi.config.number
import com.github.lupi13.limpi.platform.AbilityDamage
import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.damage.DamageType
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.ShulkerBullet
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 경비 탑 챔피언의 포탑 종류. 수치는 능력 설정 파일에서 종류 id 접두사(diamond_, echo_, amethyst_, shulker_, emerald_)로 바꿉니다.

private const val DIAMOND_PIERCE = "pierce"
private const val AMETHYST_SLOW_SECONDS = "slowness_seconds"
private const val AMETHYST_SLOW_LEVEL = "slowness_level"
private const val SHULKER_LEVITATION = "levitation_seconds"
private const val EMERALD_HEAL = "heal_per_second"

/** 다이아몬드 포탑: 철 포탑의 강화판. 더 세고 빠르고 멀리, 더 평평하게 쏘며 한 발이 적을 꿰뚫어 뒤의 적도 맞힙니다. */
object DiamondTurretKind : TurretKind(
    id = "diamond",
    trigger = Material.DIAMOND,
    itemName = "다이아몬드",
    color = DyeColor.LIGHT_BLUE,
    ammo = Material.DIAMOND,
    speed = 2.0,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 2, fireIntervalTicks = 12, damage = 8.0, maxHealth = 50.0, decayPerSecond = 0.5,
        range = 32.0, extra = mapOf(DIAMOND_PIERCE to 1.0)),
) {
    override fun extraFields(prefix: String): List<AbilityField> =
        listOf(integer(TurretStats.key(prefix, PIERCE), defaults.extra(PIERCE).toInt(), 0, 16)) // 처음 맞힌 적 뒤로 더 꿰뚫는 수

    override fun pierce(stats: TurretStats): Int = stats.extra(PIERCE).toInt()

    const val PIERCE = DIAMOND_PIERCE
}

/**
 * 메아리 포탑: 벽을 무시하고 표적을 향해 사거리 끝까지 워든처럼 음파를 쏩니다. 음파가 지나는 직선 위(폭 [BEAM_RADIUS])의 적을
 * 모두 맞히며 방어구를 무시하는 피해를 줍니다.
 */
object EchoTurretKind : TurretKind(
    id = "echo",
    trigger = Material.ECHO_SHARD,
    itemName = "메아리 조각",
    color = DyeColor.CYAN,
    ammo = Material.ECHO_SHARD,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 2, fireIntervalTicks = 60, damage = 8.0, maxHealth = 30.0, decayPerSecond = 0.1,
        range = 24.0),
) {
    override val needsSight: Boolean get() = false

    override fun fire(turret: TurretRuntime.PlacedTurret, target: LivingEntity) {
        val owner = Bukkit.getPlayer(turret.owner) ?: return
        val from = turret.center()
        val direction = target.boundingBox.center.subtract(from.toVector())
        if (direction.lengthSquared() < 1.0e-9) return
        direction.normalize()
        val length = turret.stats.range
        for (i in 1..length.toInt().coerceAtLeast(1)) from.world.spawnParticle(Particle.SONIC_BOOM, from.clone().add(direction.clone().multiply(i)), 1)
        from.world.playSound(from, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.3f)
        val shot = TurretRuntime.Shot(turret.id, this, turret.ability, turret.stats)
        val origin = from.toVector()
        for (enemy in TurretRuntime.enemiesInRange(turret, owner)) {
            if (enemy.boundingBox.clone().expand(BEAM_RADIUS).rayTrace(origin, direction, length) == null) continue
            TurretRuntime.strike(shot, owner, enemy, enemy.boundingBox.center.toLocation(from.world))
        }
    }

    /** 음파 줄기의 굵기(판정 상자를 넓히는 양, 블록). */
    const val BEAM_RADIUS = 0.5

    override fun onHit(shot: TurretRuntime.Shot, owner: Player, target: Damageable): Boolean =
        AbilityDamage.hit(owner, target, shot.ability, shot.stats.damage, knockback = false, type = DamageType.SONIC_BOOM,
            direct = TurretRuntime.body(shot))

    override fun hitEffect(at: Location) {}
}

/** 자수정 포탑: 약한 자수정 조각을 쏴 구속을 겁니다. 구속이 걸리지 않은 적을 먼저 노립니다. */
object AmethystTurretKind : TurretKind(
    id = "amethyst",
    trigger = Material.AMETHYST_SHARD,
    itemName = "자수정 조각",
    color = DyeColor.MAGENTA,
    ammo = Material.AMETHYST_SHARD,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 1, fireIntervalTicks = 20, damage = 1.0, maxHealth = 30.0, decayPerSecond = 1.0,
        range = 14.0, extra = mapOf(AMETHYST_SLOW_SECONDS to 3.0, AMETHYST_SLOW_LEVEL to 2.0)),
) {
    override fun extraFields(prefix: String): List<AbilityField> = listOf(
        number(TurretStats.key(prefix, SLOW_SECONDS), defaults.extra(SLOW_SECONDS), 0.0, 600.0), // 구속 시간(초)
        integer(TurretStats.key(prefix, SLOW_LEVEL), defaults.extra(SLOW_LEVEL).toInt(), 1, 10), // 구속 단계(2 = 구속 II)
    )

    override fun pickTarget(candidates: List<LivingEntity>, center: Location): LivingEntity? =
        super.pickTarget(candidates.filter { !it.hasPotionEffect(PotionEffectType.SLOWNESS) }, center) ?: super.pickTarget(candidates, center)

    override fun onHit(shot: TurretRuntime.Shot, owner: Player, target: Damageable): Boolean {
        val accepted = super.onHit(shot, owner, target)
        val ticks = (shot.stats.extra(SLOW_SECONDS) * 20).toInt()
        if (accepted && target is LivingEntity && !target.isDead && ticks > 0) {
            TurretRuntime.potion(shot, target, PotionEffect(PotionEffectType.SLOWNESS, ticks, shot.stats.extra(SLOW_LEVEL).toInt() - 1, false, true, true))
        }
        return accepted
    }

    override fun hitEffect(at: Location) {
        super.hitEffect(at)
        at.world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f)
    }

    const val SLOW_SECONDS = AMETHYST_SLOW_SECONDS
    const val SLOW_LEVEL = AMETHYST_SLOW_LEVEL
}

/** 셜커 포탑: 진짜 셜커 탄환을 쏩니다(표적을 따라감). 맞은 적은 피해와 함께 잠깐 공중에 뜹니다. */
object ShulkerTurretKind : TurretKind(
    id = "shulker",
    trigger = Material.SHULKER_SHELL,
    itemName = "셜커 껍데기",
    color = DyeColor.PURPLE,
    ammo = Material.SHULKER_SHELL,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 2, fireIntervalTicks = 60, damage = 5.0, maxHealth = 40.0, decayPerSecond = 0.25,
        range = 16.0, extra = mapOf(SHULKER_LEVITATION to 5.0)),
) {
    override fun extraFields(prefix: String): List<AbilityField> =
        listOf(number(TurretStats.key(prefix, LEVITATION), defaults.extra(LEVITATION), 0.0, 60.0)) // 공중부양 시간(초)

    override fun fire(turret: TurretRuntime.PlacedTurret, target: LivingEntity) {
        val owner = Bukkit.getPlayer(turret.owner) ?: return
        val from = turret.center()
        val direction = target.boundingBox.center.subtract(from.toVector())
        if (direction.lengthSquared() < 1.0e-9) return
        val start = from.clone().add(direction.normalize().multiply(0.8))
        val bullet = from.world.spawn(start, ShulkerBullet::class.java) { bullet ->
            bullet.shooter = owner
            bullet.target = target
        }
        if (!bullet.isValid) return
        TurretRuntime.registerShot(turret, bullet)
        from.world.playSound(from, Sound.ENTITY_SHULKER_SHOOT, 0.8f, 1.0f)
    }

    override fun onHit(shot: TurretRuntime.Shot, owner: Player, target: Damageable): Boolean {
        val accepted = super.onHit(shot, owner, target)
        val ticks = (shot.stats.extra(LEVITATION) * 20).toInt()
        if (accepted && target is LivingEntity && !target.isDead && ticks > 0) {
            TurretRuntime.potion(shot, target, PotionEffect(PotionEffectType.LEVITATION, ticks, 0, false, true, true))
        }
        return accepted
    }

    override fun hitEffect(at: Location) {
        at.world.spawnParticle(Particle.END_ROD, at, 6, 0.2, 0.2, 0.2, 0.02)
    }

    const val LEVITATION = SHULKER_LEVITATION
}

/**
 * 에메랄드 포탑: 공격하지 않고 1초마다 맞닿은 아군 포탑(주인 본인·같은 팀)의 체력을 [HEAL]만큼 회복합니다.
 * 에메랄드 포탑끼리는 회복하지 않습니다. 맞닿은 금 포탑의 가속은 회복량에 곱해집니다(금 1개 +50%).
 */
object EmeraldTurretKind : TurretKind(
    id = "emerald",
    trigger = Material.EMERALD,
    itemName = "에메랄드",
    color = DyeColor.LIME,
    ammo = Material.EMERALD,
    defaults = TurretStats(ingotCost = 2, redstoneCost = 1, fireIntervalTicks = 20, damage = 0.0, maxHealth = 30.0, decayPerSecond = 1.0,
        range = 1.0, extra = mapOf(EMERALD_HEAL to 1.0)),
) {
    override val attacks: Boolean get() = false

    override fun extraFields(prefix: String): List<AbilityField> =
        listOf(number(TurretStats.key(prefix, HEAL), defaults.extra(HEAL), 0.0, 1024.0)) // 1초마다 맞닿은 포탑 회복량

    override fun pulse(turret: TurretRuntime.PlacedTurret, neighbors: List<TurretRuntime.PlacedTurret>) {
        val amount = TurretHeal.amount(turret.stats.extra(HEAL), turret.speed)
        if (amount <= 0.0) return
        for (neighbor in neighbors) {
            if (neighbor.kind === this) continue // 에메랄드끼리는 회복하지 않음
            val body = neighbor.entity
            val max = body.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: continue
            if (body.health >= max) continue
            body.heal(amount, EntityRegainHealthEvent.RegainReason.CUSTOM)
            body.world.spawnParticle(Particle.HAPPY_VILLAGER, neighbor.center(), 4, 0.3, 0.3, 0.3, 0.0)
        }
    }

    const val HEAL = EMERALD_HEAL
}
