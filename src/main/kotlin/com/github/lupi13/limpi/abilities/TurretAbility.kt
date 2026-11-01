package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.config.specs.TurretSpecs
import com.github.lupi13.limpi.estate.EstateRules
import com.github.lupi13.limpi.events.EstateGuard
import com.github.lupi13.limpi.turret.TurretKind
import com.github.lupi13.limpi.turret.TurretRuntime
import com.github.lupi13.limpi.turret.TurretStats
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 포탑 설치 능력의 공통 부분: [kinds] 중 손에 든 아이템([TurretKind.trigger])에 맞는 포탑을 블록을 우클릭한 면에 세웁니다.
 * 손에 든 설치 아이템과 인벤토리의 레드스톤은 값싼 소모품이라 DB 기록 없이 게임 안에서 바로 줄입니다(사용자 결정).
 * 상위 능력은 이 클래스를 상속해 [kinds]에 종류를 더 나열하면 되고, 설정 키는 [TurretSpecs.spec]이 같은 규칙으로 만듭니다.
 */
abstract class TurretAbility(
    grade: Grade,
    displayName: Component,
    codeName: String,
    material: Material,
    description: List<Component>,
    acquisition: AcquisitionType = AcquisitionType.GACHA,
) : Ability(grade, displayName, codeName, material, description, acquisition = acquisition, needFile = true) {
    /** 이 능력으로 세울 수 있는 포탑 종류. */
    abstract val kinds: List<TurretKind>

    fun prefix(kind: TurretKind): String = TurretSpecs.prefix(kinds, kind)
    fun stats(kind: TurretKind): TurretStats = kind.stats(config!!, prefix(kind))
    protected fun cooldownTicks(): Int = config!!.getInt("cooldown_ticks")

    private val lastPlaced = HashMap<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlace(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || event.action != Action.RIGHT_CLICK_BLOCK || event.useItemInHand() == Event.Result.DENY) return
        val hand = event.hand ?: return
        val item = event.item ?: return
        val kind = kinds.firstOrNull { it.trigger == item.type } ?: return
        if (AbilityManager.active(item)) return
        val clicked = event.clickedBlock ?: return
        if (clicked.type.isInteractable && !player.isSneaking) return // 상자·문 등은 원래대로 쓰기
        if (FileManager.delivery.blocked(player.uniqueId)) return

        val now = System.currentTimeMillis()
        val cooldown = cooldownTicks() * 50
        val last = lastPlaced[player.uniqueId]
        if (last != null && now - last < cooldown) { AbilityManager.showCooldown(player, cooldown, last); return }

        val space = clicked.getRelative(event.blockFace)
        if (!TurretRuntime.spaceFree(space)) return
        val estate = FileManager.estates.at(space.world.uid, space.x, space.y, space.z)
        if (estate != null && !config!!.getBoolean("allow_in_others_estate") &&
            !EstateRules.canBuild(estate, player.uniqueId, player.hasPermission(EstateGuard.BYPASS))) {
            player.sendActionBar(Component.text("다른 사람의 사유지에는 포탑을 설치할 수 없습니다!", NamedTextColor.YELLOW))
            return
        }

        val stats = stats(kind)
        val creative = player.gameMode == GameMode.CREATIVE
        val redstone = ItemStack(Material.REDSTONE)
        if (!creative) {
            val held = player.inventory.getItem(hand)
            if (held.type != kind.trigger || held.amount < stats.ingotCost) {
                player.sendActionBar(Component.text("${kind.itemName} ${stats.ingotCost}개가 필요합니다!", NamedTextColor.YELLOW))
                return
            }
            if (stats.redstoneCost > 0 && !player.inventory.containsAtLeast(redstone, stats.redstoneCost)) {
                player.sendActionBar(Component.text("레드스톤 ${stats.redstoneCost}개가 필요합니다!", NamedTextColor.YELLOW))
                return
            }
        }
        event.setUseItemInHand(Event.Result.DENY)
        event.setUseInteractedBlock(Event.Result.DENY)

        // 다른 플러그인이 소환을 막으면 아무것도 줄이지 않습니다.
        TurretRuntime.place(player, codeName, kind, stats, space, event.blockFace.oppositeFace) ?: return
        if (!creative) {
            val held = player.inventory.getItem(hand)
            held.amount -= stats.ingotCost
            player.inventory.setItem(hand, if (held.amount <= 0) null else held)
            if (stats.redstoneCost > 0) player.inventory.removeItem(redstone.asQuantity(stats.redstoneCost))
        }
        lastPlaced[player.uniqueId] = now
    }

    override fun release(owner: UUID) = TurretRuntime.closeOwner(owner, codeName)
    override fun resetCooldowns(owner: UUID) { lastPlaced.remove(owner) }
    override fun stop() = TurretRuntime.closeAbility(codeName)
}
