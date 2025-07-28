package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.ExpAbsorber
import com.github.lupi13.limpi.quests.UndeadCooperation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.roundToInt
import kotlin.random.Random

object ZombieKing : Ability(
    grade = Grade.EPIC,
    element = Element.UNDEAD,
    displayName = Component.text("좀비", NamedTextColor.DARK_GREEN)
        .append(Component.text("킹", NamedTextColor.GOLD)),
    codeName = "zombie_king",
    material = Material.ZOMBIE_SPAWN_EGG,
    description = listOf(
        Component.text("언데드 몹이 나를 적대하지 않습니다.", NamedTextColor.WHITE),
        Component.text("낮 시간에 불에 탈 수 있는 대신,", NamedTextColor.WHITE),
        Component.text("여러가지 강력한 효과를 얻습니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(UndeadCooperation.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("언데드 몹이 나를 적대하지 않습니다.", NamedTextColor.WHITE),
            Component.text("낮 시간에 모자 슬롯에 장착된 아이템이 없고 머리 위로 블럭이 없다면 불에 탑니다.", NamedTextColor.WHITE),
            Component.text("침대에서 잠을 잘 수 없으며 오직 스폰 포인트 역할만을 합니다.", NamedTextColor.WHITE),
            Component.text("즉시 회복", NamedTextColor.DARK_BLUE)
                .append(Component.text(" 효과와 ", NamedTextColor.WHITE))
                .append(Component.text("즉시 피해", NamedTextColor.DARK_BLUE))
                .append(Component.text(" 효과가 서로 바뀌어 적용됩니다.", NamedTextColor.WHITE)),
            Component.text("방어력이 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getInt("armor_point"), NamedTextColor.GREEN))
                .append(Component.text("만큼 추가됩니다.", NamedTextColor.WHITE)),
            Component.text("모든 근접 공격이 ", NamedTextColor.WHITE)
                .append(Component.text(config!!.getDouble("damage_addition"), NamedTextColor.GREEN))
                .append(Component.text("의 고정피해를 추가로 입힙니다.", NamedTextColor.WHITE)),
            Component.text("적", NamedTextColor.RED)
                .append(Component.text("을 공격할 때마다 주변 ", NamedTextColor.WHITE))
                .append(Component.text(config!!.getDouble("bullying_range"), NamedTextColor.GREEN))
                .append(Component.text("블럭 내의 모든 언데드 몹이 해당 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("을 공격 대상으로 삼습니다.", NamedTextColor.WHITE)),
            Component.text("적", NamedTextColor.RED)
                .append(Component.text("에게 피해를 입을 때, ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("reinforcement_probability") * 100}%", NamedTextColor.GREEN))
                .append(Component.text("의 확률로 주변에 해당 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("을 공격 대상으로 삼는 좀비를 소환합니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("armor_point", 2)
            config?.set("damage_addition", 3.0)
            config?.set("bullying_range", 32.0)
            config?.set("reinforcement_probability", 0.3)
        }
        saveConfig()
    }

    // 낮에 불타는 메서드
    init {
        object: BukkitRunnable() {
            override fun run() {
                for (player in plugin.server.onlinePlayers) {
                    if (player.ability != this@ZombieKing) continue
                    player.getAttribute(Attribute.ARMOR)!!.baseValue = config!!.getDouble("armor_point")
                    if (!player.world.isDayTime) break

                    val helmet = player.inventory.helmet
                    if (helmet == null || (helmet.itemMeta !is org.bukkit.inventory.meta.Damageable)) {
                        if (!player.isInWater && player.location.block.lightFromSky == 15.toByte()) {
                            player.fireTicks = 100
                        }
                    }
                    else {
                        if (player.location.block.lightFromSky == 15.toByte()) {
                            val meta = helmet.itemMeta as org.bukkit.inventory.meta.Damageable
                            val unbreaking = meta.enchants[Enchantment.UNBREAKING] ?: 0

                            if (Random.nextDouble() < (0.6 + (0.4 / (unbreaking + 1))) && (player.gameMode != GameMode.CREATIVE)) {
                                meta.damage += 1
                            }
                            if (meta.damage >= helmet.type.maxDurability) {
                                player.inventory.helmet = null
                                player.world.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
                            } else {
                                helmet.itemMeta = meta
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }


    // 언데드 적대 제거는 FriendlyUndead에 구현되어 있음


    // 침대 이용 불가
    @EventHandler
    fun onBedUse(event: PlayerBedEnterEvent) {
        if (event.player.ability != this) return
        event.isCancelled = true
        if (!event.player.world.isDayTime) {
            event.player.sendActionBar(Component.text("좀비킹은 침대에서 잠을 잘 수 없습니다.", NamedTextColor.RED))
        }
    }


    // 즉시 회복을 즉시 피해로 변경
    @EventHandler
    fun onHealed(event: EntityRegainHealthEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (player.ability != this) return

        if (event.regainReason != EntityRegainHealthEvent.RegainReason.MAGIC) return
        event.isCancelled = true
        player.health = (player.health - event.amount).coerceAtLeast(0.0)
    }

    // 즉시 피해를 즉시 회복으로 변경
    @EventHandler
    fun onDamaged(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (player.ability != this) return
        if (player.gameMode == GameMode.CREATIVE) return

        if (event.cause != EntityDamageEvent.DamageCause.MAGIC) return
        event.isCancelled = true
        player.health = (player.health + event.damage).coerceAtMost(player.getAttribute(Attribute.MAX_HEALTH)!!.value)
    }


    // 모든 근접 공격에 대해 고정피해를 입히는 이벤트
    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (event.damager !is Player) return
        val player = event.damager as Player
        if (player.ability != this) return

        if (event.entity !is Damageable) return
        val target = event.entity as Damageable
        if (target !is LivingEntity || (target.noDamageTicks > 10)) return

        target.health = (target.health - config!!.getDouble("damage_addition")).coerceAtLeast(0.0)

        // 주변 언데드의 공격 대상 변경
        if (!AbilityManager().isEnemy(player, target)) return
        val range = config!!.getDouble("bullying_range")
        for (entity in player.getNearbyEntities(range*2, range*2, range*2)) {
            if (entity == target) continue
            if (!(FriendlyUndead.undead.any { it.isInstance(entity) })) continue
            (entity as Mob).target = target
        }
    }

    // 적에게 피해를 입을 때 좀비 소환
    @EventHandler
    fun onEntityDamageByPlayer(event: EntityDamageByEntityEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (player.ability != this) return

        val damager: Damageable = event.damager as? Damageable ?: return
        if (!AbilityManager().isEnemy(player, damager)) return
        if (Random.nextDouble() < config!!.getDouble("reinforcement_probability")) {
            // 50회 생성 시도
            for (i in 1..50) {
                player.sendMessage("소환 시도 $i")
                val location = player.location.clone().add(
                    Random.nextDouble(-40.0, 40.0),
                    Random.nextDouble(-5.0, 5.0),
                    Random.nextDouble(-40.0, 40.0),
                )
                location.set(
                    location.x.roundToInt() + 0.5,
                    location.y.roundToInt() + 0.0,
                    location.z.roundToInt() + 0.5
                )
                // 생성 위치가 유효한지 확인
                if (!location.block.isPassable) continue
                if (!location.block.getRelative(0, 1, 0).isPassable) continue
                if (!location.block.getRelative(0, -1, 0).isSolid) continue

                player.world.spawn(location, org.bukkit.entity.Zombie::class.java) {
                    it.target = damager as LivingEntity
                }
                player.sendMessage(Component.text("좀비가 소환되었습니다. $location", NamedTextColor.GREEN))
                return
            }
        }
    }
}