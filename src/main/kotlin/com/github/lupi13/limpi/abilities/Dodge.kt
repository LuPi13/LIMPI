package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.util.Vector

object Dodge : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("회피", NamedTextColor.GRAY),
    codeName = "dodge",
    material = Material.RABBIT_FOOT,
    description = listOf(
        Component.text("지상에서 빠르게 두 번 웅크려", NamedTextColor.WHITE),
        Component.text("이동 방향으로 회피합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
//    relatedQuest =
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("지상에서 빠르게 두 번 웅크려", NamedTextColor.WHITE),
            Component.text("이동 방향으로 회피합니다.", NamedTextColor.WHITE),
            Component.text("쿨타임: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown") / 1000.0}초", NamedTextColor.WHITE))
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
                dodge(player, config!!.getDouble("velocity"), config!!.getBoolean("allowJump"))
                lastDodgeTime[player.uniqueId] = System.currentTimeMillis()
            }
            else {
                AbilityManager.showCooldown(player, cooldown, lastDodgeTime[player.uniqueId]!!)
            }
        }
        lastSneakTime[player.uniqueId] = System.currentTimeMillis()
    }
    override fun release(owner: java.util.UUID) { velocityMap.remove(owner); lastSneakTime.remove(owner); lastDodgeTime.remove(owner) }
    override fun stop() { velocityMap.clear(); lastSneakTime.clear(); lastDodgeTime.clear() }

}
