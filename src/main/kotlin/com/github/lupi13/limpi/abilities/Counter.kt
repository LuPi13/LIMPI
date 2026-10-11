package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityDamage
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID

object Counter : Ability(
    grade = Grade.RARE,
    element = Element.DEFENSIVE,
    displayName = Component.text("카운터", NamedTextColor.AQUA),
    codeName = "counter",
    material = Material.DIAMOND_SWORD,
    description = listOf(
        Component.text("검을 들고 우클릭하면 반격 태세에 들어갑니다.", NamedTextColor.WHITE),
        Component.text("직후 피해를 입으면 피해를 무시하고", NamedTextColor.WHITE),
        Component.text("전방에 강하게 반격합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("검을 들고 우클릭하면 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("stance_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초간 반격 태세에 들어가며,", NamedTextColor.WHITE)),
            Component.text("그동안 거의 움직일 수 없습니다.", NamedTextColor.WHITE),
            Component.text("시전 후 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("window_ms") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초 안에 받는 피해를 모두 무시하고,", NamedTextColor.WHITE)),
            Component.text("전방의 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 받은 피해의 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage_multiplier")}", NamedTextColor.GREEN))
                .append(Component.text("배로 한 번 반격합니다.", NamedTextColor.WHITE)),
            Component.text("쿨타임: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초 (모든 검 공통)", NamedTextColor.WHITE))
        )
    }

    private val swords = listOf(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.COPPER_SWORD, Material.GOLDEN_SWORD,
        Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD)

    /** 반격으로 막지 않는 피해(즉사/월드 경계 등). */
    private val unblockable = setOf(DamageCause.VOID, DamageCause.KILL, DamageCause.WORLD_BORDER, DamageCause.SUICIDE)

    private class Stance(val effect: AbilityEffect, val castAt: Long) { var countered = false }
    private val stances: MutableMap<UUID, Stance> = mutableMapOf()

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || !event.action.isRightClick || event.useItemInHand() == Event.Result.DENY) return
        val item = event.item ?: return
        if (item.type !in swords || FileManager.delivery.blocked(player.uniqueId) || player.isDead) return
        if (player.getCooldown(item.type) > 0) {
            AbilityManager.showCooldown(player, item.type)
            return
        }
        val cooldown = config!!.getInt("cooldown_ticks")
        val stanceTicks = config!!.getInt("stance_ticks")
        swords.forEach { player.setCooldown(it, cooldown) }

        val id = player.uniqueId
        stances.remove(id)?.effect?.close()
        val effect = AbilityRuntime.begin(player, codeName)
        val stance = Stance(effect, System.currentTimeMillis())
        stances[id] = stance
        effect.own { if (stances[id] === stance) stances.remove(id) }
        effect.potion(player, PotionEffect(PotionEffectType.SLOWNESS, stanceTicks, config!!.getInt("slowness_level") - 1, true, false, false))
        effect.repeat(delay = stanceTicks.toLong()) { it.close() }
        player.world.playSound(player.location, Sound.ENTITY_ZOMBIE_CONVERTED_TO_DROWNED, SoundCategory.PLAYERS, 0.7f, 2f)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val stance = stances[player.uniqueId] ?: return
        if (player.ability != this || event.cause in unblockable) return
        if (System.currentTimeMillis() - stance.castAt > config!!.getInt("window_ms")) return
        event.isCancelled = true
        if (stance.countered) return
        stance.countered = true

        val damage = event.damage * config!!.getDouble("damage_multiplier")
        if (!damage.isFinite() || damage < 0) return
        // 반격 범위: 눈앞 2블록, 폭 3블록, 높이 1블록 (원본의 Interaction 판정과 같음)
        val center = player.eyeLocation.add(player.location.direction.multiply(2.0)).subtract(0.0, 0.5, 0.0)
        val box = BoundingBox(center.x - 1.5, center.y, center.z - 1.5, center.x + 1.5, center.y + 1.0, center.z + 1.5)
        player.world.playSound(player.location, Sound.ITEM_TRIDENT_HIT_GROUND, SoundCategory.PLAYERS, 1f, 2f)

        stance.effect.repeat(delay = config!!.getInt("counter_delay_ticks").toLong()) { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            val push = owner.location.direction.setY(0)
            val knockback = if (push.lengthSquared() > 1e-6) push.normalize().multiply(0.5).setY(0.1) else Vector(0.0, 0.1, 0.0)
            for (entity in owner.world.getNearbyEntities(box)) {
                if (entity !is LivingEntity || entity == owner) continue
                if (!AbilityDamage.hit(owner, entity, codeName, damage)) continue
                if (entity.isValid && !entity.isDead) entity.velocity = knockback
                if (!owner.isOnline || owner.isDead) break
            }
            owner.world.spawnParticle(Particle.SWEEP_ATTACK, owner.eyeLocation.add(owner.location.direction.multiply(1.0)), 1, 0.0, 0.0, 0.0)
            owner.world.playSound(owner.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1f, 1f)
            scope.close()
        }
    }

    override fun release(owner: UUID) {
        stances.remove(owner)?.effect?.close()
    }

    override fun stop() {
        stances.values.toList().forEach { it.effect.close() }
        stances.clear()
    }
}
