package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.ability.RailgunSettings
import com.github.lupi13.limpi.quests.DoEndermansDreamOfFreeingEnd
import com.github.lupi13.limpi.quests.QuestManager
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Damageable
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Enderman
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.random.Random

/** Shared railgun mechanics; one-shot consumption is distinct from the lifetime of its owned effect. */
object RailgunEffect {
    fun start(player: Player, ability: Ability, settings: RailgunSettings, consumed: Boolean) {
        require(player.inventory.itemInOffHand.isSimilar(ability.activeItem)) { "레일건을 장착한 후 사용하세요." }
        val fixed = player.location.clone()
        val direction = fixed.direction.normalize()
        val world = player.world
        val horizontal = Vector(direction.x, 0.0, direction.z)
        val perpendicular = if (horizontal.lengthSquared() <= 0.0001) Vector(1.0, 0.0, 0.0) // main: unscaled when aiming straight up/down
            else horizontal.normalize().crossProduct(direction).normalize().multiply(settings.size / 2)
        val expected = if (consumed) "" else ability.codeName
        val effect = AbilityRuntime.begin(player, ability.codeName, expected)
        try {
            val rail = world.spawn(player.eyeLocation.add(direction.clone().multiply(3)).subtract(Vector(0.0, settings.size / 2, 0.0)),
                Interaction::class.java) { entity ->
                effect.ownEntity(entity)
                entity.setGravity(false); entity.isInvulnerable = true
                entity.interactionHeight = settings.size.toFloat(); entity.interactionWidth = settings.size.toFloat()
            }
            // A protection/entity-limit plugin can cancel the spawn; never consume the single-use ability without a shot.
            check(rail.isValid) { "Railgun entity spawn was rejected" }
            if (consumed) AbilityManager.applyAbility(player, null, effect.scope.id)
            player.setCooldown(ability.material, if (consumed) settings.delay + settings.lifetime + 1 else settings.delay + 1)
            val speed = effect.attribute(Attribute.MOVEMENT_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
            val gravity = effect.attribute(Attribute.GRAVITY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
            var ticks = 0
            var killedEnderman = false
            // Same jittered palette as the original railgun: red, yellow, green, cyan, blue, purple.
            val palettes: List<() -> Color> = listOf(
                { Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(50, 70), Random.nextInt(50, 70)) },
                { Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(225, 255), Random.nextInt(50, 70)) },
                { Color.fromRGB(Random.nextInt(50, 70), Random.nextInt(225, 255), Random.nextInt(50, 70)) },
                { Color.fromRGB(Random.nextInt(50, 70), Random.nextInt(225, 255), Random.nextInt(225, 255)) },
                { Color.fromRGB(Random.nextInt(50, 70), Random.nextInt(50, 70), Random.nextInt(225, 255)) },
                { Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(50, 70), Random.nextInt(225, 255)) },
            )
            effect.repeat { session ->
                if (!rail.isValid) { session.close(); return@repeat }
                if (ticks <= settings.delay) {
                    player.teleport(fixed)
                    if (ticks % 5 == 0) world.playSound(fixed, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f,
                        ticks / (settings.delay / 1.5f) + 0.5f)
                }
                perpendicular.rotateAroundAxis(direction, PI / 12)
                for ((index, palette) in palettes.withIndex()) {
                    val start = if (index == 0) 1 else (settings.delay * (index / 8.0)).toInt()
                    if (ticks < start) continue
                    // As in the original, each later ring rotates the shared perpendicular in place (cumulative spin).
                    if (index > 0) perpendicular.rotateAroundAxis(direction, PI / 3)
                    val location = rail.boundingBox.center.toLocation(world).add(perpendicular)
                    world.spawnParticle(Particle.DUST, location, (ticks - start).coerceAtMost(10), 0.2, 0.2, 0.2, 0.1,
                        Particle.DustOptions(palette(), 1f), true)
                }
                if (ticks == settings.delay) { speed.close(); gravity.close() }
                if (ticks >= settings.delay) {
                    val velocity = ((ticks - settings.delay) * settings.speed / settings.acceleration).coerceAtMost(settings.speed)
                    rail.teleport(rail.location.add(direction.clone().multiply(velocity)))
                    world.playSound(rail.location, Sound.ENTITY_ZOMBIE_CONVERTED_TO_DROWNED, 0.8f, 0.5f)
                    world.playSound(rail.location, Sound.ENTITY_HUSK_CONVERTED_TO_ZOMBIE, 0.8f, 0.5f)
                    val particles = ((ticks - settings.delay) / settings.acceleration).toInt()
                    world.spawnParticle(Particle.DUST, rail.boundingBox.center.toLocation(world), particles.coerceAtMost(50),
                        settings.size / 6, settings.size / 6, settings.size / 6, 1.0, Particle.DustOptions(Color.fromRGB(Random.nextInt(225, 255), Random.nextInt(225, 255), Random.nextInt(225, 255)), 4f), true)
                    world.spawnParticle(Particle.DUST, rail.boundingBox.center.toLocation(world), particles.coerceAtMost(20),
                        settings.size / 6, settings.size / 6, settings.size / 6, 1.0, Particle.DustOptions(Color.fromRGB(Random.nextInt(0, 30), Random.nextInt(0, 30), Random.nextInt(0, 30)), 2f), true)
                    for (entity in world.getNearbyEntities(rail.boundingBox.center.toLocation(world), settings.size * 2, settings.size * 2, settings.size * 2)) {
                        if (entity !is Damageable || entity == player || entity == rail || !entity.boundingBox.overlaps(rail.boundingBox)) continue
                        if (AbilityManager.isEnemy(player, entity)) {
                            val accepted = AbilityDamage.hit(player, entity, if (consumed) "over_balance" else "judgement_ray", settings.damage)
                            if (consumed && entity is Enderman && accepted) killedEnderman = true
                            if (consumed && entity is EnderDragon && entity.isDead && !killedEnderman)
                                QuestManager.clearQuests(player, DoEndermansDreamOfFreeingEnd)
                        } else if (!consumed && settings.heal > 0) entity.heal(settings.heal)
                    }
                }
                if (ticks >= settings.delay + settings.lifetime) session.close() else ticks++
            }
        } catch (failure: Exception) { effect.close(); throw failure }
    }
}
