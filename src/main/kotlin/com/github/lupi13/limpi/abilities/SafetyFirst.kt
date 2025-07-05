package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent

object SafetyFirst : Ability(
    grade = Grade.TROLL,
    element = Element.NONE,
    displayName = Component.text("안전제일", NamedTextColor.YELLOW),
    codeName = "safety_first",
    material = Material.GOLDEN_HELMET,
    description = listOf(
        Component.text("웅크렸을 때만 블럭을 설치/파괴할 수 있습니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
        Component.text("도대체 누가 이런걸 능력이라고 제안한거야?", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH).decoration(TextDecoration.ITALIC, false)
    )
) {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this) return
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            if(event.item != null && event.item!!.type.isBlock) {
                if (!(player.isSneaking)) {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler
    fun onPlayerBreakBlock(event: BlockBreakEvent) {
        val player = event.player
        if (player.ability != this) return
        if (!(player.isSneaking)) {
            event.isCancelled = true
        }
    }
}