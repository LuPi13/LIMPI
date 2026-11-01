package com.github.lupi13.limpi.turret

import com.github.lupi13.limpi.config.AbilityField
import com.github.lupi13.limpi.config.AbilitySettings
import com.github.lupi13.limpi.config.integer
import com.github.lupi13.limpi.config.number
import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.cos
import kotlin.math.sin

/**
 * 포탑 종류(확장 지점). 능력은 들고 우클릭한 [trigger] 아이템으로 종류를 고릅니다.
 * 철(포탑 설치), 철·구리·금(경비 전문가)이 있고 더 높은 능력은 종류를 더해 [com.github.lupi13.limpi.abilities.TurretAbility.kinds]에 나열합니다.
 * - 다른 공격은 [fire]와 [onHit]을 바꿔 만듭니다(구리: 범위 화염).
 * - 공격하지 않는 지원 포탑은 [attacks]를 false로 두고 [speedBonus]처럼 맞닿은 포탑에 주는 효과를 정합니다(금).
 * - 종류만의 설정은 [extraFields]로 더하고 [TurretStats.extra]에서 읽습니다.
 * 수치는 능력 설정 파일에서 [prefix]가 붙은 키로 읽습니다.
 */
abstract class TurretKind(
    /** 설정 키 접두사와 기록에 쓰는 이름(예: "iron"). */
    val id: String,
    /** 들고 우클릭하면 이 포탑을 설치하는 아이템. 이 아이템이 손에서 소비됩니다. */
    val trigger: Material,
    /** 안내 문구에 쓰는 [trigger]의 이름(예: "철 주괴"). */
    val itemName: String,
    /** 셜커 껍데기 색. */
    val color: DyeColor,
    /** 투사체 겉모습과 명중 파티클. */
    val ammo: Material,
    /** 투사체 초기 속도(블록/틱). */
    val speed: Double = 1.5,
    /** 이 종류의 기본 수치. */
    val defaults: TurretStats,
) {
    /** false면 적을 찾지도 쏘지도 않는 지원 포탑입니다(공격 설정 키도 만들지 않음). */
    open val attacks: Boolean get() = true

    /** 이 종류만의 설정 항목(예: 화염 시간). */
    protected open fun extraFields(prefix: String): List<AbilityField> = emptyList()

    /** 이 종류의 설정 항목. [prefix]가 빈 문자열이면 접두사 없이 씁니다(종류가 하나인 능력). */
    fun fields(prefix: String): List<AbilityField> = TurretStats.fields(prefix, defaults, attacks) + extraFields(prefix)

    fun stats(settings: AbilitySettings, prefix: String): TurretStats {
        val base = TurretStats.read(settings, prefix, defaults, attacks)
        val extra = extraFields(prefix).associate { field -> field.key.removePrefix(TurretStats.key(prefix, "")) to settings.getDouble(field.key) }
        return base.copy(extra = extra)
    }

    /** 맞닿은 포탑의 공격 속도에 더하는 몫(금 포탑 0.25 = +25%). */
    open fun speedBonus(stats: TurretStats): Double = 0.0

    /** 목표를 향해 한 발 쏩니다. 기본은 중력을 받는 투척물(눈덩이에 [ammo] 겉모습). */
    open fun fire(turret: TurretRuntime.PlacedTurret, target: LivingEntity) = TurretRuntime.throwShot(turret, target)

    /** 투사체가 [target]에 맞았을 때. 기본은 주인의 능력 피해. 피해가 실제로 들어갔으면 true. */
    open fun onHit(shot: TurretRuntime.Shot, owner: Player, target: Damageable): Boolean =
        com.github.lupi13.limpi.platform.AbilityDamage.hit(owner, target, shot.ability, shot.stats.damage, knockback = false)

    /** 명중 지점의 연출. 기본은 탄 조각. */
    open fun hitEffect(at: Location) {
        at.world.spawnParticle(Particle.ITEM, at, 6, 0.1, 0.1, 0.1, 0.05, ItemStack(ammo))
    }
}

/** 한 포탑 종류의 설정 가능한 수치. 지원 포탑은 공격 수치(발사 간격·피해·사거리)를 기본값 그대로 둡니다. */
data class TurretStats(
    val ingotCost: Int,
    val redstoneCost: Int,
    val fireIntervalTicks: Int,
    val damage: Double,
    val maxHealth: Double,
    val decayPerSecond: Double,
    /** 시야 반경(블록). */
    val range: Double,
    /** 종류만의 수치([TurretKind.extraFields]의 키에서 접두사를 뗀 이름). */
    val extra: Map<String, Double> = emptyMap(),
) {
    fun extra(name: String): Double = extra.getValue(name)

    companion object {
        fun key(prefix: String, name: String) = if (prefix.isEmpty()) name else "${prefix}_$name"

        fun fields(prefix: String, defaults: TurretStats, attacks: Boolean = true): List<AbilityField> = buildList {
            add(integer(key(prefix, "ingot_cost"), defaults.ingotCost, 1, 64)) // 손에서 소비하는 주괴(설치 아이템) 수
            add(integer(key(prefix, "redstone_cost"), defaults.redstoneCost, 0, 2304)) // 인벤토리에서 소비하는 레드스톤 수
            if (attacks) {
                add(integer(key(prefix, "fire_interval_ticks"), defaults.fireIntervalTicks, 1, 72000)) // 발사 간격
                add(number(key(prefix, "damage"), defaults.damage, 0.0, 1024.0)) // 한 발의 피해
            }
            add(number(key(prefix, "max_health"), defaults.maxHealth, 1.0, 1024.0)) // 포탑 최대 체력(설치 시 가득)
            add(number(key(prefix, "decay_per_second"), defaults.decayPerSecond, 0.0, 1024.0)) // 1초마다 줄어드는 체력
            if (attacks) add(number(key(prefix, "range"), defaults.range, 1.0, 64.0)) // 시야 반경(블록)
        }

        fun read(settings: AbilitySettings, prefix: String, defaults: TurretStats, attacks: Boolean = true) = TurretStats(
            settings.getInt(key(prefix, "ingot_cost")),
            settings.getInt(key(prefix, "redstone_cost")),
            if (attacks) settings.getInt(key(prefix, "fire_interval_ticks")) else defaults.fireIntervalTicks,
            if (attacks) settings.getDouble(key(prefix, "damage")) else defaults.damage,
            settings.getDouble(key(prefix, "max_health")),
            settings.getDouble(key(prefix, "decay_per_second")),
            if (attacks) settings.getDouble(key(prefix, "range")) else defaults.range,
        )
    }
}

