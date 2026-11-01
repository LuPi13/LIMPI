package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.ability.PerfectReloadPolicy
import com.github.lupi13.limpi.ability.PerfectReloadPolicy.State
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CrossbowMeta
import java.util.UUID

/**
 * 완벽한 재장전: 석궁을 쏘면 액션바에 게이지가 1틱에 한 칸씩 차고, 가운데 성공 구간에서 장전을 시작하면 그 장전에 한해
 * 빠른 장전이 [bonus]단계 더 붙은 속도로 장전됩니다(이미 빠른 장전이 있으면 그 단계에 더함).
 *
 * 석궁 아이템은 바꾸지 않습니다. 바닐라가 장전 정도를 "사용 시간 / 원래 장전 시간"으로 정하므로, 매 틱 남은 사용 시간을 줄여
 * 사용 시간을 [PerfectReloadPolicy.boostedUsedTicks]로 맞춥니다. 인챈트를 잠시 붙였다 떼는 방식은 장전 도중 아이템을
 * 던지거나 옮기면 인챈트가 남을 수 있어 쓰지 않았습니다.
 */
object PerfectReload : Ability(
    grade = Grade.RARE,
    displayName = Component.text("완벽한 재장전", NamedTextColor.WHITE),
    codeName = "perfect_reload",
    material = Material.CROSSBOW,
    description = listOf(
        Component.text("석궁 사격 후 제시된 타이밍에 재장전 시", NamedTextColor.WHITE),
        Component.text("재장전 속도가 빨라집니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("석궁", NamedTextColor.YELLOW)
                .append(Component.text("을 사격하고, 액션바에 나타나는 타이밍에 장전을 시작하면", NamedTextColor.WHITE)),
            Component.text("해당 장전에 한해 ", NamedTextColor.WHITE)
                .append(Component.text("빠른 장전 ${bonus()}", NamedTextColor.AQUA))
                .append(Component.text(" 효과가 추가됩니다.", NamedTextColor.WHITE))
        )
    }

    private fun length() = config!!.getInt("gauge_length")
    private fun perfectBars() = config!!.getInt("perfect_bars")
    private fun bonus() = config!!.getInt("quick_charge_bonus")

    private enum class Phase { GAUGE, BOOST }

    /** 사격 한 번의 게이지. 설정은 사격 순간 값으로 고정합니다(도중 reload가 판정을 바꾸지 않게). */
    private class Session(val effect: AbilityEffect, val startedTick: Int, val length: Int, val perfect: Int, val bonus: Int) {
        var phase = Phase.GAUGE
        /** 지금 액션바에 채워져 보이는 칸 수. */
        var shown = 0
        /** 장전 시작을 누른 순간의 [shown]. 다음 틱에 실제로 장전 중인지 확인하고 판정합니다. */
        var pressedAt: Int? = null
        /** 성공 뒤 장전을 시작하고 실제로 흐른 틱과 그 석궁의 빠른 장전 단계. */
        var elapsed = 0
        var level = 0
    }

    private val sessions: MutableMap<UUID, Session> = mutableMapOf()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability != this || event.bow?.type != Material.CROSSBOW) return
        val tick = Bukkit.getCurrentTick()
        // 다중 발사는 화살마다 이벤트가 오므로 같은 틱의 두 번째부터는 무시합니다.
        if (sessions[player.uniqueId]?.startedTick == tick) return
        sessions.remove(player.uniqueId)?.effect?.close()
        start(player, tick)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!event.action.isRightClick || event.useItemInHand() == Event.Result.DENY) return
        val session = sessions[player.uniqueId] ?: return
        if (session.phase != Phase.GAUGE || session.pressedAt != null) return
        val item = event.item ?: return
        if (item.type != Material.CROSSBOW || charged(item)) return
        session.pressedAt = session.shown
    }

    private fun start(player: Player, tick: Int) {
        val effect = AbilityRuntime.begin(player, codeName)
        val owner = player.uniqueId
        val session = Session(effect, tick, length(), perfectBars(), bonus())
        sessions[owner] = session
        effect.own { if (sessions[owner] === session) sessions.remove(owner) }
        show(player, session, State.RUNNING)
        effect.repeat(1, 1) { active ->
            val current = active.player() ?: run { active.close(); return@repeat }
            when (session.phase) {
                Phase.GAUGE -> gaugeTick(current, session)
                Phase.BOOST -> boostTick(current, session)
            }
        }
    }

    private fun gaugeTick(player: Player, session: Session) {
        val pressed = session.pressedAt
        if (pressed != null) {
            session.pressedAt = null
            // 화살이 없거나 다른 이유로 장전이 시작되지 않았으면 판정하지 않고 게이지를 계속 진행합니다.
            if (chargingCrossbow(player)) {
                if (PerfectReloadPolicy.perfect(session.length, session.perfect, pressed)) succeed(player, session) else miss(player, session)
                return
            }
        }
        if (session.shown >= session.length) { miss(player, session); return }
        session.shown++
        show(player, session, State.RUNNING)
    }

    private fun succeed(player: Player, session: Session) {
        show(player, session, State.PERFECT)
        session.phase = Phase.BOOST
        session.level = player.activeItem.getEnchantmentLevel(Enchantment.QUICK_CHARGE)
        session.elapsed = player.activeItemUsedTime
        boost(player, session)
    }

    private fun miss(player: Player, session: Session) {
        show(player, session, State.MISSED)
        session.effect.close()
    }

    /** 장전이 끝나거나(장전됨) 멈추면(손을 놓음·아이템 바꿈) 효과도 끝납니다. */
    private fun boostTick(player: Player, session: Session) {
        if (!chargingCrossbow(player)) { session.effect.close(); return }
        session.elapsed++
        boost(player, session)
    }

    private fun boost(player: Player, session: Session) {
        val target = PerfectReloadPolicy.boostedUsedTicks(session.elapsed, session.level, session.bonus)
        val skip = target - player.activeItemUsedTime
        if (skip > 0) player.activeItemRemainingTime = (player.activeItemRemainingTime - skip).coerceAtLeast(1)
    }

    private fun show(player: Player, session: Session, state: State) =
        player.sendActionBar(PerfectReloadPolicy.render(displayName, session.length, session.perfect, session.shown, state))

    private fun charged(item: ItemStack): Boolean = (item.itemMeta as? CrossbowMeta)?.hasChargedProjectiles() == true

    private fun chargingCrossbow(player: Player): Boolean {
        if (!player.hasActiveItem()) return false
        val item = player.activeItem
        return item.type == Material.CROSSBOW && !charged(item)
    }

    override fun release(owner: UUID) { sessions.remove(owner)?.effect?.close() }
    override fun stop() {
        sessions.values.toList().forEach { it.effect.close() }
        sessions.clear()
    }
}
