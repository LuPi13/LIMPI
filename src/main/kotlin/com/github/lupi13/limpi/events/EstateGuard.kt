package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.estate.Estate
import com.github.lupi13.limpi.estate.EstateRules
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.projectiles.BlockProjectileSource
import org.bukkit.entity.minecart.ExplosiveMinecart
import org.bukkit.block.data.Directional
import org.bukkit.entity.*
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.*
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.bukkit.event.world.StructureGrowEvent
import java.util.EnumSet
import java.util.UUID

/**
 * Keeps estates from being changed by anyone but the owner and trusted players. Always registered, also when the estate
 * system is off. Each check is one index lookup that returns at once outside estates; no event reads the DB.
 */
class EstateGuard : Listener {
    private val estates get() = FileManager.estates
    private val warned = HashMap<UUID, Long>()

    private fun at(block: Block): Estate? = estates.at(block.world.uid, block.x, block.y, block.z)
    private fun at(location: Location?): Estate? {
        val world = location?.world ?: return null
        return estates.at(world.uid, location.blockX, location.blockY, location.blockZ)
    }
    private fun allowed(estate: Estate?, player: Player?): Boolean =
        estate == null || EstateRules.canBuild(estate, player?.uniqueId, player?.hasPermission(BYPASS) == true)

    /** Returns true when [player] may not change [estate], telling them so at most once a second. */
    private fun refuse(player: Player, estate: Estate?, notify: Boolean = true): Boolean {
        if (allowed(estate, player)) return false
        if (notify) {
            val now = System.currentTimeMillis()
            if (now - (warned[player.uniqueId] ?: 0L) >= 1000L) {
                warned[player.uniqueId] = now
                player.sendMessage(Component.text("이 지역은 ", NamedTextColor.YELLOW)
                    .append(Component.text(EstateEvents.ownerName(estate!!.owner), NamedTextColor.GREEN))
                    .append(Component.text("님의 사유지입니다.", NamedTextColor.YELLOW)))
            }
        }
        return true
    }

    /** The player responsible for an entity's action: itself, a projectile's shooter or the one who lit the TNT. */
    private fun playerOf(entity: Entity?): Player? = when (entity) {
        is Player -> entity
        is Projectile -> entity.shooter as? Player
        is TNTPrimed -> playerOf(entity.source)
        is AreaEffectCloud -> entity.source as? Player
        else -> null
    }

    // ---- players ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) { if (refuse(event.player, at(event.block))) event.isCancelled = true }

    /** Also BlockMultiPlaceEvent (beds, doors), which shares this handler list. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val blocks = (event as? BlockMultiPlaceEvent)?.replacedBlockStates?.map { it.block } ?: listOf(event.block)
        if (blocks.any { refuse(event.player, at(it)) }) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (refuse(event.player, at(event.block)) || refuse(event.player, at(event.blockClicked), false)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        if (refuse(event.player, at(event.block)) || refuse(event.player, at(event.blockClicked), false)) event.isCancelled = true
    }

    @Suppress("DEPRECATION") // Material.isInteractable: no BlockType replacement on Paper 26.3 that reads a Material
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        when (event.action) {
            // Pressure plates, tripwire, farmland and turtle eggs.
            org.bukkit.event.block.Action.PHYSICAL -> if (refuse(event.player, at(block), false)) event.isCancelled = true
            org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK -> {
                val estate = at(block) ?: return
                if (allowed(estate, event.player)) return
                // Only the block is refused: eating, bows and ability items keep working inside someone else's estate.
                event.setUseInteractedBlock(Event.Result.DENY)
                val item = event.item?.type
                val tool = item != null && item in WORLD_TOOLS
                if (tool) event.setUseItemInHand(Event.Result.DENY)
                if (tool || block.type.isInteractable) refuse(event.player, estate)
            }
            org.bukkit.event.block.Action.LEFT_CLICK_BLOCK -> {
                val face = event.blockFace
                val fire = block.getRelative(face).takeIf { it.type == Material.FIRE || it.type == Material.SOUL_FIRE }
                if (fire != null && refuse(event.player, at(fire))) event.isCancelled = true
                else if (block.type == Material.DRAGON_EGG && refuse(event.player, at(block))) event.isCancelled = true
            }
            else -> {}
        }
    }

    /** Animals, villagers, item frames, armor stands, vehicles and storage carts; monsters and players are not protected. */
    private fun protectedEntity(entity: Entity): Boolean = entity !is Player && entity !is Enemy