private const val COPPER_BURN = "burn_seconds"
private const val GOLD_SPEED = "speed_bonus"

/** 철 포탑: 철 주괴로 설치, 철 조각 모양의 투척물을 쏩니다. */
object IronTurretKind : TurretKind(
    id = "iron",
    trigger = Material.IRON_INGOT,
    itemName = "철 주괴",
    color = DyeColor.LIGHT_GRAY,
    ammo = Material.IRON_NUGGET,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 1, fireIntervalTicks = 20, damage = 4.0, maxHealth = 30.0, decayPerSecond = 1.0,
        range = 16.0),
)

/**
 * 구리 포탑: 사거리(기본 2블록) 안에 적이 들어오면 공격 간격마다 사거리 안의 보이는 적 전부에게 피해를 주고 불을 붙입니다.
 * 투사체 없이 바로 맞히며, 맞은 공격적인 몹은 다른 포탑처럼 이 포탑에 고정됩니다.
 */
object CopperTurretKind : TurretKind(
    id = "copper",
    trigger = Material.COPPER_INGOT,
    itemName = "구리 주괴",
    color = DyeColor.ORANGE,
    ammo = Material.COPPER_INGOT,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 1, fireIntervalTicks = 40, damage = 2.0, maxHealth = 40.0, decayPerSecond = 1.0,
        range = 2.0, extra = mapOf(COPPER_BURN to 3.0)),
) {
    override fun extraFields(prefix: String): List<AbilityField> =
        listOf(number(TurretStats.key(prefix, BURN), defaults.extra(BURN), 0.0, 600.0)) // 불이 붙어 있는 시간(초)

    override fun fire(turret: TurretRuntime.PlacedTurret, target: LivingEntity) {
        val owner = Bukkit.getPlayer(turret.owner) ?: return
        val center = turret.center()
        val radius = turret.stats.range
        val points = (radius * 8).toInt().coerceIn(8, 64)
        for (i in 0 until points) {
            val angle = 2 * Math.PI * i / points
            center.world.spawnParticle(Particle.FLAME, center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius), 1, 0.0, 0.05, 0.0, 0.01)
        }
        center.world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.2f)
        val shot = TurretRuntime.Shot(turret.id, this, turret.ability, turret.stats)
        for (enemy in TurretRuntime.enemiesInRange(turret, owner)) {
            TurretRuntime.strike(shot, owner, enemy, enemy.boundingBox.center.toLocation(center.world))
        }
    }

    override fun onHit(shot: TurretRuntime.Shot, owner: Player, target: Damageable): Boolean {
        val accepted = super.onHit(shot, owner, target)
        val ticks = (shot.stats.extra(BURN) * 20).toInt()
        if (accepted && !target.isDead && ticks > 0) target.fireTicks = maxOf(target.fireTicks, ticks)
        return accepted
    }

    override fun hitEffect(at: Location) {
        at.world.spawnParticle(Particle.FLAME, at, 6, 0.2, 0.3, 0.2, 0.02)
    }

    const val BURN = COPPER_BURN
}

/** 금 포탑: 공격하지 않고, 맞닿은 여섯 면의 포탑(나와 같은 팀 플레이어의 포탑)의 공격 속도를 금 포탑 하나당 [SPEED]만큼 올립니다. */
object GoldTurretKind : TurretKind(
    id = "gold",
    trigger = Material.GOLD_INGOT,
    itemName = "금 주괴",
    color = DyeColor.YELLOW,
    ammo = Material.GOLD_NUGGET,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 1, fireIntervalTicks = 20, damage = 0.0, maxHealth = 30.0, decayPerSecond = 1.0,
        range = 1.0, extra = mapOf(GOLD_SPEED to 0.25)),
) {
    override val attacks: Boolean get() = false

    override fun extraFields(prefix: String): List<AbilityField> =
        listOf(number(TurretStats.key(prefix, SPEED), defaults.extra(SPEED), 0.0, 10.0)) // 맞닿은 포탑 공격 속도 증가(0.25 = +25%)

    override fun speedBonus(stats: TurretStats): Double = stats.extra(SPEED)

    const val SPEED = GOLD_SPEED
}
