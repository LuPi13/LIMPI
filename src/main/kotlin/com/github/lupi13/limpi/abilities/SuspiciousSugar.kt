package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

object SuspiciousSugar : Ability(
    grade = Grade.COMMON,
    element = Element.MAGIC,
    displayName = Component.text("정체불명의 흰 가루", NamedTextColor.WHITE),
    codeName = "suspicious_sugar",
    material = Material.SUGAR,
    description = listOf(
        Component.text("설탕을 우클릭하면 무작위 상태 효과를 얻습니다.", NamedTextColor.WHITE),
        Component.text("확률적으로 해당 상태 효과를", NamedTextColor.WHITE),
        Component.text("주변 엔티티에게도 적용시킵니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("설탕을 우클릭하면 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("duration") / 20}", NamedTextColor.GREEN))
                .append(Component.text("초의 무작위 상태 효과를 얻습니다.", NamedTextColor.WHITE)),
            Component.text("${config!!.getDouble("areaChance") * 100}%", NamedTextColor.GREEN)
                .append(Component.text(" 확률로 해당 상태 효과를 주변 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("range") / 2.0}", NamedTextColor.GREEN))
                .append(Component.text("블록 내의", NamedTextColor.WHITE)),
            Component.text("모든 엔티티에게도 적용시킵니다.", NamedTextColor.WHITE),
            Component.text("분명히 사탕수수로 만든 정직한 설탕일텐데...", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH)
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("areaChance", 0.1)
            config?.set("duration", 200)
            config?.set("range", 10.0)
        }
        saveConfig()
    }


    @EventHandler
    fun onClick(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this) return
        if (!event.action.isRightClick) return
        if (event.item == null || event.item!!.type != Material.SUGAR) return

        event.item!!.amount -= 1 // 설탕 아이템 사용

        // 랜덤 효과 적용
        val effects = Registry.POTION_EFFECT_TYPE.iterator()
        val randomEffect = effects.asSequence().filterNotNull().shuffled().first()
        val duration = when (randomEffect) {
            PotionEffectType.INSTANT_DAMAGE -> 1
            PotionEffectType.INSTANT_HEALTH -> 1
            else -> config!!.getInt("duration")
        }
        player.addPotionEffect(PotionEffect(randomEffect, duration, 0))

        // 확률적으로 주변 엔티티에게도 적용
        if (Random.nextDouble() <= config!!.getDouble("areaChance")) {
            val range = config!!.getDouble("range")
            val area = player.getNearbyEntities(range, range, range)
            for (entity in area) {
                if (entity is LivingEntity && entity != player) {
                    entity.addPotionEffect(PotionEffect(randomEffect, duration, 0))
                }
            }
        }
        player.world.spawnParticle(Particle.SPIT, player.eyeLocation, 30, 0.0, 0.0, 0.0, 1.0)
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SHOOT, 0.4f, 2f)
    }
}