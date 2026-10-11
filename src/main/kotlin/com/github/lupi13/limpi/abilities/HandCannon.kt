package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityDamage
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.BoldForBoom
import com.github.lupi13.limpi.quests.NoHurt
import com.github.lupi13.limpi.quests.NogadaKing
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.QuestManager
import com.github.lupi13.limpi.quests.TriggerFinger
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Creeper
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.random.Random

object HandCannon : Ability(
    grade = Grade.COMMON,
    element = Element.NONE,
    displayName = Component.text("핸드캐논", NamedTextColor.GRAY),
    codeName = "hand_cannon",
    material = Material.INK_SAC,
    description = listOf(
        Component.text("왼손이 핸드캐논으로 고정됩니다.", NamedTextColor.WHITE),
        Component.text("")
            .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
            .append(Component.text("키를 누르면 작은 탄환을 발사합니다.", NamedTextColor.WHITE))
    ),
    needFile = true,
    restrictedSlot = 40
) {
    override val relatedQuest: List<Quest> by lazy { listOf(NogadaKing, TriggerFinger, NoHurt, BoldForBoom) }

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("왼손이 ", NamedTextColor.WHITE)
                .append(Component.text("핸드캐논", NamedTextColor.YELLOW))
                .append(Component.text("으로 고정됩니다.", NamedTextColor.WHITE)),
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 누르면 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 주는 작은 탄환을 발사합니다.", NamedTextColor.WHITE)),
            Component.text("이 능력은 특정 조건을 통해 다른 능력을 해금할 수 있습니다.", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, true)
        )
    }

    /** 핸드캐논 계열(핸드캐논, 펄스캐논)의 왼손 아이템을 만듭니다. */
    internal fun cannonItem(name: Component, line: String): ItemStack {
        val item = ItemStack(Material.TURTLE_SCUTE)
        val meta = item.itemMeta
        meta.itemModel = NamespacedKey("minecraft", "ink_sac")
        meta.itemName(name)
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text(line, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        return item
    }

    override val activeItem: ItemStack by lazy {
        cannonItem(Component.text("핸드캐논", NamedTextColor.GRAY), "키를 누르면 작은 탄환을 발사합니다.")
    }

    /** 날아가는 탄환: 투사체 UUID → 소유 효과와 적중 처리. 효과가 닫히면 탄환도 제거됩니다. */
    private class Bullet(val effect: AbilityEffect, val onHit: (Player, Damageable) -> Unit)
    private val bullets: MutableMap<UUID, Bullet> = mutableMapOf()

    /** 손가락 안아파요? 를 이미 달성한 플레이어 (더는 사격마다 DB에 기록하지 않음). */
    private val shotQuestDone: MutableSet<UUID> = mutableSetOf()

    /**
     * 중력 없는 작은 탄환을 발사합니다. 탄환은 [lifetime]틱 후 사라지고, [적][AbilityManager.isEnemy]이 아닌 개체는 통과합니다.
     */
    internal fun shoot(player: Player, ability: String, velocity: Double, lifetime: Int, onHit: (Player, Damageable) -> Unit) {
        val direction = player.location.direction
        val effect = AbilityRuntime.begin(player, ability)
        try {
            val bullet = player.world.spawn(player.eyeLocation.add(direction), Snowball::class.java) { snowball ->
                effect.ownEntity(snowball)
                snowball.setGravity(false)
                snowball.isInvulnerable = true
                snowball.item = ItemStack(Material.STONE_BUTTON)
                snowball.shooter = player
                snowball.velocity = direction.clone().multiply(velocity)
            }
            val id = bullet.uniqueId
            bullets[id] = Bullet(effect, onHit)
            effect.own { bullets.remove(id) }
            effect.repeat(delay = lifetime.toLong()) { it.close() }
        } catch (failure: Exception) { effect.close(); throw failure }
        player.world.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.5f, Random.nextDouble(0.5, 2.0).toFloat())
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBulletHit(event: ProjectileHitEvent) {
        val bullet = bullets[event.entity.uniqueId] ?: return
        event.isCancelled = true // 눈덩이의 바닐라 충돌(넉백·블레이즈 피해)을 막고 직접 처리합니다.
        val player = bullet.effect.player()
        val target = event.hitEntity
        if (target == null || player == null || !bullet.effect.live()) { bullet.effect.close(); return }
        if (target.uniqueId == player.uniqueId) return
        if (target !is Damageable) { bullet.effect.close(); return }
        if (!AbilityManager.isEnemy(player, target)) return // 적이 아니면 통과
        bullet.effect.close()
        bullet.onHit(player, target)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this || FileManager.delivery.blocked(player.uniqueId)) return
        event.isCancelled = true
        val config = requireNotNull(config)
        shoot(player, codeName, config.getDouble("velocity"), config.getInt("lifetime_ticks")) { owner, target ->
            if (!AbilityDamage.hit(owner, target, codeName, config.getDouble("damage"), knockback = false) || !target.isDead) return@shoot
            if (owner.ability != this) return@shoot
            if (target is Creeper && target.isPowered) QuestManager.clearQuests(owner, BoldForBoom)
            QuestManager.progress(owner, NogadaKing)
        }
        checkShotQuests(player)
    }

    private fun checkShotQuests(player: Player) {
        val id = player.uniqueId
        // TRIGGER FINGER!: 원본처럼 아직 날아가는 자기 탄환이 20발 이상이면 달성합니다.
        if (bullets.values.count { it.effect.owner == id } >= 20) QuestManager.clearQuests(player, TriggerFinger)

        if (id in shotQuestDone) return
        QuestManager.progress(player, NoHurt)
        if (QuestManager.isClearedQuest(player, NoHurt)) shotQuestDone += id
    }

    override fun release(owner: UUID) {
        shotQuestDone.remove(owner)
        bullets.values.filter { it.effect.owner == owner }.forEach { it.effect.close() }
    }

    override fun stop() {
        shotQuestDone.clear()
        bullets.values.toList().forEach { it.effect.close() }
        bullets.clear()
    }
}
