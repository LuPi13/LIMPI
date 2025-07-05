package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.scheduler.BukkitRunnable

object LetsBoom : Ability(
    grade = Grade.TROLL,
    element = Element.EXPLOSIVE,
    displayName = Component.text("함께 폭⭐사하자", NamedTextColor.DARK_RED),
    codeName = "lets_boom",
    material = Material.TNT,
    description = listOf(
        Component.text("죽으면 3초 후 6번의 큰 폭발을 일으킵니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
        Component.text("지형을 파괴하지는 않습니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
) {


    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        if (player.ability != this) return

        // 3초 후에 6번의 큰 폭발을 일으킴
        val location = player.location.clone().add(0.0, 0.5, 0.0)
        object: BukkitRunnable() {
            var timer = 0
            override fun run() {
                if ((timer >= 0) && (timer < 40)) {
                    // 1초 후에 폭발 시작
                    if (timer <= 20) {
                        location.world.spawnParticle(Particle.PORTAL, location, timer * 10, 0.0, 0.0, 0.0, 2.0)
                    }
                    location.world.playSound(location, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, (timer / 20).toFloat(), (timer / 20).toFloat())
                }
                if ((timer >= 20) && (timer < 60)) {
                    // 2초 후에 폭발 시작
                    location.world.spawnParticle(Particle.FLASH, location, (timer / 3.0).toInt(), 0.1, 0.1, 0.1, 0.0)
                }
                if ((timer % 10 == 0) && (timer >= 60)) {
                    location.world.createExplosion(player, location, 4.0f, false, false, true)
                    location.world.spawnParticle(Particle.EXPLOSION, location, 30, 2.5, 2.5, 2.5, 2.0)
                    location.world.spawnParticle(Particle.FLASH, location, 10, 2.0, 2.0, 2.0,2.0)
                }
                if (timer++ >= 110) {
                    cancel() // 6번 폭발 후 작업 중지
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}