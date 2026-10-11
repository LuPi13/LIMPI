package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.IntoTheDark
import com.github.lupi13.limpi.quests.Quest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object BlightRanger : DodgeVariant(
    grade = Grade.MYTHIC,
    element = Element.MAGIC,
    displayName = Component.text("어둠그림자 돌격대", NamedTextColor.DARK_PURPLE),
    codeName = "blight_ranger",
    material = Material.WITHER_SKELETON_SKULL,
    description = listOf(
        Component.text("소멸의 걸음", NamedTextColor.DARK_GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("회피하면 은신과 함께 연막을 뿌리고", NamedTextColor.WHITE),
        Component.text("이동속도 증가를 얻습니다. 연막 안의 ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("은", NamedTextColor.WHITE)),
        Component.text("약화되며, 어둠 속에서 분신이 생겨납니다.", NamedTextColor.WHITE)
    )
) {
    override val unlockQuest: Quest get() = IntoTheDark

    override val details: List<Component> by lazy {
        listOf(
            Component.text("소멸의 걸음", NamedTextColor.DARK_GRAY)
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
            Component.text("회피 후 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("invisible_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초 동안 은신하고 신속 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("speed_level")}", NamedTextColor.GREEN))
                .append(Component.text("을 얻습니다.", NamedTextColor.WHITE)),
            Component.text("회피한 자리에 반경 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("smoke_radius")}", NamedTextColor.GREEN))
                .append(Component.text("블록의 연막을 뿌립니다.", NamedTextColor.WHITE)),
            Component.text("연막 안의 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("은 구속, 채굴 피로, 실명, 나약함,", NamedTextColor.WHITE)),
            Component.text("멀미, 어둠에 걸립니다.", NamedTextColor.WHITE),
            Component.text("어둠 속에서 7명의 분신이 함께 움직입니다.", NamedTextColor.WHITE),
            Component.text("공격하거나 피해를 입으면 은신이 풀립니다.", NamedTextColor.WHITE),
            cooldownLine()
        )
    }

    private val active: MutableMap<UUID, AbilityEffect> = mutableMapOf()
    private val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
    private val weaken by lazy { listOf(PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.BLINDNESS,
        PotionEffectType.WEAKNESS, PotionEffectType.NAUSEA, PotionEffectType.DARKNESS) }

    override fun afterDodge(player: Player) {
        val id = player.uniqueId
        active.remove(id)?.close()
        val duration = config!!.getInt("invisible_ticks")
        val speed = config!!.getInt("speed_level")
        val radius = config!!.getDouble("smoke_radius")
        val weakenTicks = config!!.getInt("weaken_ticks")
        val range = config!!.getDouble("target_clear_range")
        val center = player.location.add(0.0, 1.0, 0.0)
        val world = player.world
        world.playSound(center, Sound.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 1f, 0.5f)

        val effect = AbilityRuntime.begin(player, codeName)
        active[id] = effect
        effect.own {
            if (active[id] === effect) active.remove(id)
            effect.player()?.let { owner ->
                showArmor(owner)
                owner.playSound(owner.location, Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 1f)
            }
        }
        effect.potion(player, PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, true, false, true))
        if (speed > 0) effect.potion(player, PotionEffect(PotionEffectType.SPEED, duration, speed - 1, true, false, true))
        var ticks = 0
        effect.repeat { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            if (ticks++ >= duration || owner.world.uid != world.uid) return@repeat scope.close()
            // 갑옷은 실제로 벗기지 않고 다른 플레이어에게만 숨깁니다.
            val hidden = armorSlots.associateWith { ItemStack.empty() }
            owner.trackedBy.forEach { viewer -> viewer.sendEquipmentChange(owner, hidden) }

            // 연막
            val dust = Particle.DustOptions(Color.fromRGB(Random.nextInt(0, 11), Random.nextInt(0, 11), Random.nextInt(0, 11)), 4f)
            world.spawnParticle(Particle.DUST, center, 100, 3.0, 0.6, 3.0, 0.0, dust, true)

            // 회피한 자리를 중심으로 45도씩 돌린 위치에 분신(연기)
            val offset = owner.location.toVector().subtract(center.toVector())
            val ground = owner.location.clone().subtract(0.0, 0.1, 0.0).block
            val moving = (com.github.lupi13.limpi.abilities.Dodge.velocityMap[owner.uniqueId]?.length() ?: 0.0) >= 0.1
            for (i in 1..7) {
                val angle = Math.toRadians(45.0 * i)
                val x = offset.z * sin(angle) + offset.x * cos(angle)
                val z = offset.z * cos(angle) - offset.x * sin(angle)
                val clone = center.clone().add(x, offset.y + 0.9, z)
                world.spawnParticle(Particle.LARGE_SMOKE, clone, 10, 0.2, 0.4, 0.2, 0.0, null, true)
                if (!ground.type.isAir) {
                    if (owner.isSprinting) world.spawnParticle(Particle.BLOCK, clone.clone().subtract(0.0, 0.9, 0.0), 1, 0.1, 0.01, 0.1, ground.blockData)
                    if (!owner.isSneaking && moving && ticks % 10 == 0) {
                        world.playSound(clone, ground.blockData.soundGroup.stepSound, SoundCategory.PLAYERS, 0.3f, 1f)
                    }
                }
            }

            owner.getNearbyEntities(range, range, range).forEach { entity ->
                if (entity is Mob && entity.target?.uniqueId == owner.uniqueId) entity.target = null
            }
            for (entity in world.getNearbyEntities(center, radius, radius, radius)) {
                if (entity !is LivingEntity || entity == owner || entity.location.distance(center) > radius) continue
                if (!AbilityManager.isEnemy(owner, entity)) continue
                weaken.forEach { type -> scope.potion(entity, PotionEffect(type, weakenTicks, 0, true, true, true)) }
            }
        }
        player.playSound(player.location, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 2f)
    }

    private fun showArmor(owner: Player) {
        val equipment = owner.equipment
        val real = armorSlots.associateWith { equipment.getItem(it) }
        owner.trackedBy.forEach { viewer -> viewer.sendEquipmentChange(owner, real) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamaged(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        active[player.uniqueId]?.close()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val attacker = damager as? Player ?: (damager as? Projectile)?.shooter as? Player ?: return
        active[attacker.uniqueId]?.close()
    }

    override fun release(owner: UUID) {
        super.release(owner)
        active.remove(owner)?.close()
    }

    override fun stop() {
        super.stop()
        active.values.toList().forEach { it.close() }
        active.clear()
    }
}
