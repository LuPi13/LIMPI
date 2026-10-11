package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.ability.AbilityItemTerms
import com.github.lupi13.limpi.ability.AbilityItemUse
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.platform.PaperItemCodec
import com.github.lupi13.limpi.quests.BoldForBoom
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

object RealHandCannon : Ability(
    grade = Grade.EPIC,
    acquisition = AcquisitionType.QUEST,
    element = Element.EXPLOSIVE,
    displayName = Component.text("진짜 핸드'캐논'", NamedTextColor.DARK_RED),
    codeName = "real_hand_cannon",
    material = Material.FIRE_CHARGE,
    description = listOf(
        Component.text("왼손이 핸드'캐논'으로 고정됩니다.", NamedTextColor.WHITE),
        Component.text("")
            .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
            .append(Component.text("키를 누르면 인벤토리 내의 화약 1개를 소비해", NamedTextColor.WHITE)),
        Component.text("지형을 파괴하는 짧은 곡사포를 발사합니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    restrictedSlot = 40
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(BoldForBoom.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("왼손이 핸드'캐논'으로 고정됩니다.", NamedTextColor.WHITE),
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 누르면 인벤토리 내의 화약 1개를 소비해", NamedTextColor.WHITE)),
            Component.text("위력 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("explosion_power")}", NamedTextColor.GREEN))
                .append(Component.text("의 폭발을 일으키는 짧은 곡사포를 발사합니다.", NamedTextColor.WHITE)),
            Component.text("폭발은 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게만 피해를 주며, ", NamedTextColor.WHITE))
                .append(Component.text(if (config!!.getBoolean("break_blocks")) "지형을 파괴합니다." else "지형을 파괴하지 않습니다.", NamedTextColor.WHITE)),
            Component.text("크리에이티브 모드에서는 화약을 소비하지 않습니다.", NamedTextColor.GRAY),
            Component.text("쿨타임: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown_ticks") / 20.0}초", NamedTextColor.WHITE))
        )
    }

    override val activeItem: ItemStack by lazy {
        val item = ItemStack(Material.TURTLE_SCUTE)
        val meta = item.itemMeta
        meta.itemModel = NamespacedKey("minecraft", "fire_charge")
        meta.itemName(Component.text("핸드'캐논'", NamedTextColor.DARK_RED))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 누르면 화약 1개로 곡사포를 발사합니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        item
    }

    /** 날아가는 포탄: 투사체 UUID → 소유 효과. */
    private val cannons: MutableMap<UUID, AbilityEffect> = mutableMapOf()
    private val lastShot: MutableMap<UUID, Long> = mutableMapOf()
    /** 폭발 중인 소유자. 이 동안의 피해는 적에게만 허용합니다 (LetsBoom과 같은 방식). */
    private var exploding: UUID? = null

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this || FileManager.delivery.blocked(player.uniqueId)) return
        event.isCancelled = true
        val id = player.uniqueId
        val cooldown = config!!.getInt("cooldown_ticks").toLong() * 50L
        val last = lastShot[id]
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            AbilityManager.showCooldown(player, cooldown.toInt(), last)
            return
        }
        if (player.gameMode != GameMode.CREATIVE && !consumeGunpowder(player)) return
        if (player.ability != this || !player.isOnline || player.isDead) return
        lastShot[id] = System.currentTimeMillis()

        val direction = player.location.direction
        val effect = AbilityRuntime.begin(player, codeName)
        try {
            val cannon = player.world.spawn(player.eyeLocation.add(direction), Snowball::class.java) { snowball ->
                effect.ownEntity(snowball)
                snowball.shooter = player
                snowball.item = ItemStack(Material.TNT)
                snowball.isVisualFire = true
                snowball.velocity = direction.clone().multiply(config!!.getDouble("velocity"))
            }
            val cannonId = cannon.uniqueId
            cannons[cannonId] = effect
            effect.own { cannons.remove(cannonId) }
            effect.repeat(delay = config!!.getInt("lifetime_ticks").toLong()) { it.close() }
            // 날아가는 동안 연기 꼬리. 포탄 효과가 소유하므로 포탄이 사라지면 함께 멈춥니다.
            effect.repeat(delay = 1) { scope ->
                if (!cannon.isValid) return@repeat scope.close()
                val at = cannon.location
                at.world.spawnParticle(Particle.LARGE_SMOKE, at, 2, 0.05, 0.05, 0.05, 0.01, null, true)
                at.world.spawnParticle(Particle.SMOKE, at, 4, 0.1, 0.1, 0.1, 0.01, null, true)
            }
            player.world.playSound(cannon.location, Sound.ENTITY_CREEPER_PRIMED, 1f, 1f)
            player.world.playSound(cannon.location, Sound.ITEM_TRIDENT_THROW, 1f, 0.5f)
        } catch (failure: Exception) { effect.close(); throw failure }
    }

    /** 저장 공간의 화약 1개를 회수 기록과 함께 소비합니다. 없으면 안내하고 false. */
    private fun consumeGunpowder(player: Player): Boolean {
        val noGunpowder = {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 2f)
            player.sendActionBar(Component.text("화약을 갖고 있지 않습니다!", NamedTextColor.YELLOW))
            false
        }
        if (player.inventory.storageContents.none { it?.type == Material.GUNPOWDER }) return noGunpowder()
        if (!FileManager.delivery.claim(player)) return false
        val slot = player.inventory.storageContents.indexOfFirst { it?.type == Material.GUNPOWDER }
        if (slot < 0) return noGunpowder()
        val before = PaperItemCodec.snapshot(player, true)
        val source = before[slot] ?: return false
        if (player.ability != this) return false
        val after = before.toMutableList().apply { this[slot] = if (source.count == 1) null else source.copy(count = source.count - 1) }
        val terms = AbilityItemTerms(codeName, slot, source.payload, null)
        return FileManager.delivery.remove(player, UUID.randomUUID(), AbilityItemUse.KIND, terms.encode(), before, after)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCannonHit(event: ProjectileHitEvent) {
        val effect = cannons[event.entity.uniqueId] ?: return
        val player = effect.player()
        val live = effect.live()
        val location = event.entity.location
        effect.close() // 포탄 제거
        if (player == null || !live) return
        val power = config!!.getDouble("explosion_power").toFloat()
        exploding = player.uniqueId
        try {
            location.world.createExplosion(player, location, power, false, config!!.getBoolean("break_blocks"), true)
        } finally { exploding = null }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun filterExplosion(event: EntityDamageEvent) {
        val owner = exploding ?: return
        val target = event.entity
        val player = plugin.server.getPlayer(owner)
        if (player == null || target !is LivingEntity || target.uniqueId == owner || !AbilityManager.isEnemy(player, target)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun protectHanging(event: HangingBreakEvent) { if (exploding != null) event.isCancelled = true }

    override fun release(owner: UUID) {
        lastShot.remove(owner)
        cannons.values.filter { it.owner == owner }.forEach { it.close() }
    }

    override fun stop() {
        lastShot.clear()
        cannons.values.toList().forEach { it.close() }
        cannons.clear()
        exploding = null
    }
}
