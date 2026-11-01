package com.github.lupi13.limpi.turret

import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Enemy
import org.bukkit.entity.Entity
import org.bukkit.entity.IronGolem
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.entity.Snowball
import org.bukkit.entity.Snowman
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * 설치된 포탑의 공통 동작(어느 포탑 능력이든 같음). 포탑 몸체는 AI를 끈 셜커(LivingEntity)라 몹이 노릴 수 있고,
 * 피해를 받아 체력 0이면 죽고, 회복 물약이 바닐라 규칙대로 듣습니다. 다른 능력에게는 갑옷 거치대처럼 장식(기계)으로 보입니다
 * ([isTurret]: 적 판정·능력 피해·붙잡기에서 제외).
 *
 * 포탑마다 주인의 [AbilityEffect]를 하나 열어 소유합니다. 주인의 퇴장·사망·월드 이동·능력 변경, 포탑의 죽음·청크 언로드에
 * 효과가 닫히며 셜커도 지워집니다. 셜커는 저장하지 않습니다(persistent=false).
 */
object TurretRuntime : Listener {
    /** 셜커 PDC: 주인 UUID. 표시가 있으면 포탑입니다. */
    val TURRET_KEY = NamespacedKey("limpi", "turret")
    /** 투사체 PDC: 쏜 포탑 UUID. */
    private val SHOT_KEY = NamespacedKey("limpi", "turret_shot")

    /** 주변 검색 주기(틱). */
    const val SEARCH_INTERVAL_TICKS = 10L
    private const val DECAY_INTERVAL_TICKS = 20L
    /** 셜커 껍데기를 열어 둔 정도. 닫혀 있으면 방어 +20이고 화살을 튕겨 내므로 조금 엽니다. */
    private const val PEEK = 0.3f
    /** 투사체가 출발하는 중심에서의 거리. 셜커 판정 상자(반폭 0.5) 밖이어야 자기 몸에 맞지 않습니다. */
    private const val START_OFFSET = 0.8

    class PlacedTurret internal constructor(
        val entity: Shulker,
        val owner: UUID,
        val kind: TurretKind,
        val stats: TurretStats,
        /** 피해·기록에 쓰는 능력 코드. */
        val ability: String,
        val effect: AbilityEffect,
    ) {
        var lastShot: Int = Int.MIN_VALUE / 2
        var lastSearch: Int = Int.MIN_VALUE / 2
        /** 마지막 검색에서 고른 표적. 쏠 때마다 아직 쏠 수 있는지 다시 확인합니다. */
        var target: LivingEntity? = null
        val id: UUID get() = entity.uniqueId
        /** 투사체가 나가는 중심점. */
        fun center(): Location = entity.location.add(0.0, 0.5, 0.0)
    }

    /** 날아가는 투사체에서 쏜 포탑을 찾지 못할 때(포탑이 먼저 사라짐)를 위해 필요한 값을 따로 둡니다. */
    class Shot(val turret: UUID, val kind: TurretKind, val ability: String, val damage: Double)

    private val turrets = HashMap<UUID, PlacedTurret>()
    private val shots = HashMap<UUID, Shot>()
    private val locks = TargetLocks()

    fun isTurret(entity: Entity?): Boolean = entity != null && entity.persistentDataContainer.has(TURRET_KEY, PersistentDataType.STRING)

    /** 포탑이면 주인 UUID, 아니면 null. */
    fun ownerOf(entity: Entity?): UUID? {
        val raw = entity?.persistentDataContainer?.get(TURRET_KEY, PersistentDataType.STRING) ?: return null
        return try { UUID.fromString(raw) } catch (_: IllegalArgumentException) { null }
    }

    fun turretsOf(owner: UUID): List<PlacedTurret> = turrets.values.filter { it.owner == owner }

    /** 포탑을 세울 수 있는 칸: 비었거나 풀처럼 대체 가능한 칸이고, 살아 있는 엔티티와 겹치지 않음. */
    fun spaceFree(space: Block): Boolean {
        if (!space.type.isAir && !space.isReplaceable) return false
        return space.world.getNearbyEntities(org.bukkit.util.BoundingBox.of(space)).none { it is LivingEntity && !it.isDead }
    }

