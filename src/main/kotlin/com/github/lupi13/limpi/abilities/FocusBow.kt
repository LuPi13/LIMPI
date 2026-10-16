package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID

object FocusBow : Ability(
    grade = Grade.EPIC,
    displayName = Component.text("집중의 활시위", NamedTextColor.AQUA),
    codeName = "focus_bow",
    material = Material.ARCHER_POTTERY_SHERD,
    description = listOf(
        Component.text("공중에서 활시위를 당기면 시야가 확대되고", NamedTextColor.WHITE),
        Component.text("제자리에서 아주 느리게 낙하합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("공중에서 ", NamedTextColor.WHITE)
                .append(Component.text("활", NamedTextColor.YELLOW))
                .append(Component.text("시위를 당기면 시야가 확대되고", NamedTextColor.WHITE)),
            Component.text("제자리에서 아주 느리게 낙하합니다.", NamedTextColor.WHITE),
            Component.text("처음 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("brake_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
                .append(Component.text(" 동안 서서히 감속한 뒤 멈춰 섭니다.", NamedTextColor.WHITE)),
            Component.text("화살", NamedTextColor.YELLOW)
                .append(Component.text("을 쏘거나, ", NamedTextColor.WHITE))
                .append(Component.text("활", NamedTextColor.YELLOW))
                .append(Component.text("시위를 놓거나, 착지하면 끝나며", NamedTextColor.WHITE)),
            Component.text("집중하는 동안 쌓인 낙하 거리는 초기화됩니다.", NamedTextColor.WHITE)
        )
    }

    private val arrows = listOf(Material.ARROW, Material.TIPPED_ARROW, Material.SPECTRAL_ARROW)

    /** 집중 중인 플레이어 → 그 효과 범위. 효과가 닫히면(착지, 사격, 사망, 퇴장 등) 항목도 지워집니다. */
    private val focusing: MutableMap<UUID, AbilityEffect> = mutableMapOf()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || !event.action.isRightClick || event.useItemInHand() == Event.Result.DENY) return
        if (event.item?.type != Material.BOW || player.uniqueId in focusing) return
        if ((player as Entity).isOnGround || player.isFlying || player.isDead) return
        if (FileManager.delivery.blocked(player.uniqueId)) return
        if (player.gameMode != GameMode.CREATIVE && arrows.none { player.inventory.contains(it) }) return
        startFocus(player)
    }

    private fun startFocus(player: Player) {
        val brakeTicks = config!!.getInt("brake_ticks")
        val brake = config!!.getDouble("brake_multiplier")
        val slowness = config!!.getInt("slowness_amplifier")
        val effect = AbilityRuntime.begin(player, codeName)
        val owner = player.uniqueId
        focusing[owner] = effect
        effect.own { if (focusing[owner] === effect) focusing.remove(owner) }
        var ticks = 0
        effect.repeat(0, 1) { active ->
            val current = active.player() ?: run { active.close(); return@repeat }
            // 상호작용 직후에는 아직 활을 당기는 상태가 아닐 수 있으므로 첫 틱은 활시위 검사를 건너뜁니다.
            val drawing = current.hasActiveItem() && current.activeItem.type == Material.BOW
            if ((current as Entity).isOnGround || current.isFlying || (ticks > 0 && !drawing)) {
                current.fallDistance = 0f
                active.close()
                return@repeat
            }
            if (ticks < brakeTicks) {
                current.velocity = current.velocity.multiply(brake)
            } else {
                current.velocity = Vector()
                current.fallDistance = 0f
            }
            // 구속 효과는 시야를 확대(FOV 축소)하는 용도입니다.
            active.potion(current, PotionEffect(PotionEffectType.SLOWNESS, 3, slowness, true, false, false))
            ticks++
        }
    }

    override fun release(owner: UUID) { focusing.remove(owner)?.close() }
    override fun stop() {
        focusing.values.toList().forEach { it.close() }
        focusing.clear()
    }
}
