package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.ability.AbilityItemTerms
import com.github.lupi13.limpi.ability.AbilityItemUse
import com.github.lupi13.limpi.platform.AbilityDamage
import com.github.lupi13.limpi.platform.PaperItemCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Door
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.BoundingBox
import java.util.UUID

object BlockThrower : Ability(
    grade = Grade.RARE,
    element = Element.UTILITY,
    displayName = Component.text("투척 설치", NamedTextColor.GRAY),
    codeName = "block_thrower",
    material = Material.STONE,
    description = listOf(
        Component.text("손에 든 블록을 허공에 우클릭하면 던지고,", NamedTextColor.WHITE),
        Component.text("떨어진 자리에 설치합니다.", NamedTextColor.WHITE),
        Component.text("적", NamedTextColor.RED)
            .append(Component.text("이 맞으면 피해를 줍니다.", NamedTextColor.WHITE))
    ),
    needFile = true
) {
    override val details: List<Component> by lazy {
        listOf(
            Component.text("손에 든 단단한 블록을 허공에 우클릭하면 던지고,", NamedTextColor.WHITE),
            Component.text("떨어진 자리에 설치합니다.", NamedTextColor.WHITE),
            Component.text("적", NamedTextColor.RED)
                .append(Component.text("이 맞으면 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 주고 블록은 떨어집니다.", NamedTextColor.WHITE)),
            Component.text("설치할 수 없는 자리에서도 블록이 떨어집니다.", NamedTextColor.WHITE),
            Component.text("이름·인챈트 등 정보가 붙은 블록은 던지지 않습니다.", NamedTextColor.GRAY),
            Component.text("투척 간격: ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("cooldown_ms") / 1000.0}", NamedTextColor.GREEN))
                .append(Component.text("초", NamedTextColor.WHITE))
        )
    }

    /** 던진 눈덩이에 실린 블록 종류. 재시작 후에도 남도록 엔티티 PDC에 저장합니다. */
    private val blockKey by lazy { NamespacedKey(plugin, "block_thrower_block") }
    private val lastThrow: MutableMap<UUID, Long> = mutableMapOf()

    /** 던질 수 있는 블록: 단단하고, 두 칸짜리(문·침대)가 아니며, 추가 정보가 없는 기본 아이템. */
    private fun throwable(item: ItemStack): Boolean {
        val type = item.type
        if (type.isAir || !type.isBlock || !type.isItem || !type.isSolid) return false
        if (AbilityManager.active(item) || !item.isSimilar(ItemStack(type))) return false
        val data = type.createBlockData()
        return data !is Door && data !is Bed
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || event.action != Action.RIGHT_CLICK_AIR || event.hand != EquipmentSlot.HAND) return
        if (event.useItemInHand() == Event.Result.DENY || FileManager.delivery.blocked(player.uniqueId)) return
        val item = event.item?.takeIf { it.amount > 0 } ?: return
        if (!throwable(item)) return
        val now = System.currentTimeMillis()
        val last = lastThrow[player.uniqueId]
        if (last != null && now - last < config!!.getInt("cooldown_ms")) return
        val type = item.type
        event.setUseItemInHand(Event.Result.DENY)

        val creative = player.gameMode == GameMode.CREATIVE
        if (!creative) {
            val original = PaperItemCodec.payload(item)
            if (!FileManager.delivery.claim(player, quiet = true)) return
            val before = PaperItemCodec.snapshot(player, true)
            val slot = player.inventory.heldItemSlot
            val source = before[slot] ?: return
            if (player.ability != this || source.payload != original) return
            val after = before.toMutableList().apply { this[slot] = if (source.count == 1) null else source.copy(count = source.count - 1) }
            val terms = AbilityItemTerms(codeName, slot, source.payload, null)
            if (!FileManager.delivery.remove(player, UUID.randomUUID(), AbilityItemUse.KIND, terms.encode(), before, after)) return
        }
        lastThrow[player.uniqueId] = now

        val velocity = player.location.direction.multiply(config!!.getDouble("velocity"))
        val snowball = player.world.spawn(player.eyeLocation, Snowball::class.java) { ball ->
            ball.shooter = player
            ball.item = ItemStack(type)
            ball.velocity = velocity
            ball.persistentDataContainer.set(blockKey, PersistentDataType.STRING, type.key.toString())
        }
        if (!snowball.isValid) {
            // 생성이 막혔다면 이미 소비한 블록을 그 자리에 떨어뜨립니다.
            if (!creative) drop(player.location, type)
            return
        }
        player.world.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1f, 1.2f)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLand(event: ProjectileHitEvent) {
        val projectile = event.entity as? Snowball ?: return
        val raw = projectile.persistentDataContainer.get(blockKey, PersistentDataType.STRING) ?: return
        projectile.persistentDataContainer.remove(blockKey)
        val type = Material.matchMaterial(raw)?.takeIf { it.isBlock && it.isItem } ?: return
        val shooter = (projectile.shooter as? Player)?.takeIf { it.isOnline }
        val location = projectile.location

        val hitEntity = event.hitEntity
        if (hitEntity != null) {
            if (shooter != null && hitEntity is LivingEntity) AbilityDamage.hit(shooter, hitEntity, codeName, config!!.getDouble("damage"))
            drop(location, type)
            return
        }
        val hitBlock = event.hitBlock
        val face = event.hitBlockFace
        if (hitBlock == null || face == null || shooter == null || !place(shooter, hitBlock, hitBlock.getRelative(face), type)) {
            drop(location, type)
        }
    }

    /** 실제 설치처럼 BlockPlaceEvent로 건축 보호를 확인하고, 거부되면 되돌립니다. */
    private fun place(player: Player, against: Block, target: Block, type: Material): Boolean {
        val world = target.world
        if (player.world.uid != world.uid) return false
        if (target.y < world.minHeight || target.y >= world.maxHeight) return false
        if (!world.worldBorder.isInside(target.location) || !target.isReplaceable) return false
        if (world.getNearbyEntities(BoundingBox.of(target)).any { it is LivingEntity && !it.isDead }) return false
        val replaced = target.state
        target.setType(type, true)
        val placeEvent = BlockPlaceEvent(target, replaced, against, ItemStack(type), player, true, EquipmentSlot.HAND)
        plugin.server.pluginManager.callEvent(placeEvent)
        if (placeEvent.isCancelled || !placeEvent.canBuild()) {
            replaced.update(true, false)
            return false
        }
        world.playSound(target.location, target.blockData.soundGroup.placeSound, SoundCategory.BLOCKS, 1f, 0.8f)
        return true
    }

    private fun drop(location: Location, type: Material) {
        location.world?.dropItemNaturally(location, ItemStack(type))
    }

    override fun release(owner: UUID) {
        lastThrow.remove(owner)
    }

    override fun stop() {
        lastThrow.clear()
    }
}
