package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.PlayerDeathEvent

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


    private var exploding: java.util.UUID? = null
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun filterExplosion(event: org.bukkit.event.entity.EntityDamageEvent) {
        val owner = exploding ?: return
        val target = event.entity
        val player = plugin.server.getPlayer(owner)
        if (player == null || target !is org.bukkit.entity.LivingEntity || target.uniqueId == owner ||
            !com.github.lupi13.limpi.abilities.AbilityManager.isEnemy(player, target)) event.isCancelled = true
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun protectHanging(event: org.bukkit.event.hanging.HangingBreakEvent) { if (exploding != null) event.isCancelled = true }

    // 취소된 사망에는 폭발하지 않는다. 같은 MONITOR에서 먼저 등록된 런타임 사망 정리 뒤에 시작하므로 이 작업은 정리되지 않는다.
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        if (player.ability != this) return

        // 3초 후에 6번의 큰 폭발을 일으킴
        val location = player.location.clone().add(0.0, 0.5, 0.0)
        object: com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName, allowDead = true) {
            var timer = 0
            override fun tick() {
                if ((timer >= 0) && (timer < 40)) {
                    // 1초 후에 폭발 시작
                    if (timer <= 20) {
                        location.world.spawnParticle(Particle.PORTAL, location, timer * 10, 0.0, 0.0, 0.0, 2.0)
                    }
                    location.world.playSound(location, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, (timer / 20).toFloat(), (timer / 20).toFloat())
                }
                if ((timer >= 20) && (timer < 60)) {
                    // 2초 후에 폭발 시작
                    location.world.spawnParticle(Particle.FLASH, location, (timer / 3.0).toInt(), 0.1, 0.1, 0.1, 0.0, org.bukkit.Color.WHITE)
                }
                if ((timer % 10 == 0) && (timer >= 60)) {
                    // The owner is dead, so AbilityDamage.hit cannot be used. Filter the synchronous explosion instead:
                    // only enemies are hurt; drops (including the owner's death pile), frames and allies are protected.
                    exploding = player.uniqueId
                    try { location.world.createExplosion(player, location, 4.0f, false, false, true) } finally { exploding = null }
                    location.world.spawnParticle(Particle.EXPLOSION, location, 30, 2.5, 2.5, 2.5, 2.0)
                    location.world.spawnParticle(Particle.FLASH, location, 10, 2.0, 2.0, 2.0,2.0, org.bukkit.Color.WHITE)
                }
                if (timer++ >= 110) {
                    cancel() // 6번 폭발 후 작업 중지
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}
