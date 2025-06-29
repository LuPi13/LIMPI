package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.scheduler.BukkitRunnable

object LetsBoom : Ability(
    grade = Grade.TROLL,
    displayName = Component.text("함께 폭⭐사하자"),
    codeName = "lets_boom",
    material = Material.TNT,
    description = listOf(
        Component.text("죽으면 1초 후 6번의 큰 폭발을 일으킵니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
        Component.text("지형을 파괴하지는 않지만, 주변의 아이템을 모두 없애버립니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)),
    restrictedSlot = null,
    attribute = Attribute.EXPLOSIVE
) {

    /**
     * 지정된 위치에서 플레이어가 폭발을 일으키도록 지연 실행하는 함수
     * @param location 폭발이 일어날 위치
     * @param player 폭발을 일으킬 플레이어
     * @param delay 폭발까지의 지연 시간 (틱 단위)
     */
    fun createDelayedExplosion(location: Location, player: Player, delay: Long) {
        object: BukkitRunnable() {
            override fun run() {
                location.world.createExplosion(player, location, 5f, false, false, true)
            }
        }.runTaskLater(plugin, delay)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        if (player.ability != this) return

        // 1초 후에 6번의 큰 폭발을 일으킴
        val location = player.location
        for (i in 20..70 step(10)) {
            createDelayedExplosion(location, player, i.toLong())
        }
    }
}