    /**
     * [space] 칸에 [attachedTo] 면으로 붙은 포탑을 세웁니다. 다른 플러그인이 소환을 막으면 null(아무것도 소비하지 않게).
     */
    fun place(owner: Player, ability: String, kind: TurretKind, stats: TurretStats, space: Block, attachedTo: BlockFace): PlacedTurret? {
        val location = space.location.add(0.5, 0.0, 0.5)
        val entity = space.world.spawn(location, Shulker::class.java) { shulker ->
            shulker.setAI(false)
            shulker.isPersistent = false
            shulker.removeWhenFarAway = false
            shulker.isSilent = true
            shulker.attachedFace = attachedTo
            shulker.color = kind.color
            shulker.peek = PEEK
            shulker.getAttribute(Attribute.MAX_HEALTH)?.baseValue = stats.maxHealth
            shulker.health = stats.maxHealth
            shulker.persistentDataContainer.set(TURRET_KEY, PersistentDataType.STRING, owner.uniqueId.toString())
        }
        if (!entity.isValid) { entity.remove(); return null }
        val effect = try { AbilityRuntime.begin(owner, ability) } catch (failure: Exception) { entity.remove(); throw failure }
        val turret = PlacedTurret(entity, owner.uniqueId, kind, stats, ability, effect)
        turrets[turret.id] = turret
        effect.own {
            turrets.remove(turret.id)
            locks.release(turret.id)
            turret.target = null
            if (entity.isValid) { breakSound(entity.location); entity.remove() } // 죽지 않고 사라질 때(주인 퇴장 등)
        }
        effect.repeat(1, 1) { tick(turret) }
        if (stats.decayPerSecond > 0) effect.repeat(DECAY_INTERVAL_TICKS, DECAY_INTERVAL_TICKS) { decay(turret) }
        entity.world.playSound(location, Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.4f)
        return turret
    }

    private fun decay(turret: PlacedTurret) {
        val entity = turret.entity
        if (!entity.isValid || entity.isDead) { turret.effect.close(); return }
        entity.health = TurretDecay.next(entity.health, turret.stats.decayPerSecond) // 0이면 바닐라 사망(드롭 없음은 onDeath)
    }

    /**
     * 매 틱: 주변 검색은 [SEARCH_INTERVAL_TICKS]마다만(부하), 발사는 [TurretStats.fireIntervalTicks]마다 검색해 둔 표적이
     * 아직 쏠 수 있으면 쏩니다. 예전에는 검색 주기에 맞춰서만 쏴 발사 간격이 10틱보다 짧아지지 않았습니다.
     */
    private fun tick(turret: PlacedTurret) {
        val entity = turret.entity
        if (!entity.isValid || entity.isDead) { turret.effect.close(); return }
        val owner = Bukkit.getPlayer(turret.owner) ?: return
        val now = Bukkit.getCurrentTick()
        if (TurretSchedule.due(now, turret.lastSearch, SEARCH_INTERVAL_TICKS.toInt())) {
            turret.lastSearch = now
            holdLockedMobs(turret)
            turret.target = findTarget(turret, owner)
        }
        if (!TurretSchedule.due(now, turret.lastShot, turret.stats.fireIntervalTicks)) return
        val target = turret.target ?: return
        if (!shootable(turret, owner, target)) { turret.target = null; return }
        turret.lastShot = now
        turret.kind.fire(turret, target)
    }

    /** 반경 안에서 주인의 적이고 블록에 가리지 않은 가장 가까운 생명체. */
    private fun findTarget(turret: PlacedTurret, owner: Player): LivingEntity? {
        val center = turret.center()
        return center.world.getNearbyLivingEntities(center, turret.kind.range).asSequence()
            .filter { shootable(turret, owner, it) }
            .minByOrNull { it.location.distanceSquared(center) }
    }

