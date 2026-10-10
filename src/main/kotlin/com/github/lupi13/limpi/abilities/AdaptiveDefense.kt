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


    val damageHistory = mutableMapOf<java.util.UUID, EntityDamageEvent.DamageCause>()
    val damageStack = mutableMapOf<java.util.UUID, Int>()
    private val pending = java.util.WeakHashMap<EntityDamageEvent, Pair<java.util.UUID, Int>>()
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability != this || event.cause == EntityDamageEvent.DamageCause.VOID || player.noDamageTicks > 10) return
        val count = if (damageHistory[player.uniqueId] == event.cause) ((damageStack[player.uniqueId] ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        val reduction = config!!.getDouble("damage_reduction")
        require(reduction in 0.0..1.0)
        event.damage *= reduction.pow(count)
        pending[event] = player.uniqueId to count
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    fun confirmed(event: EntityDamageEvent) {
        val (owner, count) = pending.remove(event) ?: return
        if (event.isCancelled || event.finalDamage <= 0) return
        val player = plugin.server.getPlayer(owner) ?: return
        if (player.ability != this) return
        damageHistory[owner] = event.cause
        damageStack[owner] = count
        if (count > 0) player.sendActionBar(Component.text("피해 감쇠: ", NamedTextColor.YELLOW)
            .append(Component.text(String.format("%.1f", config!!.getDouble("damage_reduction").pow(count) * 100) + "%", NamedTextColor.GREEN)))
    }
    override fun release(owner: java.util.UUID) { damageHistory.remove(owner); damageStack.remove(owner); pending.entries.removeIf { it.value.first == owner } }
    override fun stop() { damageHistory.clear(); damageStack.clear(); pending.clear() }

}
