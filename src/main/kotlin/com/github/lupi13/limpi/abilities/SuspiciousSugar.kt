package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

object SuspiciousSugar : Ability(
    grade = Grade.COMMON,
    element = Element.MAGIC,
    displayName = Component.text("정체불명의 흰 가루", NamedTextColor.WHITE),
    codeName = "suspicious_sugar",
    material = Material.SUGAR,
    description = listOf(
        Component.text("설탕을 우클릭하면 무작위 상태 효과를 얻습니다.", NamedTextColor.WHITE),
        Component.text("확률적으로 해당 상태 효과를", NamedTextColor.WHITE),
        Component.text("주변 엔티티에게도 적용시킵니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("설탕을 우클릭하면 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("duration") / 20}", NamedTextColor.GREEN))
                .append(Component.text("초의 무작위 상태 효과를 얻습니다.", NamedTextColor.WHITE)),
            Component.text("${config!!.getDouble("areaChance") * 100}%", NamedTextColor.GREEN)
                .append(Component.text(" 확률로 해당 상태 효과를 주변 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("range") / 2.0}", NamedTextColor.GREEN))
                .append(Component.text("블록 내의", NamedTextColor.WHITE)),
            Component.text("모든 엔티티에게도 적용시킵니다.", NamedTextColor.WHITE),
            Component.text("분명히 사탕수수로 만든 정직한 설탕일텐데...", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH)
        )
    }


    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    fun onClick(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || !event.action.isRightClick || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND && event.hand != org.bukkit.inventory.EquipmentSlot.OFF_HAND) return
        if (config!!.getInt("duration") == 0 || com.github.lupi13.limpi.FileManager.delivery.blocked(player.uniqueId)) return
        val observed = event.item?.takeIf { it.type == Material.SUGAR && it.amount > 0 } ?: return
        val original = com.github.lupi13.limpi.platform.PaperItemCodec.payload(observed)
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        if (!com.github.lupi13.limpi.FileManager.delivery.claim(player)) return
        val before = com.github.lupi13.limpi.platform.PaperItemCodec.snapshot(player, true)
        val slot = if (event.hand == org.bukkit.inventory.EquipmentSlot.HAND) player.inventory.heldItemSlot else 36
        val source = before[slot] ?: return
        if (player.ability != this || source.payload != original) return
        val randomEffect = Registry.POTION_EFFECT_TYPE.toList().randomOrNull() ?: return
        val duration = if (randomEffect.isInstant) 1 else config!!.getInt("duration")
        val range = config!!.getDouble("range")
        val targets = if (Random.nextDouble() < config!!.getDouble("areaChance")) {
            player.getNearbyEntities(range, range, range).filterIsInstance<LivingEntity>()
                .filter { affected(player, it, range) }.take(256).map { it.uniqueId }
        } else emptyList()
        val terms = com.github.lupi13.limpi.ability.SugarTerms(slot, source.payload, randomEffect.key.toString(),
            duration, player.world.uid, targets, range)
        val after = before.toMutableList().apply { this[slot] = if (source.count == 1) null else source.copy(count = source.count - 1) }
        if (com.github.lupi13.limpi.FileManager.delivery.remove(player, java.util.UUID.randomUUID(),
                com.github.lupi13.limpi.ability.SugarSettlement.KIND, terms.encode(), before, after)) recover(player)
    }

    private fun affected(player: org.bukkit.entity.Player, entity: LivingEntity, range: Double): Boolean {
        if (entity == player || !entity.isValid || entity.isDead || entity.world != player.world || !AbilityManager.isEnemy(player, entity)) return false
        if (entity.location.distanceSquared(player.location) > range * range) return false
        if (entity is org.bukkit.entity.Player && (com.github.lupi13.limpi.FileManager.delivery.blocked(entity.uniqueId) ||
            entity.gameMode == org.bukkit.GameMode.CREATIVE || entity.gameMode == org.bukkit.GameMode.SPECTATOR ||
            entity.world.getGameRuleValue(Registry.GAME_RULE.getOrThrow(io.papermc.paper.registry.keys.GameRuleKeys.PVP)) != true)) return false
        return true
    }

    fun recover(player: org.bukkit.entity.Player) {
        if (!player.isOnline || player.isDead || com.github.lupi13.limpi.FileManager.delivery.blocked(player.uniqueId)) return
        val pending = try { com.github.lupi13.limpi.FileManager.sugar.pending(player.uniqueId) } catch (failure: Exception) {
            // Leave the record for /financial sugar; an unreadable result must not kick the player on every login.
            plugin.logger.log(java.util.logging.Level.SEVERE, "Unreadable sugar pending results for ${player.uniqueId}; use /financial sugar", failure)
            return
        }
        for ((id, frozen) in pending) {
            val type = org.bukkit.NamespacedKey.fromString(frozen.effect)?.let(Registry.POTION_EFFECT_TYPE::get)
            if (type == null) {
                plugin.logger.severe("Sugar result $id for ${player.uniqueId} names unknown effect ${frozen.effect}; kept for /financial sugar void")
                continue
            }
            val terms = com.github.lupi13.limpi.FileManager.sugar.activate(player.uniqueId, id) ?: continue
            try {
                player.addPotionEffect(PotionEffect(type, terms.duration, 0))
                if (player.world.uid == terms.world) for (target in terms.targets) {
                    val entity = player.world.getEntity(target) as? LivingEntity ?: continue
                    if (affected(player, entity, terms.range)) entity.addPotionEffect(PotionEffect(type, terms.duration, 0))
                }
                player.world.spawnParticle(Particle.SPIT, player.eyeLocation, 30, 0.0, 0.0, 0.0, 1.0)
                player.world.playSound(player.location, Sound.ENTITY_WITHER_SHOOT, 0.4f, 2f)
            } catch (failure: Exception) {
                // Never replay instant damage/healing after a partly successful live application.
                plugin.logger.log(java.util.logging.Level.WARNING, "Sugar activation $id for ${player.uniqueId} failed; not replayed", failure)
            }
        }
    }
}
