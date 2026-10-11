package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityDamage
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.NogadaKing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Damageable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.random.Random

object ExplosionMove : Ability(
    grade = Grade.RARE,
    acquisition = AcquisitionType.QUEST,
    element = Element.EXPLOSIVE,
    displayName = Component.text("익스플로전 무브", NamedTextColor.GOLD),
    codeName = "explosion_move",
    material = Material.HONEYCOMB,
    description = listOf(
        Component.text("왼손이 리볼버 캐논으로 고정됩니다.", NamedTextColor.WHITE),
        Component.text("짧은 전방에 폭발 피해를 주며", NamedTextColor.WHITE),
        Component.text("시전자는 반대방향으로 강하게 밀려납니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    restrictedSlot = 40
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(NogadaKing.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("왼손이 ", NamedTextColor.WHITE)
                .append(Component.text("리볼버 캐논", NamedTextColor.YELLOW))
                .append(Component.text("으로 고정됩니다.", NamedTextColor.WHITE)),
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 짧은 전방의 ", NamedTextColor.WHITE))
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 폭발 피해를 주며,", NamedTextColor.WHITE)),
            Component.text("시전자는 반대방향으로 강하게 밀려납니다.", NamedTextColor.WHITE),
            Component.text("최대 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("max_charges")}", NamedTextColor.GREEN))
                .append(Component.text("번까지 축적됩니다. (충전: ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getInt("charge_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
                .append(Component.text(")", NamedTextColor.WHITE))
        )
    }

    override val activeItem: ItemStack by lazy {
        val item = ItemStack(Material.TURTLE_SCUTE)
        val meta = item.itemMeta
        meta.itemModel = NamespacedKey("minecraft", "honeycomb")
        meta.itemName(Component.text("리볼버 캐논", NamedTextColor.GOLD))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 누르면 전방에 폭발 피해를 줍니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        item
    }

    /** 장탄 수와 다음 장탄까지 흐른 틱. 새로 능력을 얻으면 0발에서 시작합니다. */
    private class Magazine(var count: Int = 0, var ticks: Int = 0)
    private val magazines: MutableMap<UUID, Magazine> = mutableMapOf()

    override fun start() {
        AbilityRuntime.global(1) {
            for (player in plugin.server.onlinePlayers) {
                if (player.ability === this) magazines.putIfAbsent(player.uniqueId, Magazine())
                else if (player.uniqueId in magazines) release(player.uniqueId)
            }
            val max = config!!.getInt("max_charges")
            val chargeTicks = config!!.getInt("charge_ticks")
            for ((owner, magazine) in magazines.entries.toList()) {
                val player = plugin.server.getPlayer(owner)
                if (player == null) { release(owner); continue }
                if (player.isDead || FileManager.delivery.blocked(owner)) continue
                if (magazine.count < max) {
                    if (++magazine.ticks >= chargeTicks) {
                        magazine.ticks = 0
                        magazine.count++
                        player.playSound(player.location, Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1f)
                    }
                } else magazine.ticks = 0
                player.sendActionBar(Component.text("현재 장탄 수: ${magazine.count}발", NamedTextColor.YELLOW, TextDecoration.BOLD))
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this || FileManager.delivery.blocked(player.uniqueId)) return
        event.isCancelled = true
        val magazine = magazines.getOrPut(player.uniqueId) { Magazine() }
        if (magazine.count < 1) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 2f)
            return
        }
        magazine.count--

        val direction = player.location.direction
        val distance = config!!.getDouble("hit_distance")
        val size = config!!.getDouble("hit_size")
        val damage = config!!.getDouble("damage")
        val center = player.eyeLocation.toVector().add(direction.clone().multiply(distance))
        val box = BoundingBox.of(center, size / 2.0, size / 2.0, size / 2.0)
        for (entity in player.world.getNearbyEntities(box)) {
            if (entity !is Damageable || entity == player) continue
            AbilityDamage.hit(player, entity, codeName, damage)
        }

        // 반동
        val recoil = direction.clone().multiply(-config!!.getDouble("recoil"))
        recoil.y = recoil.y / 2.0
        if (recoil.y >= 0) recoil.add(Vector(0.0, 0.2, 0.0))
        player.velocity = recoil

        val world = player.world
        val eye = player.eyeLocation
        repeat(100) {
            world.spawnParticle(Particle.FLAME, eye, 0, direction.x + Random.nextDouble(-0.5, 0.5),
                direction.y + Random.nextDouble(-0.5, 0.5), direction.z + Random.nextDouble(-0.5, 0.5), Random.nextDouble(0.1, 0.4))
            world.spawnParticle(Particle.SMOKE, eye, 0, direction.x + Random.nextDouble(-0.5, 0.5),
                direction.y + Random.nextDouble(-0.5, 0.5), direction.z + Random.nextDouble(-0.5, 0.5), Random.nextDouble(0.1, 0.4))
        }
        world.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.7f, 1f)
        world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 2f)
    }

    override fun release(owner: UUID) { magazines.remove(owner) }
    override fun stop() { magazines.clear() }
}
