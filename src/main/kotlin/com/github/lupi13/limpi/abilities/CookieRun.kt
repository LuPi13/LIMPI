package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object CookieRun : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("쿠키런", TextColor.color(127, 63, 63)),
    codeName = "cookie_run",
    material = Material.COOKIE,
    description = listOf(
        Component.text("쿠키를 먹으면 신속 효과를 얻습니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){

    override val details: List<Component> by lazy {
        listOf(
            Component.text("쿠키를 먹으면 신속 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("intensity") - 1}", NamedTextColor.GREEN)),
            Component.text(" 버프를 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("duration") / 20}", NamedTextColor.GREEN))
                .append(Component.text("초 동안 얻습니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("duration", 200)
            config?.set("intensity", 1)
        }
        saveConfig()
    }


    @EventHandler
    fun onConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (player.ability != this) return
        if (event.item.type != Material.COOKIE) return

        val duration = config!!.getInt("duration")
        val intensity = config!!.getInt("intensity") - 1 // PotionEffectType intensity starts from 0
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, duration, intensity))
    }
}