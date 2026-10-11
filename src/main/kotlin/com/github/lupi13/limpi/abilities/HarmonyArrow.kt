package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.quests.LineBetweenSwordAndArrow
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent

object HarmonyArrow : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("조화: 화살", NamedTextColor.BLUE),
    codeName = "harmony_arrow",
    material = Material.ARCHER_POTTERY_SHERD,
    description = listOf(
        Component.text("화살로 피해를 누적시키면", NamedTextColor.WHITE),
        Component.text("다음 검격이 강화됩니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    relatedQuest = listOf(LineBetweenSwordAndArrow)
) {

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("화살", NamedTextColor.YELLOW)
                .append(Component.text("로 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("required_damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 누적하면, 다음에 가하는 ", NamedTextColor.WHITE))
                .append(Component.text("검", NamedTextColor.YELLOW))
                .append(Component.text("격이 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage_amplifier")}", NamedTextColor.GREEN))
                .append(Component.text("배의 피해를 입힙니다.", NamedTextColor.WHITE))
        )
    }


    var amplified: MutableMap<java.util.UUID, Double> = mutableMapOf()


    private val reserved = mutableSetOf<java.util.UUID>()
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (com.github.lupi13.limpi.combat.DamageContext.current != null) return
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (player.ability != this || !AbilityManager.isEnemy(player, target)) return
        val charge = amplified[player.uniqueId] ?: 0.0
        if (charge < config!!.getDouble("required_damage") || !reserved.add(player.uniqueId)) return
        confirmedHits[event] = player.uniqueId
        event.damage *= config!!.getDouble("damage_amplifier")
    }

    private val confirmedHits = java.util.WeakHashMap<org.bukkit.event.entity.EntityDamageByEntityEvent, java.util.UUID>()
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    fun recordConfirmedHit(event: EntityDamageByEntityEvent) {
        val boosted = confirmedHits.remove(event)
        if (boosted != null) reserved.remove(boosted)
        if (event.isCancelled || event.finalDamage <= 0 || com.github.lupi13.limpi.combat.DamageContext.current != null) return
        if (boosted != null) {
            val player = plugin.server.getPlayer(boosted) ?: return
            if (player.ability != this) return
            amplified[boosted] = com.github.lupi13.limpi.combat.ChargeGauge.consume(amplified[boosted] ?: 0.0, true)
            player.playSound(player.location, Sound.ENTITY_WARDEN_DEATH, 0.7f, 2.0f)
            event.entity.world.spawnParticle(Particle.DRAGON_BREATH, event.entity.boundingBox.center.toLocation(event.entity.world), 30, 0.1, 0.1, 0.1, 0.2, 1.0f)
            QuestManager.harmonyHit(player, "harmony_arrow")
            return
        }
        val player = (event.damager as? AbstractArrow)?.shooter as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (player.ability != this || !AbilityManager.isEnemy(player, target)) return
        val required = config!!.getDouble("required_damage")
        val previous = amplified[player.uniqueId] ?: 0.0
        val next = com.github.lupi13.limpi.combat.ChargeGauge.gain(previous, event.finalDamage, required)
        amplified[player.uniqueId] = next
        if (previous >= required) return
        if (next >= required) {
            player.sendActionBar(Component.text("HARMONY: 검 강화됨.", NamedTextColor.BLUE, TextDecoration.BOLD))
            player.playSound(player.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 2.0f)
        } else {
            val filled = (next / required * 40.0).toInt().coerceIn(0, 40)
            val empty = (40 - next / required * 40.0).toInt().coerceIn(0, 40)
            player.sendActionBar(Component.text("HARMONY ", NamedTextColor.BLUE, TextDecoration.BOLD)
                .append(Component.text("|".repeat(filled), NamedTextColor.BLUE).decoration(TextDecoration.BOLD, false))
                .append(Component.text("·".repeat(empty), NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)))
        }
    }

    override fun release(owner: java.util.UUID) { amplified.remove(owner); reserved.remove(owner); confirmedHits.entries.removeIf { it.value == owner } }
    override fun stop() { amplified.clear(); reserved.clear(); confirmedHits.clear() }

}
