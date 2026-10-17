package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely
import com.github.lupi13.limpi.quests.ColdDodge
import com.github.lupi13.limpi.quests.DodgeCliche
import com.github.lupi13.limpi.quests.DodgeMaster
import com.github.lupi13.limpi.quests.EnoughDodge
import com.github.lupi13.limpi.quests.QuestConditions
import com.github.lupi13.limpi.quests.QuestManager
import com.github.lupi13.limpi.quests.ShouldDodge
import com.github.lupi13.limpi.quests.YourDodge

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

object Dodge : Ability(
    grade = Grade.COMMON,
    displayName = Component.text("회피", NamedTextColor.GRAY),
    codeName = "dodge",
    material = Material.RABBIT_FOOT,
    description = listOf(
        Component.text("지상에서 빠르게 두 번 웅크려", NamedTextColor.WHITE),
        Component.text("이동 방향으로 회피합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val relatedQuest: List<com.github.lupi13.limpi.quests.Quest> by lazy {
        listOf(DodgeMaster, YourDodge, EnoughDodge, ShouldDodge, DodgeCliche, ColdDodge)
    }

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("지상에서 빠르게 두 번 웅크려", NamedTextColor.WHITE),
            Component.text("이동 방향으로 회피합니다.", NamedTextColor.WHITE),
            Component.text("쿨타임: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
        )
    }


    val velocityMap: MutableMap<java.util.UUID, Vector> = mutableMapOf()
    @EventHandler(ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.ability != this) return
        val from = event.from.toVector()
        val to: Vector = try {
            event.to.toVector()
        } catch (e: Exception) {
            from
        }
        velocityMap[player.uniqueId] = to.subtract(from)
    }

    /**
     * 이동중인 방향으로 수평 가속
     * @param player 회피를 시전할 플레이어
     * @param amplifier 회피 속도 (기본값: 3.0)
     */
    fun dodge(player: Player, amplifier: Double, allowJump: Boolean) {
        require(amplifier.isFinite() && amplifier >= 0)
        val velocity = velocityMap[player.uniqueId]?.clone() ?: Vector()
        val moveVector = if (velocity.lengthSquared() > 0.000001) {
            if (velocity.length() >= 2) velocity.multiply(amplifier) else velocity.normalizeSafely().multiply(amplifier)
        } else {
            val yaw = Math.toRadians(player.yaw.toDouble())
            Vector(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw)).multiply(amplifier)
        }

        try {
            player.velocity = moveVector
        } catch (ignored: Exception) {
        }
        player.world.playSound(player.location, Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 1f)


        if (!allowJump) {
            val effect = com.github.lupi13.limpi.platform.AbilityRuntime.begin(player, codeName)
            effect.attribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH, -1.0, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1)
            effect.repeat(delay = 5) { it.close() }
        }
    }


    val lastSneakTime: MutableMap<java.util.UUID, Long> = mutableMapOf()
    val lastDodgeTime: MutableMap<java.util.UUID, Long> = mutableMapOf()

    @EventHandler(ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val cooldown = config!!.getInt("cooldown")
        if (player.ability != this) return
        if (!(player as Entity).isOnGround || !event.isSneaking) return

        if ((lastSneakTime[player.uniqueId] != null) && (System.currentTimeMillis() - lastSneakTime[player.uniqueId]!! <= 300)) { // 300ms 이내에 두 번 웅크리면
            if ((lastDodgeTime[player.uniqueId] == null) || (System.currentTimeMillis() - lastDodgeTime[player.uniqueId]!! > config!!.getInt("cooldown"))) { // 쿨타임이 지나면
                val horizontalSpeed = velocityMap[player.uniqueId]?.let { kotlin.math.hypot(it.x, it.z) } ?: 0.0
                dodge(player, config!!.getDouble("velocity"), config!!.getBoolean("allowJump"))
                lastDodgeTime[player.uniqueId] = System.currentTimeMillis()
                checkDodgeQuests(player, horizontalSpeed)
            }
            else {
                AbilityManager.showCooldown(player, cooldown, lastDodgeTime[player.uniqueId]!!)
            }
        }
        lastSneakTime[player.uniqueId] = System.currentTimeMillis()
    }
    /** 회피 직후 확인하는 퀘스트: 회피의 달인, 바람처럼 차갑게, 너의 회피는, 굳이 회피해야해? */
    private fun checkDodgeQuests(player: Player, horizontalSpeed: Double) {
        QuestManager.progress(player, DodgeMaster)
        if (QuestConditions.coldDodge(horizontalSpeed, player.getPotionEffect(PotionEffectType.SPEED)?.amplifier)) QuestManager.clearQuests(player, ColdDodge)
        if (player.getNearbyEntities(5.0, 5.0, 5.0).count { it.type == EntityType.ENDERMAN } >= 5) QuestManager.clearQuests(player, YourDodge)
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) QuestManager.clearQuests(player, ShouldDodge)
    }

    /** 이거면 충분해: 회피 후 0.3초 내 폭발 피해를 받고 살아남기. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onExplosionDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (event.cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && event.cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return
        if (player.ability != this) return
        val last = lastDodgeTime[player.uniqueId] ?: return
        if (System.currentTimeMillis() - last > 300) return
        if (event.finalDamage >= player.health) return
        QuestManager.clearQuests(player, EnoughDodge)
    }

    /** 전형적인 회피 클리셰: 회피 후 0.5초 내 근접 공격 한 번으로 적 처치. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFinishingBlow(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (player.ability != this) return
        val last = lastDodgeTime[player.uniqueId] ?: return
        if (System.currentTimeMillis() - last > 500) return
        if (event.finalDamage < target.health || !AbilityManager.isEnemy(player, target)) return
        QuestManager.clearQuests(player, DodgeCliche)
    }

    override fun release(owner: java.util.UUID) { velocityMap.remove(owner); lastSneakTime.remove(owner) }
    override fun resetCooldowns(owner: java.util.UUID) { lastDodgeTime.remove(owner) }
    override fun stop() { velocityMap.clear(); lastSneakTime.clear(); lastDodgeTime.clear() }

}
