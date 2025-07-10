package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileHitEvent

object HeavySnowball : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("돌덩이 눈덩이", NamedTextColor.GRAY),
    codeName = "heavy_snowball",
    material = Material.SNOWBALL,
    description = listOf(
        Component.text("눈덩이에 피해를 부여합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("던진 눈덩이가 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 줍니다.", NamedTextColor.WHITE)),
            Component.text("눈에 돌을 넣었나봐요", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH)
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("damage", 1.0)
        }
        saveConfig()
    }


    @EventHandler
    fun onSnowballHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (projectile !is Snowball) return

        val shooter = projectile.shooter
        if (shooter !is Player || shooter.ability != this) return

        val hitEntity = event.hitEntity
        if (hitEntity !is LivingEntity) return

        hitEntity.damage(config!!.getDouble("damage"), shooter)
    }
}