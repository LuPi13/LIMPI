package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.SpectralArrow
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.random.Random

/**
 * 일제히 사격: 활을 쏘면 다른 손에 든 화살을 전부 쏩니다.
 * - 화살이 왼손: 그 개수만큼 무작위 편차(spread, 도)를 가진 화살을 한꺼번에 발사합니다.
 * - 화살이 오른손(활은 왼손): 첫 발 다음 틱부터 1틱마다 한 발씩, 바라보는 방향으로 첫 발의 속도와 바닐라 편차로 연사합니다.
 * 화살은 바닐라 화살 소비처럼 인벤토리에서 직접 줄입니다(지급할 권리가 없으므로 회수 기록을 쓰지 않음).
 * 사격 이벤트 시점에 바닐라는 이미 첫 발을 손에서 꺼냈습니다(무한·크리에이티브면 꺼내지 않고 그 화살을 줍지 못하게 함).
 * 그래서 그 순간 손에 남은 화살을 전부 추가로 쏘고 모두 줄입니다. 무한이어도 손에 화살이 남지 않고, 첫 발만 바닐라처럼
 * 줍지 못하는 화살로 남아 개수가 늘지 않습니다. 활은 추가로 쏜 수만큼 닳습니다(내구성 인챈트 적용, 크리에이티브 제외).
 * 추가 화살의 아이템은 손에 든 실제 화살을 본뜹니다. 첫 화살의 아이템에는 바닐라의 숨은 표시(intangible_projectile)가
 * 붙을 수 있어, 그것을 본뜨면 주운 화살이 인벤토리 화살과 겹치지 않습니다.
 */
