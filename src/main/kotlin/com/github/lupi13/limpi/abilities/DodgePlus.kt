package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.DodgeMaster
import com.github.lupi13.limpi.quests.Quest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import java.util.UUID

/**
 * 회피 계열(퀘스트 보상) 능력의 공통 동작: 이동 방향 기록, 지상에서 빠른 두 번 웅크리기, 쿨타임.
 * 실제 이동은 [Dodge.dodge]를 재사용하며, 각 능력은 [performDodge]/[afterDodge]로 효과를 덧붙입니다.
 * 설정에는 최소한 cooldown(ms), velocity, allowJump가 있어야 합니다.
 */
abstract class DodgeVariant(
    grade: Grade,
    displayName: Component,
    codeName: String,
    material: Material,
    description: List<Component>,
) : Ability(
    grade = grade,
    displayName = displayName,
    codeName = codeName,
    material = material,
    description = description,
    acquisition = AcquisitionType.QUEST,
    needFile = true
) {
    /** 이 능력을 해금하는 퀘스트. 객체 초기화 순환을 피하려고 getter로 구현합니다. */
    protected abstract val unlockQuest: Quest

    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(unlockQuest.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    protected val lastSneakTime: MutableMap<UUID, Long> = mutableMapOf()
    protected val lastDodgeTime: MutableMap<UUID, Long> = mutableMapOf()

    @EventHandler(ignoreCancelled = true)
    fun onDodgeVariantMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.ability != this) return
        Dodge.velocityMap[player.uniqueId] = event.to.toVector().subtract(event.from.toVector())
    }

    @EventHandler(ignoreCancelled = true)
    fun onDodgeVariantSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (player.ability != this) return
        if (!(player as Entity).isOnGround || !event.isSneaking) return
        val now = System.currentTimeMillis()
        val previous = lastSneakTime.put(player.uniqueId, now)
        if (previous == null || now - previous > 300) return // 300ms 이내에 두 번 웅크리면

        val cooldown = config!!.getInt("cooldown")
        val last = lastDodgeTime[player.uniqueId]
        if (last != null && now - last <= cooldown) {
            AbilityManager.showCooldown(player, cooldown, last)
            return
        }
        lastDodgeTime[player.uniqueId] = now
        performDodge(player)
        afterDodge(player)
    }

    /** 기본 회피(이동 방향 수평 가속). */
    protected open fun performDodge(player: Player) {
        Dodge.dodge(player, config!!.getDouble("velocity"), config!!.getBoolean("allowJump"))
    }

    /** 회피 직후 추가 효과. */
    protected open fun afterDodge(player: Player) {}

    /** 마지막 회피 후 [windowMillis] 이내인지 확인합니다. */
    protected fun dodgedWithin(owner: UUID, windowMillis: Int): Boolean {
        val last = lastDodgeTime[owner] ?: return false
        return System.currentTimeMillis() - last <= windowMillis
    }

    protected fun inheritLine(): Component = Component.text("회피", NamedTextColor.GRAY)
        .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE))

    protected fun cooldownLine(): Component = Component.text("쿨타임: ", NamedTextColor.WHITE)
        .append(Component.text("${config!!.getInt("cooldown") / 1000.0}", NamedTextColor.GREEN))
        .append(Component.text("초", NamedTextColor.WHITE))

    override fun release(owner: UUID) {
        lastSneakTime.remove(owner); lastDodgeTime.remove(owner); Dodge.velocityMap.remove(owner)
    }

    override fun stop() {
        lastSneakTime.clear(); lastDodgeTime.clear()
    }
}

object DodgePlus : DodgeVariant(
    grade = Grade.RARE,
    displayName = Component.text("회피+", NamedTextColor.GRAY),
    codeName = "dodge_plus",
    material = Material.RABBIT_FOOT,
    description = listOf(
        Component.text("회피", NamedTextColor.GRAY)
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("회피의 쿨타임이 감소합니다.", NamedTextColor.WHITE)
    )
) {
    override val unlockQuest: Quest get() = DodgeMaster

    override val details: List<Component> by settingsCached {
        listOf(
            inheritLine(),
            Component.text("지상에서 빠르게 두 번 웅크려", NamedTextColor.WHITE),
            Component.text("이동 방향으로 회피합니다.", NamedTextColor.WHITE),
            cooldownLine()
        )
    }
}