    private fun shootable(turret: PlacedTurret, owner: Player, target: LivingEntity): Boolean {
        if (target === turret.entity || !target.isValid || target.isDead || isTurret(target)) return false
        if (target is Player && (target.gameMode == GameMode.SPECTATOR || target.gameMode == GameMode.CREATIVE)) return false
        val center = turret.center()
        if (target.world.uid != center.world.uid) return false
        val range = turret.kind.range
        if (target.boundingBox.center.toLocation(center.world).distanceSquared(center) > range * range) return false
        return AbilityManager.isEnemy(owner, target) && turret.entity.hasLineOfSight(target)
    }

    /** 기본 발사: 중력을 받는 눈덩이에 종류의 탄 겉모습을 씌워, 낙차를 계산한 각도로 쏩니다. 발사자는 주인입니다. */
    fun throwShot(turret: PlacedTurret, target: LivingEntity) {
        val owner = Bukkit.getPlayer(turret.owner) ?: return
        val from = turret.center()
        val aim = target.boundingBox.center.subtract(from.toVector())
        val horizontal = Vector(aim.x, 0.0, aim.z)
        val distance = horizontal.length()
        val pitch = Math.toRadians(TurretAim.pitch(distance, aim.y, turret.kind.speed))
        val flat = if (distance < 1.0e-6) Vector(0.0, 0.0, 0.0) else horizontal.normalize()
        val direction = flat.multiply(cos(pitch)).add(Vector(0.0, sin(pitch), 0.0)).normalize()
        val shot = Shot(turret.id, turret.kind, turret.ability, turret.stats.damage)
        // 붙어 있는 적(근접 공격 중인 좀비 등): 투사체가 적의 판정 상자 안에서 출발하면 맞지 않고 뒤로 빠져나가므로,
        // 첫 틱에 닿을 거리면 투사체 없이 바로 맞힙니다.
        val box = target.boundingBox.clone().expand(0.2)
        if (box.contains(from.toVector()) || box.rayTrace(from.toVector(), direction, START_OFFSET + turret.kind.speed) != null) {
            from.world.playSound(from, Sound.BLOCK_DISPENSER_LAUNCH, 0.6f, 1.6f)
            strike(shot, owner, target, target.boundingBox.center.toLocation(from.world))
            return
        }
        val start = from.clone().add(direction.clone().multiply(START_OFFSET))
        val ball = from.world.spawn(start, Snowball::class.java) { snowball ->
            snowball.shooter = owner
            snowball.item = ItemStack(turret.kind.ammo)
            snowball.velocity = direction.clone().multiply(turret.kind.speed)
            snowball.isPersistent = false
            snowball.persistentDataContainer.set(SHOT_KEY, PersistentDataType.STRING, turret.id.toString())
        }
        if (!ball.isValid) return
        shots[ball.uniqueId] = shot
        from.world.playSound(from, Sound.BLOCK_DISPENSER_LAUNCH, 0.6f, 1.6f)
    }

    // ---- 투사체 ----

