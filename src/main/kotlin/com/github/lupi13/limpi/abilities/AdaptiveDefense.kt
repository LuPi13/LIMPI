package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.math.pow

object AdaptiveDefense : Ability(
    grade = Grade.RARE,
    element = Element.DEFENSIVE,
    displayName = Component.text("적응형 피해 감쇠기", NamedTextColor.YELLOW),
    codeName = "adaptive_defense",
    material = Material.GOLDEN_CHESTPLATE,
    description = listOf(
        Component.text("같은 유형의 피해를 연속해서 받으면", NamedTextColor.WHITE),
        Component.text("피해가 점점 감소합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){

    override val details: List<Component> by lazy {
        listOf(
            Component.text("같은 유형의 피해를 연속해서 받을 때마다", NamedTextColor.WHITE),
            Component.text("피해량이 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("damage_reduction") * 100}%", NamedTextColor.GREEN))
                .append(Component.text("로 곱연산 감소합니다.", NamedTextColor.WHITE)),
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("damage_reduction", 0.8)
        }
        saveConfig()
    }


    val damageHistory = mutableMapOf<Player, EntityDamageEvent.DamageCause>()
    val damageStack = mutableMapOf<Player, Int>()
    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        if (event.cause == EntityDamageEvent.DamageCause.VOID) return // Void damage는 제외

        val player = event.entity as Player
        if (player.ability != this) return

        if (player.noDamageTicks > 10) return

        if (!damageHistory.containsKey(player) || damageHistory[player] != event.cause) {
            damageHistory[player] = event.cause
            damageStack[player] = 0
        }
        else {
            damageStack[player] = (damageStack[player] ?: 0) + 1
            val reduction = config!!.getDouble("damage_reduction")
            if (damageStack[player]!! > 0) {
                val factor = reduction.pow(damageStack[player]!!)
                event.damage *= factor
                player.sendActionBar(Component.text("피해 감쇠: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.1f", factor * 100) + "%", NamedTextColor.GREEN)))
            }
        }
    }
}