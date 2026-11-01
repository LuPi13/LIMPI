package com.github.lupi13.limpi.turret

import com.github.lupi13.limpi.config.AbilityField
import com.github.lupi13.limpi.config.AbilitySettings
import com.github.lupi13.limpi.config.integer
import com.github.lupi13.limpi.config.number
import org.bukkit.DyeColor
import org.bukkit.Material

/**
 * 포탑 종류(확장 지점). 능력은 들고 우클릭한 [trigger] 아이템으로 종류를 고릅니다.
 * 지금은 철 포탑뿐이고, 상위 능력은 금·구리·다이아몬드·네더라이트 종류를 더해 [com.github.lupi13.limpi.abilities.TurretAbility.kinds]에 나열합니다.
 * 다른 투사체·효과는 [fire]와 [onHit]을 바꿔 만듭니다. 수치는 능력 설정 파일에서 [prefix]가 붙은 키로 읽습니다.
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
    /** 투사체 겉모습. */
    val ammo: Material,
    /** 시야 반경(블록). */
    val range: Double = 10.0,
    /** 투사체 초기 속도(블록/틱). */
    val speed: Double = 1.5,
    /** 이 종류의 기본 수치. */
    val defaults: TurretStats,
) {
    /** 이 종류의 설정 항목. [prefix]가 빈 문자열이면 접두사 없이 씁니다(종류가 하나인 능력). */
    fun fields(prefix: String): List<AbilityField> = TurretStats.fields(prefix, defaults)
    fun stats(settings: AbilitySettings, prefix: String): TurretStats = TurretStats.read(settings, prefix)

    /** 목표를 향해 한 발 쏩니다. 기본은 중력을 받는 투척물(눈덩이에 [ammo] 겉모습). */
    open fun fire(turret: TurretRuntime.PlacedTurret, target: org.bukkit.entity.LivingEntity) = TurretRuntime.throwShot(turret, target)

    /** 투사체가 [target]에 맞았을 때. 기본은 주인의 능력 피해. 피해가 실제로 들어갔으면 true. */
    open fun onHit(shot: TurretRuntime.Shot, owner: org.bukkit.entity.Player, target: org.bukkit.entity.Damageable): Boolean =
        com.github.lupi13.limpi.platform.AbilityDamage.hit(owner, target, shot.ability, shot.damage, knockback = false)
}

/** 한 포탑 종류의 설정 가능한 수치. */
data class TurretStats(
    val ingotCost: Int,
    val redstoneCost: Int,
    val fireIntervalTicks: Int,
    val damage: Double,
    val maxHealth: Double,
    val decayPerSecond: Double,
) {
    companion object {
        fun key(prefix: String, name: String) = if (prefix.isEmpty()) name else "${prefix}_$name"

        fun fields(prefix: String, defaults: TurretStats): List<AbilityField> = listOf(
            integer(key(prefix, "ingot_cost"), defaults.ingotCost, 1, 64), // 손에서 소비하는 주괴(설치 아이템) 수
            integer(key(prefix, "redstone_cost"), defaults.redstoneCost, 0, 2304), // 인벤토리에서 소비하는 레드스톤 수
            integer(key(prefix, "fire_interval_ticks"), defaults.fireIntervalTicks, 1, 72000), // 발사 간격
            number(key(prefix, "damage"), defaults.damage, 0.0, 1024.0), // 한 발의 피해
            number(key(prefix, "max_health"), defaults.maxHealth, 1.0, 1024.0), // 포탑 최대 체력(설치 시 가득)
            number(key(prefix, "decay_per_second"), defaults.decayPerSecond, 0.0, 1024.0), // 1초마다 줄어드는 체력
        )

        fun read(settings: AbilitySettings, prefix: String) = TurretStats(
            settings.getInt(key(prefix, "ingot_cost")),
            settings.getInt(key(prefix, "redstone_cost")),
            settings.getInt(key(prefix, "fire_interval_ticks")),
            settings.getDouble(key(prefix, "damage")),
            settings.getDouble(key(prefix, "max_health")),
            settings.getDouble(key(prefix, "decay_per_second")),
        )
    }
}

/** 철 포탑: 철 주괴로 설치, 철 조각 모양의 투척물을 쏩니다. */
object IronTurretKind : TurretKind(
    id = "iron",
    trigger = Material.IRON_INGOT,
    itemName = "철 주괴",
    color = DyeColor.LIGHT_GRAY,
    ammo = Material.IRON_NUGGET,
    defaults = TurretStats(ingotCost = 1, redstoneCost = 1, fireIntervalTicks = 20, damage = 4.0, maxHealth = 30.0, decayPerSecond = 1.0),
)