    @EventHandler(priority = EventPriority.HIGH)
    fun onShotHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (!projectile.persistentDataContainer.has(SHOT_KEY, PersistentDataType.STRING)) return
        val hit = event.hitEntity
        if (hit == null) { shots.remove(projectile.uniqueId); return } // 블록: 바닐라처럼 깨짐
        event.isCancelled = true // 바닐라 눈덩이 타격(0 피해·넉백) 대신 능력 피해만
        val owner = projectile.shooter as? Player
        // 포탑·주인·주인의 적이 아닌 대상(아군 등)은 통과합니다.
        if (isTurret(hit) || owner == null || !owner.isOnline || hit !is org.bukkit.entity.Damageable ||
            !AbilityManager.isEnemy(owner, hit)) return
        val shot = shots.remove(projectile.uniqueId)
        val at = projectile.location
        projectile.remove()
        if (shot != null) strike(shot, owner, hit, at)
    }

    /** 명중: 탄 조각 파티클, 종류의 피해, 피해가 들어간 공격적인 몹은 이 포탑에 고정. */
    private fun strike(shot: Shot, owner: Player, target: org.bukkit.entity.Damageable, at: Location) {
        at.world.spawnParticle(Particle.ITEM, at, 6, 0.1, 0.1, 0.1, 0.05, ItemStack(shot.kind.ammo))
        val accepted = shot.kind.onHit(shot, owner, target)
        if (accepted && target is Mob && target is Enemy && !target.isDead) aggro(target, shot.turret)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onShotGone(event: com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent) {
        if (shots.isNotEmpty()) shots.remove(event.entity.uniqueId)
    }

    // ---- 몹 표적 ----

    /** 포탑에게 맞은 공격적인 몹은 그 포탑이 없어질 때까지 그 포탑만 노립니다(이미 다른 살아 있는 포탑에 고정이면 그대로). */
    private fun aggro(mob: Mob, turretId: UUID) {
        val turret = turrets[turretId] ?: return
        if (!turret.entity.isValid) return
        if (!locks.lock(mob.uniqueId, turretId) { id -> turrets[id]?.entity?.isValid == true }) return
        if (mob.target?.uniqueId != turretId) mob.target = turret.entity
    }

    /** 고정된 몹이 표적을 놓쳤으면(바닐라가 잊음 등) 다시 포탑을 노리게 합니다. */
    private fun holdLockedMobs(turret: PlacedTurret) {
        for (mobId in locks.mobsOf(turret.id)) {
            val mob = Bukkit.getEntity(mobId) as? Mob
            if (mob == null || !mob.isValid || mob.isDead || mob.world.uid != turret.entity.world.uid) { locks.forget(mobId); continue }
            if (mob.target?.uniqueId != turret.id) mob.target = turret.entity
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onTarget(event: EntityTargetEvent) {
        val target = event.target
        // 철 골렘·눈 골렘은 포탑을 노리지 않습니다.
        if ((event.entity is IronGolem || event.entity is Snowman) && isTurret(target)) { event.isCancelled = true; return }
        val mobId = event.entity.uniqueId
        val locked = locks.lockedTo(mobId) ?: return
        if (turrets[locked]?.entity?.isValid != true) { locks.forget(mobId); return }
        if (!locks.allows(mobId, target?.uniqueId)) event.isCancelled = true
    }

    // ---- 포탑 몸체 ----

    /** 셜커는 맞아서 체력이 절반 아래면 순간이동하고, 붙은 블록이 없어지면 자리를 옮깁니다. 포탑은 움직이지 않습니다. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onTeleport(event: EntityTeleportEvent) { if (isTurret(event.entity)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamaged(event: EntityDamageEvent) {
        val entity = event.entity
        if (event.finalDamage <= 0 || !isTurret(entity)) return
        entity.world.playSound(entity.location, Sound.ENTITY_IRON_GOLEM_HURT, 0.6f, 1.6f)
    }

    /** 셜커 껍데기·경험치를 남기지 않고, 셜커 죽는 소리 대신 기계가 부서지는 소리를 냅니다. */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (!isTurret(entity)) return
        event.drops.clear()
        event.droppedExp = 0
        event.setShouldPlayDeathSound(false)
        breakSound(entity.location)
        // 효과는 다음 검색 틱에 몸체가 없어진 것을 보고 닫힙니다(사망 처리 도중 엔티티를 지우지 않음).
    }

    private fun breakSound(at: Location) = at.world.playSound(at, Sound.BLOCK_VAULT_BREAK, 0.7f, 0.5f)

    /** 주인이 [ability]로 세운 포탑을 모두 닫습니다(효과가 닫히며 셜커도 지워짐). */
    fun closeOwner(owner: UUID, ability: String) = turretsOf(owner).filter { it.ability == ability }.forEach { it.effect.close() }

    /** [ability]로 세운 모든 포탑을 닫습니다(플러그인 종료). */
    fun closeAbility(ability: String) {
        turrets.values.filter { it.ability == ability }.forEach { it.effect.close() }
        if (turrets.isEmpty()) { shots.clear(); locks.clear() }
    }

    fun closeAll() {
        turrets.values.toList().forEach { it.effect.close() }
        turrets.clear(); shots.clear(); locks.clear()
    }
}
