package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.ability.HealOverTime
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.NeedHealing
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 응급처치 계열의 공통 부분. 종이를 들고 우클릭하면 종이 1개를 소모해(값싼 소모품이라 DB 기록 없이 게임 안에서 줄임) 자신을,
 * 적이 아닌 생명체를 보고 우클릭하면 그 대상을 [heal_seconds]초에 걸쳐 [heal_amount]만큼 서서히 회복합니다. 쿨타임 [cooldown_ticks].
 * [throwing]이면(전투 의무관) 쿨타임이 아닐 때 종이 1장을 버리면 [throw_speed_multiplier]배 빠르게 날아가고,
 * 날아가는 동안 적이 아닌 생명체에 닿으면 종이가 사라지며 그 대상을 회복합니다(던질 때 쿨타임).
 * 이미 체력이 가득 찬 대상에게는 쓰지 않습니다(소모·쿨타임 없음). 갑옷 거치대·마네킹·포탑은 생명체로 보지 않습니다.
 * "적이 아닌 대상"은 능력 적 범위 설정(`/ability enemy`)상 적이 아닌 생명체입니다.
 * 쿨타임은 종이의 아이템 쿨타임 표시로 보여 줍니다.
 */
abstract class FirstAidAbility(
    grade: Grade,
    displayName: Component,
    codeName: String,
    description: List<Component>,
    acquisition: AcquisitionType,
    /** 종이를 던져 회복할 수 있는지(전투 의무관). */
    private val throwing: Boolean,
) : Ability(grade, displayName, codeName, Material.PAPER, description, acquisition = acquisition, needFile = true) {

    protected fun healAmount() = config!!.getDouble("heal_amount")
    protected fun healSeconds() = config!!.getDouble("heal_seconds")
    protected fun cooldownTicks() = config!!.getInt("cooldown_ticks")

    private val lastUse = HashMap<UUID, Long>()
    /** 생명체를 우클릭한 틱. 같은 클릭에 바닐라가 이어 보내는 "허공 우클릭"으로 자신까지 회복하지 않게 합니다. */
    private val entityClickTick = HashMap<UUID, Int>()
    /** 마지막으로 치료한 생명체 클릭(틱, 대상). 같은 클릭의 두 번째 이벤트를 걸러냅니다. */
    private data class EntityClick(val tick: Int, val target: UUID)
    private val lastEntityClick = HashMap<UUID, EntityClick>()

    private fun coolingDown(player: Player): Boolean {
        val last = lastUse[player.uniqueId] ?: return false
        return System.currentTimeMillis() - last < cooldownTicks() * 50
    }

    private fun startCooldown(player: Player) {
        lastUse[player.uniqueId] = System.currentTimeMillis()
        player.setCooldown(Material.PAPER, cooldownTicks()) // 핫바의 종이에 쿨타임 표시
    }

    private fun paper(item: ItemStack?): Boolean = item != null && item.type == Material.PAPER && item.amount > 0 && !AbilityManager.active(item)

    /** 회복할 수 있는 대상: 살아 있고 체력이 가득 차지 않은, 장식·포탑이 아닌 생명체. 다른 대상이면 [player]의 적이 아니어야 합니다. */
    private fun treatable(player: Player, target: Entity?): Boolean {
        if (target !is LivingEntity || !target.isValid || target.isDead) return false
        if (target is ArmorStand || target is Mannequin || com.github.lupi13.limpi.turret.TurretRuntime.isTurret(target)) return false
        if (target.world.uid != player.world.uid) return false
        if (target.uniqueId != player.uniqueId && AbilityManager.isEnemy(player, target)) return false // 능력 적 범위상 적은 치료하지 않음
        val max = target.getAttribute(Attribute.MAX_HEALTH)?.value ?: return false
        return target.health < max
    }

    private fun consume(player: Player, hand: EquipmentSlot) {
        if (player.gameMode == GameMode.CREATIVE) return
        val held = player.inventory.getItem(hand)
        held.amount -= 1
        player.inventory.setItem(hand, if (held.amount <= 0) null else held)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onTreatOther(event: PlayerInteractEntityEvent) {
        // 한 번의 클릭에 위치가 담긴 이벤트(InteractAt)와 일반 이벤트가 둘 다 오거나 하나만 옵니다(서버 버전마다 다름).
        // 둘 다 받고, 같은 틱에 같은 대상으로 두 번째가 오면 거래창 등만 막고 다시 치료하지 않습니다.
        val player = event.player
        if (player.ability != this) return
        val hand = event.hand
        if (!paper(player.inventory.getItem(hand))) return
        val target = event.rightClicked
        val now = Bukkit.getCurrentTick()
        val click = EntityClick(now, target.uniqueId)
        if (lastEntityClick[player.uniqueId] == click) { event.isCancelled = true; return }
        if (target.uniqueId == player.uniqueId || !treatable(player, target)) return
        if (FileManager.delivery.blocked(player.uniqueId)) return
        entityClickTick[player.uniqueId] = now
        lastEntityClick[player.uniqueId] = click
        event.isCancelled = true // 주민 거래창 등 대신 치료
        if (coolingDown(player)) return
        consume(player, hand)
        startCooldown(player)
        treat(player, target as LivingEntity)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onTreatSelf(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || !event.action.isRightClick || event.useItemInHand() == Event.Result.DENY) return
        val hand = event.hand ?: return
        if (!paper(event.item)) return
        val clicked = event.clickedBlock
        if (event.action == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.type.isInteractable && !player.isSneaking) return
        if (entityClickTick[player.uniqueId] == Bukkit.getCurrentTick()) return
        if (!treatable(player, player) || FileManager.delivery.blocked(player.uniqueId)) return
        if (coolingDown(player)) return
        event.setUseItemInHand(Event.Result.DENY)
        consume(player, hand)
        startCooldown(player)
        treat(player, player)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onThrow(event: PlayerDropItemEvent) {
        if (!throwing) return
        val player = event.player
        if (player.ability != this || FileManager.delivery.blocked(player.uniqueId)) return
        val item = event.itemDrop
        val stack = item.itemStack
        // 한 장만 버릴 때(Q). 묶음 버리기(Ctrl+Q)는 평범하게 떨어뜨립니다.
        if (stack.amount != 1 || !paper(stack)) return
        if (coolingDown(player)) return // 쿨타임이면 평범한 버리기
        startCooldown(player)
        item.velocity = item.velocity.multiply(config!!.getDouble("throw_speed_multiplier"))
        item.pickupDelay = THROW_FLIGHT_TICKS
        player.world.playSound(player.location, Sound.BLOCK_DISPENSER_LAUNCH, 1f, 2f)
        fly(player, item)
    }

    /** 날아가는 종이: 땅에 닿거나 [THROW_FLIGHT_TICKS]가 지나기 전에 적이 아닌 생명체에 닿으면 종이를 없애고 그 대상을 회복합니다. */
    private fun fly(player: Player, item: Item) {
        val effect = AbilityRuntime.begin(player, codeName)
        var ticks = 0
        effect.repeat(1, 1) { active ->
            val thrower = active.player() ?: run { active.close(); return@repeat }
            if (!item.isValid || item.isOnGround || ++ticks > THROW_FLIGHT_TICKS) { active.close(); return@repeat }
            val hit = item.world.getNearbyEntities(item.boundingBox.clone().expand(HIT_MARGIN))
                .firstOrNull { it.uniqueId != thrower.uniqueId && treatable(thrower, it) } as? LivingEntity ?: return@repeat
            item.remove()
            treat(thrower, hit)
            active.close()
        }
    }

    /** [target]을 서서히 회복합니다. 회복하는 사람([healer])의 효과라 그가 나가거나 죽으면 멈춥니다. */
    private fun treat(healer: Player, target: LivingEntity) {
        val steps = HealOverTime.steps(healAmount(), healSeconds())
        target.world.playSound(target.location, Sound.ITEM_LEAD_TIED, 1f, 0.5f)
        if (target.uniqueId != healer.uniqueId && countsQuest) QuestManager.progress(healer, NeedHealing)
        if (steps.isEmpty()) return
        val effect = AbilityRuntime.begin(healer, codeName)
        var index = 0
        effect.repeat(0, HealOverTime.PERIOD_TICKS.toLong()) { active ->
            if (!target.isValid || target.isDead || index >= steps.size) { active.close(); return@repeat }
            val max = target.getAttribute(Attribute.MAX_HEALTH)?.value
            if (max != null && target.health < max) {
                target.heal(steps[index], EntityRegainHealthEvent.RegainReason.CUSTOM)
                target.world.spawnParticle(Particle.HAPPY_VILLAGER, target.location.add(0.0, target.height / 2, 0.0), 2, 0.3, 0.3, 0.3, 0.0)
            }
            if (++index >= steps.size) active.close()
        }
    }

    /** 다른 대상 회복을 퀘스트 [NeedHealing]에 셀지(응급처치만). */
    protected open val countsQuest: Boolean get() = false

    override fun release(owner: UUID) { entityClickTick.remove(owner); lastEntityClick.remove(owner) }
    override fun resetCooldowns(owner: UUID) {
        lastUse.remove(owner)
        Bukkit.getPlayer(owner)?.setCooldown(Material.PAPER, 0)
    }
    override fun stop() { entityClickTick.clear(); lastEntityClick.clear() }

    private companion object {
        /** 던진 종이가 회복할 수 있는 비행 시간(틱). 그동안은 아무도 줍지 못합니다. */
        const val THROW_FLIGHT_TICKS = 40
        /** 던진 종이의 판정 상자를 넓히는 양(블록). */
        const val HIT_MARGIN = 0.4
    }
}
