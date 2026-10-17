package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.normalizeSafely

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

object BlunderBooster : Ability(
    grade = Grade.LEGENDARY,
    displayName = Component.text("블런더 부스터", NamedTextColor.DARK_RED),
    codeName = "blunder_booster",
    material = Material.STRUCTURE_BLOCK,
    description = listOf(
        Component.text("왼손 슬롯이 제한됩니다.", NamedTextColor.WHITE),
        Component.text("블런더 에너지를 통해 회피하고,", NamedTextColor.WHITE),
        Component.text("피해를 무시하고, 죽음을 견뎌냅니다.", NamedTextColor.WHITE)
    ),
    restrictedSlot = 40,
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("화상 피해에 면역이 됩니다."),
            Component.text("블런더 에너지", NamedTextColor.DARK_RED)
                .append(Component.text("가 1초에 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("energy_per_tick") * 20}", NamedTextColor.GREEN))
                .append(Component.text("씩, 최대 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("energy_max")}", NamedTextColor.GREEN))
                .append(Component.text("까지 회복됩니다.", NamedTextColor.WHITE)),
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 ", NamedTextColor.WHITE))
                .append(Component.text("블런더 에너지", NamedTextColor.DARK_RED))
                .append(Component.text("를 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("dodge_energy")}", NamedTextColor.GREEN))
                .append(Component.text(" 소모하고 뒷방향으로 회피합니다. 회피 경로에 불을 지핍니다.", NamedTextColor.WHITE)),
            Component.text("피해를 입을 때, ", NamedTextColor.WHITE)
                .append(Component.text("블런더 에너지", NamedTextColor.DARK_RED))
                .append(Component.text("를 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("ignore_damage_energy")}", NamedTextColor.GREEN))
                .append(Component.text(" 소모하고 해당 피해를 무효화합니다.", NamedTextColor.WHITE)),
            Component.text("죽음에 달하는 피해를 입을 때, ", NamedTextColor.WHITE)
                .append(Component.text("불사의 토템", NamedTextColor.YELLOW))
                .append(Component.text("과 같은 효과로 되살아나며 능력이 제거됩니다.", NamedTextColor.WHITE))
        )
    }


    fun getBlunderBoosterItem(): ItemStack {
        val item = ItemStack(Material.TOTEM_OF_UNDYING)
        val meta = item.itemMeta
        meta.itemModel = NamespacedKey("minecraft", "structure_block")
        meta.itemName(Component.text("블런더 부스터", NamedTextColor.DARK_RED))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 뒷방향으로 회피합니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
            Component.text("죽음에 달하는 피해를 견뎌냈을 때,", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
            Component.text("이 아이템과 능력은 제거됩니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta
        return item
    }


    override val activeItem: ItemStack by lazy {
        getBlunderBoosterItem()
    }


    /**
     * 뒷방향으로 수평 가속, 불 설치
     * @param player 회피를 시전할 플레이어
     */
    fun dodgeWithFire(player: Player) {
        val amplifier = config!!.getDouble("velocity")
        val yaw = player.location.yaw
        val moveVector = Vector(
            -sin(Math.toRadians(yaw.toDouble())),
            0.0,
            cos(Math.toRadians(yaw.toDouble()))
        ).normalizeSafely().multiply(-amplifier)

        player.velocity = moveVector
        player.world.playSound(player.location, Sound.ENTITY_BREEZE_SLIDE, 1f, 1.5f)

        object: com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
            var ticks = 0
            override fun tick() {
                val location = player.location

                com.github.lupi13.limpi.Functions.igniteBy(player, location.block)
                ticks++
                if (ticks > 5) {
                    cancel() // 5틱 후에 실행 중지
                    return
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    val energyMap = mutableMapOf<java.util.UUID, Int>()

    /**
     * 블런더 에너지를 회복하는 함수
     */
    fun energyRecovery(player: Player) {
        if (player.ability != this) return

        val currentEnergy = energyMap.getOrDefault(player.uniqueId, 0)
        if (currentEnergy < config!!.getInt("energy_max")) {
            val newEnergy = currentEnergy.toLong() + config!!.getInt("energy_per_tick")
            energyMap[player.uniqueId] = newEnergy.coerceAtMost(config!!.getInt("energy_max").toLong()).toInt()
        }

        // 블런더 에너지를 표시하는 액션바 메시지: 게이지 효과
        var message = Component.text("BLUNDER ENERGY: ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
        val energy = energyMap[player.uniqueId] ?: 0
        val maxEnergy = config!!.getInt("energy_max")
        val gauge = energy.toLong() * 30 / maxEnergy
        for (i in 0 until 30) {
            message = if (i < gauge) {
                message.append(Component.text("|", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, false))
            } else {
                message.append(Component.text("·", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
            }
        }
        message = message.append(Component.text(" (${energy}/${maxEnergy})", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, false))
        player.sendActionBar(message)
    }

    // 블런더 에너지 회복 및 회피 기능, 불피해 면역을 주기적으로 실행하는 스케줄러
    override fun start() {
        com.github.lupi13.limpi.platform.AbilityRuntime.global(1) {
                for (player in plugin.server.onlinePlayers) {
                    if (player.ability == this@BlunderBooster && !player.isDead && !com.github.lupi13.limpi.FileManager.delivery.blocked(player.uniqueId)) {
                        energyMap.putIfAbsent(player.uniqueId, 0)
                    }
                    else {
                        energyMap.remove(player.uniqueId)
                    }
                }
                for (owner in energyMap.keys.toList()) {
                    val player = plugin.server.getPlayer(owner)
                    if (player == null) { energyMap.remove(owner); continue }
                    energyRecovery(player)
                    player.fireTicks = 0
                }
        }
    }

    // 손 스왑 시 회피 기능 실행
    @EventHandler(ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this) return

        event.isCancelled = true

        if (energyMap.getOrDefault(player.uniqueId, 0) < config!!.getInt("dodge_energy")) return

        energyMap[player.uniqueId] = energyMap.getOrDefault(player.uniqueId, 0) - config!!.getInt("dodge_energy")
        dodgeWithFire(player)
    }

    // 플레이어가 피해를 입었을 때 블런더 에너지를 소모하고 피해를 무효화
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerDamaged(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (player.ability != this || event.cause == EntityDamageEvent.DamageCause.KILL) return // /kill은 막지 않습니다.

        // 화상 피해에 면역
        if (event.cause == EntityDamageEvent.DamageCause.FIRE || event.cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
            event.isCancelled = true
            player.fireTicks = 0 // 화상 피해를 무효화
            return
        }

        // 블런더 에너지가 충분하지 않으면 아무것도 하지 않음
        if (energyMap.getOrDefault(player.uniqueId, 0) < config!!.getInt("ignore_damage_energy")) return

        // 블런더 에너지를 소모하고 피해를 무효화
        energyMap[player.uniqueId] = energyMap.getOrDefault(player.uniqueId, 0) - config!!.getInt("ignore_damage_energy")
        event.damage = 0.0 // 피해를 무효화
        event.isCancelled = true

        player.world.playSound(player.location, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.7f, 2f)
        player.world.spawnParticle(
            Particle.CRIMSON_SPORE,
            player.boundingBox.center.toLocation(player.world),
            100, 0.2, 0.4, 0.2, 1.0)
    }

    // 플레이어가 죽을 때 블런더 부스터 효과를 제거하고 되살아남음
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onUseTotemOfUndying(event: EntityResurrectEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (player.ability != this) return
        if (event.hand != EquipmentSlot.OFF_HAND) return


        // 블런더 부스터 효과 제거
        // During EntityResurrectEvent the player is still dead (health 0) and the totem is not consumed yet, so the
        // inventory transition cannot be saved now. Remove the ability on the next tick, once the player is alive.
        energyMap.remove(player.uniqueId)
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline && !player.isDead && player.ability == this) AbilityManager.applyAbility(player, null)
        })
    }
    override fun resetCooldowns(owner: java.util.UUID) { energyMap.remove(owner) }
    override fun stop() { energyMap.clear() }

}
