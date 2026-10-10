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
import org.bukkit.event.entity.ProjectileHitEvent

object HarmonySword : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("조화: 검", NamedTextColor.RED),
    codeName = "harmony_sword",
    material = Material.BLADE_POTTERY_SHERD,
    description = listOf(
        Component.text("검으로 피해를 누적시키면", NamedTextColor.WHITE),
        Component.text("다음 화살이 강화됩니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    relatedQuest = listOf(LineBetweenSwordAndArrow)
) {

    override val details: List<Component> by lazy {
        listOf(
            Component.text("검으로 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("required_damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 누적하면, 다음에 발사하는 화살이 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage_amplifier")}", NamedTextColor.GREEN))
                .append(Component.text("배의 피해를 입힙니다.", NamedTextColor.WHITE))
        )
    }


    val sword = listOf(
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.IRON_SWORD,
        Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD
    )
    var amplified: MutableMap<java.util.UUID, Double> = mutableMapOf()


    private val reserved = mutableSetOf<java.util.UUID>()
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (com.github.lupi13.limpi.combat.DamageContext.current != null) return
        val arrow = event.damager as? AbstractArrow ?: return
        val player = arrow.shooter as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (player.ability != this || !AbilityManager.isEnemy(player, target)) return
        val charge = amplified[player.uniqueId] ?: 0.0
        if (charge < config!!.getDouble("required_damage") || !reserved.add(player.uniqueId)) return
        confirmedHits[event] = player.uniqueId
        event.damage *= config!!.getDouble("damage_amplifier")
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onArrowHitBlock(event: ProjectileHitEvent) {
        if (event.entity !is AbstractArrow) return
        val arrow = event.entity as AbstractArrow
        if (arrow.shooter !is Player) return

        val player = arrow.shooter as Player
        if (player.ability != this || !amplified.contains(player.uniqueId) || amplified[player.uniqueId]!! < config!!.getDouble("required_damage")) return

        if ((event.hitBlock != null) && (event.hitEntity == null)) {
            player.world.playSound(
                arrow.location,
                Sound.ENTITY_WARDEN_DEATH,
                0.7f, 2.0f
            )
            arrow.world.spawnParticle(
                Particle.DRAGON_BREATH,
                arrow.location,
                30, 0.1, 0.1, 0.1, 0.2
            )
            amplified[player.uniqueId] = 0.0 // 강화된 화살 사용 후 제거
        }
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
            event.entity.world.spawnParticle(Particle.DRAGON_BREATH, event.entity.boundingBox.center.toLocation(event.entity.world), 30, 0.1, 0.1, 0.1, 0.2)
            QuestManager.harmonyHit(player, "harmony_sword")
            return
        }
        val player = event.damager as? Player ?: return
        if (player.inventory.itemInMainHand.type !in sword) return
        val target = event.entity as? LivingEntity ?: return
        if (player.ability != this || !AbilityManager.isEnemy(player, target)) return
        val required = config!!.getDouble("required_damage")
        val previous = amplified[player.uniqueId] ?: 0.0
        val next = com.github.lupi13.limpi.combat.ChargeGauge.gain(previous, event.finalDamage, required)
        amplified[player.uniqueId] = next
        if (previous >= required) return
        if (next >= required) {
            player.sendActionBar(Component.text("HARMONY: 화살 강화됨.", NamedTextColor.RED, TextDecoration.BOLD))
            player.playSound(player.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 2.0f)
        } else {
            val filled = (next / required * 40).toInt().coerceIn(0, 40)
            player.sendActionBar(Component.text("HARMONY ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("|".repeat(filled), NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                .append(Component.text("·".repeat(40 - filled), NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)))
        }
    }

    override fun release(owner: java.util.UUID) { amplified.remove(owner); reserved.remove(owner); confirmedHits.entries.removeIf { it.value == owner } }
    override fun stop() { amplified.clear(); reserved.clear(); confirmedHits.clear() }

}
