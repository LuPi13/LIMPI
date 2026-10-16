package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.config.AbilitySettings
import com.github.lupi13.limpi.quests.WhyDidYouDoThat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.ceil

object ChargeShotPlus : ChargeShotAbility(
    grade = Grade.EPIC,
    displayName = Component.text("차지샷+", NamedTextColor.AQUA),
    codeName = "charge_shot_plus",
    description = listOf(
        Component.text("차지샷", NamedTextColor.AQUA)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("더 빠르게 충전되고, 완충되는 순간 발사하면", NamedTextColor.WHITE),
        Component.text("피해가 증가합니다. 처치하면 쿨타임을 돌려받습니다.", NamedTextColor.WHITE)
    )
) {
    override val glint: Boolean get() = true

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(WhyDidYouDoThat.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    /** 쿨타임이 이미 끝난 뒤의 처치로 받은, 다음 충전의 시작 틱. */
    private val precharge: MutableMap<UUID, Int> = mutableMapOf()

    override fun extraDetails(): List<Component> {
        val config = config!!
        return listOf(
            Component.text("완충되는 바로 그 순간에 발사하면 피해가 ", NamedTextColor.WHITE)
                .append(Component.text("${config.getDouble("perfect_multiplier")}", NamedTextColor.GREEN))
                .append(Component.text("배가 됩니다.", NamedTextColor.WHITE)),
            Component.text("이 능력으로 처치하면 쿨타임의 ", NamedTextColor.WHITE)
                .append(Component.text("${Math.round(config.getDouble("kill_refund") * 100)}%", NamedTextColor.GREEN))
                .append(Component.text("를 돌려받습니다.", NamedTextColor.WHITE)),
            Component.text("충전 중이면 바로 그만큼 충전되고, 아니면 다음 충전이", NamedTextColor.WHITE),
            Component.text("그만큼 충전된 채로 시작합니다.", NamedTextColor.WHITE)
        )
    }

    /** 완충된 틱부터 perfect_window_ticks 동안 발사하면 perfect_multiplier배. */
    private fun perfect(settings: AbilitySettings, ticks: Int): Boolean {
        val maxCharge = settings.getInt("max_charge_ticks")
        return ticks >= maxCharge && ticks < maxCharge + settings.getInt("perfect_window_ticks")
    }

    override fun damage(settings: AbilitySettings, ticks: Int): Double {
        val base = super.damage(settings, ticks)
        return if (perfect(settings, ticks)) base * settings.getDouble("perfect_multiplier") else base
    }

    /** 정확한 타이밍에 쐈을 때만 종소리가 납니다. */
    override fun onFire(player: Player, settings: AbilitySettings, ticks: Int) {
        if (perfect(settings, ticks)) player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 2f)
    }

    override fun initialTicks(player: Player): Int = precharge.remove(player.uniqueId) ?: 0

    /**
     * 처치 보상(kill_refund 비율만큼의 완충 틱): 충전 중이면 바로 충전되고, 충전 중이 아니면(발사 직후 쿨타임 포함)
     * 다음 충전이 그만큼 충전된 채로 시작합니다(최대 완충까지 누적).
     */
    override fun onKill(player: Player) {
        val settings = config ?: return
        val refund = settings.getDouble("kill_refund")
        if (refund <= 0.0) return
        val id = player.uniqueId
        val maxCharge = settings.getInt("max_charge_ticks")
        val extra = ceil(maxCharge * refund).toInt()
        val current = charging[id]
        if (current != null && !current.effect.scope.closed) { current.ticks = Math.addExact(current.ticks, extra); return }
        precharge[id] = minOf(maxCharge, (precharge[id] ?: 0) + extra)
    }

    override fun release(owner: UUID) {
        super.release(owner)
        precharge.remove(owner)
    }

    override fun stop() {
        super.stop()
        precharge.clear()
    }
}