    /** Also PlayerInteractAtEntityEvent, which shares this handler list. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (protectedEntity(event.rightClicked) && refuse(event.player, at(event.rightClicked.location))) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onArmorStand(event: PlayerArmorStandManipulateEvent) {
        if (refuse(event.player, at(event.rightClicked.location))) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onDamageEntity(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        if (!protectedEntity(victim)) return
        val estate = at(victim.location) ?: return
        val damager = event.damager
        val player = playerOf(damager)
        when {
            player != null -> if (refuse(player, estate)) event.isCancelled = true
            // Blasts from outside the estate: creepers, cannons, crystals.
            damager is Explosive || damager is Creeper || damager is ExplosiveMinecart || damager is EnderCrystal ->
                if (at(damager.origin ?: damager.location)?.id != estate.id) event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onVehicleDamage(event: VehicleDamageEvent) {
        val player = playerOf(event.attacker) ?: return
        if (refuse(player, at(event.vehicle.location))) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val player = playerOf(event.attacker) ?: return
        if (refuse(player, at(event.vehicle.location))) event.isCancelled = true
    }

    /** Also HangingBreakByEntityEvent, which shares this handler list. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakEvent) {
        val estate = at(event.entity.location) ?: return
        val player = (event as? HangingBreakByEntityEvent)?.remover?.let(::playerOf)
        if (player != null) { if (refuse(player, estate)) event.isCancelled = true }
        else if (event.cause == HangingBreakEvent.RemoveCause.EXPLOSION || event.cause == HangingBreakEvent.RemoveCause.ENTITY) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        val player = event.player ?: return
        if (refuse(player, at(event.entity.location))) event.isCancelled = true
    }

    /** Armor stands, boats, minecarts and end crystals. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityPlace(event: EntityPlaceEvent) {
        val player = event.player ?: return
        if (refuse(player, at(event.block)) || refuse(player, at(event.entity.location), false)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSign(event: SignChangeEvent) { if (refuse(event.player, at(event.block))) event.isCancelled = true }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onFertilize(event: BlockFertilizeEvent) {
        val player = event.player
        val origin = at(event.block)
        event.blocks.removeIf { state ->
            val estate = at(state.block)
            if (player != null) !allowed(estate, player) else !EstateRules.spreadAllowed(origin, estate)
        }
    }

    // ---- the world ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!estates.hasWorld(event.location.world.uid)) return
        val entity = event.entity
        val player = playerOf(entity)
        // TNT, TNT minecarts and crystals count as coming from where they were spawned (Paper keeps the origin).
        val origin = when (entity) {
            is TNTPrimed, is ExplosiveMinecart, is EnderCrystal -> at(entity.origin ?: entity.location)?.id
            else -> null
        }
        event.blockList().removeIf { !EstateRules.explosionBreaks(at(it), { estate -> allowed(estate, player) }, origin) }
    }

    /** Beds and respawn anchors. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!estates.hasWorld(event.block.world.uid)) return
        val origin = at(event.block)?.id
        event.blockList().removeIf { !EstateRules.explosionBreaks(at(it), { false }, origin) }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onTntPrime(event: TNTPrimeEvent) {
        val estate = at(event.block) ?: return
        if (event.cause == TNTPrimeEvent.PrimeCause.REDSTONE) return
        val source = event.primingEntity
        val player = playerOf(source)
        val origin = event.primingBlock?.let(::at) ?: source?.let { at(it.origin ?: it.location) }
        if (origin?.id == estate.id) return
        if (player != null) { if (refuse(player, estate)) event.isCancelled = true }
        else event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (!estates.hasWorld(event.block.world.uid)) return
        val direction = event.direction
        val touched = event.blocks.flatMap { listOf(at(it), at(it.getRelative(direction))) } + at(event.block.getRelative(direction))
        if (!EstateRules.pistonAllowed(at(event.block), touched)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!estates.hasWorld(event.block.world.uid)) return
        val direction = event.direction
        val touched = event.blocks.flatMap { listOf(at(it), at(it.getRelative(direction))) }
        if (!EstateRules.pistonAllowed(at(event.block), touched)) event.isCancelled = true
    }

    /** Water and lava (and dragon egg teleports). The most frequent event here: one lookup when the target is in the wild. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onFlow(event: BlockFromToEvent) {
        val to = at(event.toBlock) ?: return
        if (!EstateRules.spreadAllowed(at(event.block), to)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSpread(event: BlockSpreadEvent) {
        val type = event.newState.type
        if (type != Material.FIRE && type != Material.SOUL_FIRE) return
        val to = at(event.block) ?: return
        if (!EstateRules.spreadAllowed(at(event.source), to)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) {
        val estate = at(event.block) ?: return
        if (!EstateRules.spreadAllowed(event.ignitingBlock?.let(::at), estate)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onIgnite(event: BlockIgniteEvent) {
        val estate = at(event.block) ?: return
        val player = event.player ?: playerOf(event.ignitingEntity)
        if (player != null) { if (refuse(player, estate)) event.isCancelled = true; return }
        if (event.ignitingBlock?.let(::at)?.id != estate.id) event.isCancelled = true
    }

    /** Buckets, fire charges, TNT and the like shot from a dispenser outside the estate. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onDispense(event: BlockDispenseEvent) {
        val facing = (event.block.blockData as? Directional)?.facing ?: return
        val target = at(event.block.getRelative(facing)) ?: return
        if (at(event.block)?.id != target.id) event.isCancelled = true
    }

    /** Also EntityBreakDoorEvent (zombies), which shares this handler list. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        val block = event.block
        val estate = at(block)
        val entity = event.entity
        when (entity) {
            is Player -> if (refuse(entity, estate)) event.isCancelled = true
            is FallingBlock -> if (event.to.isAir) {
                // A block starting to fall: not when what held it up is outside the estate.
                if (!EstateRules.fallAllowed(estate, at(block.getRelative(org.bukkit.block.BlockFace.DOWN)))) event.isCancelled = true
            } else if (estate != null && at(entity.origin)?.id != estate.id) event.isCancelled = true // sand or anvils dropped in from outside
            is Projectile -> if (estate != null) {
                val shooter = entity.shooter
                val ok = when (shooter) {
                    is Player -> allowed(estate, shooter)
                    is BlockProjectileSource -> at(shooter.block)?.id == estate.id
                    else -> false
                }
                if (!ok) event.isCancelled = true
            }
            is Enderman, is Wither, is WitherSkull, is EnderDragon, is Ravager, is Silverfish, is Zombie ->
                if (estate != null) event.isCancelled = true
            else -> {}
        }
    }

    /** Arrows on buttons and pressure plates from outside the estate. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityInteract(event: EntityInteractEvent) {
        val projectile = event.entity as? Projectile ?: return
        val estate = at(event.block) ?: return
        val ok = when (val shooter = projectile.shooter) {
            is Player -> allowed(estate, shooter)
            is BlockProjectileSource -> at(shooter.block)?.id == estate.id
            else -> false
        }
        if (!ok) event.isCancelled = true
    }

    /** Hoppers and hopper minecarts outside an estate cannot pull items out of its containers. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onMoveItem(event: InventoryMoveItemEvent) {
        val source = at(event.source.location) ?: return
        val destination = event.destination.location ?: return
        if (at(destination)?.id != source.id) event.isCancelled = true
    }

    /** Trees and huge mushrooms growing into an estate from outside. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onGrow(event: StructureGrowEvent) {
        if (!estates.hasWorld(event.world.uid)) return
        val player = event.player
        val origin = at(event.location)
        event.blocks.removeIf { state ->
            val estate = at(state.block)
            if (player != null) !allowed(estate, player) else !EstateRules.spreadAllowed(origin, estate)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) { warned.remove(event.player.uniqueId) }

    companion object {
        const val BYPASS = "limpi.estate.admin.bypass"

        /** Items that change a block or place something when used on it. Read lazily: Material is a server registry. */
        private val WORLD_TOOLS: Set<Material> by lazy {
            EnumSet.noneOf(Material::class.java).apply {
                Material.entries.filter { it.isItem }.forEach { type ->
                    val name = type.name
                    if (name.endsWith("_BUCKET") || name.endsWith("_HOE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") ||
                        name.endsWith("_SPAWN_EGG") || name.endsWith("_DYE") || name.endsWith("_BOAT") || name.endsWith("_RAFT") ||
                        name.contains("MINECART") || name.endsWith("ITEM_FRAME")) add(type)
                }
                addAll(listOf(Material.BUCKET, Material.BONE_MEAL, Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.SHEARS,
                    Material.BRUSH, Material.HONEYCOMB, Material.ARMOR_STAND, Material.PAINTING, Material.END_CRYSTAL, Material.LEAD,
                    Material.INK_SAC, Material.GLOW_INK_SAC, Material.POTION))
            }
        }
    }
}
