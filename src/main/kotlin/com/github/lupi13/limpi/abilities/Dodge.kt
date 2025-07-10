package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector

object Dodge : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("회피", NamedTextColor.AQUA),
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
            Component.text("쿨타임: ${config!!.getInt("cooldown") / 1000}초", NamedTextColor.WHITE),
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("cooldown", 5000)
            config?.set("velocity", 2.0)
            config?.set("allowJump", false)
        }
        saveConfig()
    }

    val velocityMap: MutableMap<Player, Vector> = mutableMapOf()
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from.toVector()
        val to: Vector = try {
            event.to.toVector()
        } catch (e: Exception) {
            from
        }
        velocityMap[player] = to.subtract(from)
    }

    /**
     * 이동중인 방향으로 수평 가속
     * @param player 회피를 시전할 플레이어
     * @param amplifier 회피 속도 (기본값: 3.0)
     */
    fun dodge(player: Player, amplifier: Double, allowJump: Boolean) {
        val velocity = velocityMap[player]!!
        val moveVector = try {
            if (velocity.length() >= 2) {
                velocity.multiply(amplifier)
            } else {
                velocity.normalize().multiply(amplifier)
            }
        } catch (e: Exception) {
            player.location.direction.setY(0.0).normalize().multiply(amplifier)
        }

        try {
            player.velocity = moveVector
        } catch (ignored: Exception) {
        }
        player.world.playSound(player.location, Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 1f)


        if (!allowJump) {
            player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH)!!.baseValue = 0.0
            // 5 ticks 후에 점프력 복원
            object: BukkitRunnable() {
                override fun run() {
                    player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH)!!.baseValue = player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH)!!.defaultValue
                }
            }.runTaskLater(plugin, 5L)
        }
    }


    val lastSneakTime: MutableMap<Player, Long> = mutableMapOf()
    val lastDodgeTime: MutableMap<Player, Long> = mutableMapOf()

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val cooldown = config!!.getInt("cooldown")
        if (player.ability != this) return
        if (!(player as Entity).isOnGround || !event.isSneaking) return

        if ((lastSneakTime[player] != null) && (System.currentTimeMillis() - lastSneakTime[player]!! <= 300)) { // 300ms 이내에 두 번 웅크리면
            if ((lastDodgeTime[player] == null) || (System.currentTimeMillis() - lastDodgeTime[player]!! > config!!.getInt("cooldown"))) { // 쿨타임이 지나면
                dodge(player, config!!.getDouble("velocity"), config!!.getBoolean("allowJump"))
                lastDodgeTime[player] = System.currentTimeMillis()
            }
            else {
                AbilityManager().showCooldown(player, cooldown, lastDodgeTime[player]!!)
            }
        }
        lastSneakTime[player] = System.currentTimeMillis()
    }
}