object VolleyShot : Ability(
    grade = Grade.RARE,
    displayName = Component.text("일제히 사격", NamedTextColor.WHITE),
    codeName = "volley_shot",
    material = Material.BOW,
    description = listOf(
        Component.text("양손에 활과 화살을 들고 사격하면", NamedTextColor.WHITE),
        Component.text("손에 든 화살을 전부 발사합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {
    override val details: List<Component> = listOf(
        Component.text("양손에 ", NamedTextColor.WHITE).append(item("활")).append(Component.text("과 ", NamedTextColor.WHITE)).append(item("화살"))
            .append(Component.text("을 들고 사격하면 손에 든 ", NamedTextColor.WHITE)).append(item("화살"))
            .append(Component.text("을 전부 발사합니다.", NamedTextColor.WHITE)),
        item("화살").append(Component.text("이 왼손에 있을 경우, 손에 든 ", NamedTextColor.WHITE)).append(item("화살"))
            .append(Component.text(" 모두를 한번에 사격합니다.", NamedTextColor.WHITE)),
        item("화살").append(Component.text("이 오른손에 있을 경우, 손에 든 ", NamedTextColor.WHITE)).append(item("화살"))
            .append(Component.text("을 매우 빠르게 연사합니다.", NamedTextColor.WHITE)),
        Component.text("이 능력은 ", NamedTextColor.YELLOW).append(Component.text("무한", NamedTextColor.AQUA))
            .append(Component.text(" 마법 부여의 영향을 받지 않습니다.", NamedTextColor.YELLOW))
    )

    /** 상세 설명의 아이템 이름은 다른 능력처럼 노란색입니다. */
    private fun item(name: String): Component = Component.text(name, NamedTextColor.YELLOW)

    private val arrows = setOf(Material.ARROW, Material.TIPPED_ARROW, Material.SPECTRAL_ARROW)

    /** 연사 중인 플레이어. 연사 도중의 다른 사격은 평범한 한 발로 처리합니다. */
    private val rapid: MutableSet<UUID> = mutableSetOf()

    /**
     * 첫 화살에서 옮겨 받을 성질(활 인챈트로 정해진 피해·치명타·관통·화염과 속도). 물약·분광 효과는 화살 아이템에서 옵니다
     * (화살의 물약 효과는 줍는 아이템에 들어 있으므로 아이템을 정하면 함께 정해집니다).
     */
    private class Template(val critical: Boolean, val damage: Double, val pierce: Int, val fireTicks: Int, val weapon: ItemStack?, val speed: Double) {
        companion object {
            fun of(arrow: AbstractArrow) = Template(arrow.isCritical, arrow.damage, arrow.pierceLevel, arrow.fireTicks, arrow.weapon?.clone(), arrow.velocity.length())
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        if (player.ability != this || event.bow?.type != Material.BOW) return
        val first = event.projectile as? AbstractArrow ?: return
        val consumable = event.consumable ?: return
        val arrowHand = if (event.hand == EquipmentSlot.HAND) EquipmentSlot.OFF_HAND else EquipmentSlot.HAND
        val held = player.inventory.getItem(arrowHand)
        if (held.type !in arrows || held.type != consumable.type) return // 화살이 활의 반대 손에 있을 때만
        val ammo = held.asOne() // 손에 든 실제 화살(숨은 표시 없음)
        val template = Template.of(first)
        if (arrowHand == EquipmentSlot.OFF_HAND) volley(player, ammo, held.amount, template, event.hand)
        else if (rapid.add(player.uniqueId)) rapidFire(player, ammo, template, player.inventory.heldItemSlot)
    }

    /**
     * 첫 발 뒤 왼손에 남은 화살 [remaining]개를 지금 한꺼번에 쏘고, 활을 그 수만큼 한 번에 닳게 하고, 그만큼 왼손에서 줄입니다.
     * 활이 부서져도 덜 쏘지 않습니다.
     */
    private fun volley(player: Player, ammo: ItemStack, remaining: Int, template: Template, bowHand: EquipmentSlot) {
        if (remaining <= 0) return
        val spread = Math.toRadians(config!!.getDouble("spread"))
        val look = player.eyeLocation.direction
        repeat(remaining) { launch(player, ammo, template, deviate(look, spread)) }
        if (creative(player)) return
        wearBow(player, bowHand, remaining)
        consume(player, EquipmentSlot.OFF_HAND, ammo, remaining)
    }

    /** 바닐라와 같은 방식(내구성 인챈트 확률, 부서지면 깨지는 연출)으로 [hand]의 활을 [amount]만큼 닳게 합니다. */
    private fun wearBow(player: Player, hand: EquipmentSlot, amount: Int) {
        if (amount <= 0 || player.inventory.getItem(hand).type != Material.BOW) return
        player.damageItemStack(hand, amount)
    }

    /**
     * 오른손 화살: 다음 틱부터 1틱마다 한 발씩, 손의 화살이 떨어질 때까지 쏘고 그때마다 왼손 활을 1 닳게 합니다.
     * 사격을 시작한 핫바 칸([slot])을 벗어나거나, 왼손 활이 부서지거나 없어지면(크리에이티브 제외) 연사를 멈춥니다.
     */
    private fun rapidFire(player: Player, ammo: ItemStack, template: Template, slot: Int) {
        object : com.github.lupi13.limpi.platform.AbilityRunnable(player, codeName) {
            init { own { rapid.remove(player.uniqueId) } }
            override fun tick() {
                if (player.isDead || player.inventory.heldItemSlot != slot || !player.inventory.itemInMainHand.isSimilar(ammo)) { cancel(); return }
                if (!creative(player) && player.inventory.itemInOffHand.type != Material.BOW) { cancel(); return }
                if (!creative(player)) {
                    if (consume(player, EquipmentSlot.HAND, ammo, 1) == 0) { cancel(); return }
                    wearBow(player, EquipmentSlot.OFF_HAND, 1)
                }
                launch(player, ammo, template, vanillaSpread(player.eyeLocation.direction))
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    /** [slot]의 같은 화살을 최대 [amount]개 줄이고 실제로 줄인 수를 돌려줍니다. */
    private fun consume(player: Player, slot: EquipmentSlot, ammo: ItemStack, amount: Int): Int {
        val stack = player.inventory.getItem(slot)
        if (!stack.isSimilar(ammo)) return 0
        val taken = minOf(amount, stack.amount)
        stack.amount -= taken
        player.inventory.setItem(slot, if (stack.amount <= 0) null else stack)
        return taken
    }

    private fun creative(player: Player) = player.gameMode == GameMode.CREATIVE

    private fun launch(player: Player, ammo: ItemStack, template: Template, direction: Vector) {
        val type = if (ammo.type == Material.SPECTRAL_ARROW) SpectralArrow::class.java else Arrow::class.java
        val arrow = player.launchProjectile(type, direction.multiply(template.speed))
        arrow.setItemStack(ammo.clone())
        arrow.isCritical = template.critical
        arrow.damage = template.damage
        arrow.pierceLevel = template.pierce
        if (template.fireTicks > 0) arrow.fireTicks = template.fireTicks
        template.weapon?.let { arrow.setWeapon(it) }
        arrow.pickupStatus = if (creative(player)) AbstractArrow.PickupStatus.CREATIVE_ONLY else AbstractArrow.PickupStatus.ALLOWED
    }

    /** 바라보는 방향에서 표준편차 [spread](라디안)만큼 무작위로 벗어난 단위 벡터. */
    private fun deviate(look: Vector, spread: Double): Vector {
        val direction = look.clone().normalize()
        if (spread <= 0.0) return direction
        val offset = Vector(gaussian(), gaussian(), gaussian()).multiply(kotlin.math.tan(minOf(spread, 1.5)))
        return direction.add(offset).normalize()
    }

    /** 바닐라 활과 같은 편차(정규화한 방향에 축마다 triangle(0, 0.0172275)). */
    private fun vanillaSpread(look: Vector): Vector {
        fun triangle() = (Random.nextDouble() - Random.nextDouble()) * VANILLA_INACCURACY
        return look.clone().normalize().add(Vector(triangle(), triangle(), triangle()))
    }

    private const val VANILLA_INACCURACY = 0.0172275

    private fun gaussian(): Double = java.util.concurrent.ThreadLocalRandom.current().nextGaussian()

    override fun release(owner: UUID) { rapid.remove(owner) }
    override fun stop() { rapid.clear() }
}
