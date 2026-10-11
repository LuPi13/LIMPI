package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityDamage
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.TriggerFinger
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.random.Random

object PulseCannon : Ability(
    grade = Grade.COMMON,
    acquisition = AcquisitionType.QUEST,
    displayName = Component.text("펄스캐논", NamedTextColor.YELLOW),
    codeName = "pulse_cannon",
    material = Material.INK_SAC,
    description = listOf(
        Component.text("핸드캐논", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("1회 사격에 5점사를 가하며,", NamedTextColor.WHITE),
        Component.text("10% 확률로 무적시간을 제공하지 않습니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    restrictedSlot = 40
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(TriggerFinger.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("핸드캐논", NamedTextColor.GRAY)
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 누르면 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 주는 탄환을 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("burst_count")}", NamedTextColor.GREEN))
                .append(Component.text("점사합니다.", NamedTextColor.WHITE)),
            Component.text("적중 시 ", NamedTextColor.WHITE)
                .append(Component.text("${Math.round(config!!.getDouble("no_invulnerability_chance") * 100)}", NamedTextColor.GREEN).append(Component.text("%", NamedTextColor.WHITE)))
                .append(Component.text(" 확률로 무적시간을 제공하지 않습니다.", NamedTextColor.WHITE))
        )
    }

    override val activeItem: ItemStack by lazy {
        HandCannon.cannonItem(Component.text("펄스캐논", NamedTextColor.YELLOW), "키를 누르면 작은 탄환을 5개 발사합니다.")
    }

    /** 점사 중인 플레이어. 점사가 끝나기 전에는 다시 쏠 수 없습니다. */
    private val bursting: MutableMap<UUID, AbilityEffect> = mutableMapOf()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this || FileManager.delivery.blocked(player.uniqueId)) return
        event.isCancelled = true
        val id = player.uniqueId
        if (bursting[id]?.scope?.closed == false) return

        val count = config!!.getInt("burst_count")
        val velocity = config!!.getDouble("velocity")
        val lifetime = config!!.getInt("lifetime_ticks")
        val damage = config!!.getDouble("damage")
        val chance = config!!.getDouble("no_invulnerability_chance")
        val effect = AbilityRuntime.begin(player, codeName)
        bursting[id] = effect
        effect.own { if (bursting[id] === effect) bursting.remove(id) }
        var fired = 0
        effect.repeat { scope ->
            val owner = scope.player() ?: return@repeat scope.close()
            if (fired++ >= count) return@repeat scope.close()
            HandCannon.shoot(owner, codeName, velocity, lifetime) { shooter, target ->
                if (AbilityDamage.hit(shooter, target, codeName, damage, knockback = false) && target is LivingEntity && Random.nextDouble() < chance) {
                    target.noDamageTicks = 0
                }
            }
        }
    }

    override fun release(owner: UUID) { bursting.remove(owner)?.close() }

    override fun stop() {
        bursting.values.toList().forEach { it.close() }
        bursting.clear()
    }
}
