package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.log
import kotlin.random.Random

object EntityThrower : Ability(
    grade = Grade.EPIC,
    element = Element.NONE,
    displayName = Component.text("괴력투척", NamedTextColor.DARK_GREEN),
    codeName = "entity_thrower",
    material = Material.OMINOUS_BOTTLE,
    description = listOf(
        Component.text("양손으로 엔티티를 머리 위로 들어올리고,", NamedTextColor.WHITE),
        Component.text("집어던집니다.", NamedTextColor.WHITE)
    ),
    needFile = true
) {


    override val details: List<Component> by lazy {
        listOf(
            Component.text("양손을 비우고 엔티티를 우클릭하여 해당 엔티티를 머리 위에 붙잡습니다.", NamedTextColor.WHITE),
            Component.text("다시 우클릭하여 바라보는 방향으로 집어던집니다.", NamedTextColor.WHITE),
            Component.text("던지는 엔티티가 작을수록 더 멀리 던져집니다.", NamedTextColor.WHITE),
            Component.text("던져진 엔티티가 다른 엔티티와 부딪히면 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("collisionDamage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 줍니다.", NamedTextColor.WHITE)),
            Component.text("일부 엔티티는 잡고 있거나 부딪힐 때 상호작용이 존재합니다.", NamedTextColor.WHITE),
            Component.text("붙잡은 엔티티와 거리가 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("forceDropDistance")}", NamedTextColor.GREEN))
                .append(Component.text("블록 이상 멀어지면 놓칩니다.", NamedTextColor.WHITE)),
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("heldHeight", 0.5)
            config?.set("forceDropDistance", 5.0)
            config?.set("baseThrowVelocity", 1.0)
            config?.set("maxThrowVelocity", 3.0)
            config?.set("collisionDamage", 6.0)
            config?.set("phantomRandomMovementIntensity", 0.01)
            config?.set("tntAndCreeperExplodeImmediately", true)
            config?.set("enderManTeleportProbability", 0.01)
        }
        saveConfig()
    }


    /**
     * 엔티티를 들고 있는 상태에서 플레이어가 우클릭할 때 사용되는 아이템입니다.
     * 이 아이템은 양손을 비우고 엔티티를 들고 있을 때만 사용됩니다.
     */
    fun getRestrictHand(): ItemStack {
        val restrictHand = ItemStack(Material.COMMAND_BLOCK, 1)
        val meta: ItemMeta = restrictHand.itemMeta
        meta.itemName(Component.text("엔티티 들고 있음!", NamedTextColor.YELLOW))
        meta.itemModel = NamespacedKey("minecraft", "air")
        meta.isHideTooltip = true
        restrictHand.itemMeta = meta
        return restrictHand
    }

    /**
     * 플레이어의 인벤토리에서 모든 restrictHand 아이템을 제거합니다.
     */
    fun clearRestrictHand(player: Player) {
        player.inventory.contents.forEachIndexed { index, item ->
            if (item != null && item.isSimilar(getRestrictHand())) {
                player.inventory.setItem(index, ItemStack(Material.AIR))
            }
        }
    }


    val heldEntity: MutableMap<Player, Entity> = mutableMapOf()
    @EventHandler
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (player.ability != this) return

        // 플레이어가 양손을 비우지 않은 경우
        if (player.inventory.itemInMainHand.type != Material.AIR ||
            player.inventory.itemInOffHand.type != Material.AIR) {
            return
        }

        // 양손 모두 비워져 있으면 이벤트가 두 번 호출됨, 이에 따라 하나만 호출하게 리턴
        if (event.hand != EquipmentSlot.HAND) return

        val targetEntity = event.rightClicked

        //나를 잡고 있는 플레이어일 경우
        if (targetEntity is Player && heldEntity.containsKey(targetEntity) && heldEntity[targetEntity] == player) {
            player.sendActionBar(Component.text("자신을 붙잡고 있는 상대는 붙잡을 수 없습니다!", NamedTextColor.RED))
            return
        }

        player.inventory.setItemInMainHand(getRestrictHand())
        player.inventory.setItemInOffHand(getRestrictHand())
        player.world.playSound(player.location, Sound.ITEM_BUNDLE_INSERT, 1f, 0.5f)
        event.isCancelled = true

        // 들고 있는 엔티티 없으면 붙잡기 시작
        if (!heldEntity.containsKey(player)) {
            heldEntity[player] = targetEntity

            object: BukkitRunnable() {
                override fun run() {
                    // 플레이어가 죽었거나 엔티티를 놓았을 때 반복문 종료
                    if (!heldEntity.containsKey(player) || heldEntity[player] != targetEntity || player.isDead || !player.isOnline) {
                        clearRestrictHand(player)
                        player.world.playSound(player.location, Sound.ITEM_BUNDLE_INSERT_FAIL, 0.7f, 0.5f)
                        cancel()
                        return
                    }
                    // 플레이어가 엔티티를 들고 있는 상태에서 엔티티가 유효하지 않거나 죽었을 때
                    if (targetEntity.isDead || !targetEntity.isValid) {
                        heldEntity.remove(player)
                        clearRestrictHand(player)
                        player.world.playSound(player.location, Sound.ITEM_BUNDLE_INSERT_FAIL, 0.7f, 0.5f)
                        cancel()
                        return
                    }
                    // 플레이어가 너무 멀리 떨어지면 엔티티를 놓아줌
                    if (targetEntity.location.distance(Location(player.world, player.boundingBox.centerX, player.boundingBox.maxY + config!!.getDouble("heldHeight"), player.boundingBox.centerZ))> config!!.getDouble("forceDropDistance")) {
                        heldEntity.remove(player)
                        clearRestrictHand(player)
                        player.world.playSound(player.location, Sound.ITEM_BUNDLE_INSERT_FAIL, 0.7f, 0.5f)
                        cancel()
                        return
                    }
                    // 플레이어의 양손이 restrictHand가 아니면 엔티티를 놓아줌
                    if (!player.inventory.itemInMainHand.isSimilar(getRestrictHand()) ||
                        !player.inventory.itemInOffHand.isSimilar(getRestrictHand())) {
                        heldEntity.remove(player)
                        clearRestrictHand(player)
                        player.world.playSound(player.location, Sound.ITEM_BUNDLE_INSERT_FAIL, 0.7f, 0.5f)
                        cancel()
                        return
                    }


                    // 엔더드래곤, 가스트의 경우, 플레이어가 매달리기
                    if (targetEntity is EnderDragon || targetEntity is Ghast) {
                        val followingLocation = Location(player.world, targetEntity.boundingBox.center.x, targetEntity.boundingBox.minY - player.boundingBox.height - config!!.getDouble("heldHeight"), targetEntity.boundingBox.center.z)
                        player.velocity = followingLocation.subtract(player.location).toVector().multiply(0.5)
                    }
                    else {
                        val heldLocation = Location(player.world, player.boundingBox.centerX, player.boundingBox.maxY + config!!.getDouble("heldHeight"), player.boundingBox.centerZ)
                        targetEntity.velocity = heldLocation.subtract(targetEntity.location).toVector().multiply(0.5)

                        // 닭을 붙잡고 있을 경우, 느린낙하 효과 적용
                        if (targetEntity is Chicken) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 2, 0, true, false, true))
                        }

                        // 팬텀을 붙잡고 있을 경우, 느린낙하와 무작위 이동 적용
                        if (targetEntity is Phantom) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 2, 0, true, false, true))
                            val randomVector = Vector(Random.nextDouble() - 0.5, Random.nextDouble() - 0.5, Random.nextDouble() - 0.5).multiply(config!!.getDouble("phantomRandomMovementIntensity"))
                            player.velocity = player.velocity.add(randomVector)
                        }

                        // 크리퍼를 붙잡을 경우, 크리퍼 점화
                        if (targetEntity is Creeper) {
                            targetEntity.isIgnited = true
                        }

                        // 엔더맨을 붙잡을 경우, 확률적으로 텔레포트
                        if (targetEntity is Enderman) {
                            if (Random.nextDouble() < config!!.getDouble("enderManTeleportProbability")) {
                                for (i in 0 until 100) { // 최대 100번 시도
                                    val teleportDelta = Location(
                                        player.world,
                                        Random.nextDouble(-10.0, 10.0),
                                        Random.nextDouble(-10.0, 10.0),
                                        Random.nextDouble(-10.0, 10.0)
                                    )
                                    // 올바른지 체크
                                    if (player.location.clone().add(teleportDelta).block.type.isCollidable) continue
                                    if (!player.location.clone().add(teleportDelta).subtract(0.0, 1.0, 0.0).block.type.isCollidable) continue
                                    if (player.location.clone().add(teleportDelta).add(0.0, 1.0, 0.0).block.type.isCollidable) continue
                                    if (player.location.clone().add(teleportDelta).add(0.0, 2.0, 0.0).block.type.isCollidable) continue
                                    val dust = Particle.DustOptions(Color.FUCHSIA, 1f)
                                    player.world.spawnParticle(Particle.DUST, player.location.add(0.0, 3.0, 0.0), 30, 0.3, 1.0, 0.3, 0.1, dust)
                                    player.teleport(player.location.add(teleportDelta))
                                    targetEntity.teleport(targetEntity.location.add(teleportDelta))
                                    player.world.spawnParticle(Particle.DUST, player.location.add(0.0, 3.0, 0.0), 30, 0.3, 1.0, 0.3, 0.1, dust)
                                    player.world.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
                                }
                            }
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }
    }


    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this) return

        if (event.action == Action.LEFT_CLICK_AIR ||
            event.action == Action.LEFT_CLICK_BLOCK ||
            event.action == Action.PHYSICAL) return

        if (!heldEntity.containsKey(player)) return
        if (event.hand != EquipmentSlot.HAND) return

        event.isCancelled = true

        val targetEntity = heldEntity[player]!!
        val baseThrowVelocity = config!!.getDouble("baseThrowVelocity")
        val sizeBonus = log(1.0 / (targetEntity.boundingBox.volume) + 1, 2.5)
        val maxVelocity = config!!.getDouble("maxThrowVelocity")
        var velocity = (baseThrowVelocity * sizeBonus).coerceAtMost(maxVelocity)
        // TNT, 크리퍼, 팬텀은 속도 2배
        if (targetEntity is TNTPrimed || targetEntity is Creeper) {
            velocity *= 2
        }

        // 팬텀은 1초 간 방향을 유지하며 날아감
        if (targetEntity is Phantom) {
            object: BukkitRunnable() {
                var timer = 0.0
                override fun run() {
                    if (timer++ >= 20) { // 1초 후에 반복문 종료
                        cancel()
                        return
                    }
                    targetEntity.velocity = player.location.direction.multiply(velocity * (1 - timer / 40.0))
                    targetEntity.location.direction = player.location.direction
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }

        targetEntity.velocity = player.location.direction.multiply(velocity)
        player.world.playSound(player.location, Sound.ENTITY_BREEZE_SHOOT, 0.5f, velocity.toFloat())
        heldEntity.remove(player)
        clearRestrictHand(player)

        // 이후 엔티티가 다른 엔티티와 충돌했을 때 피해를 주는 로직
        object: BukkitRunnable() {
            var timer = 0
            override fun run() {
                if (timer++ >= 60 && targetEntity.velocity.length() <= 0.7) { // 60틱(3초) 후에 엔티티가 빠르지 않다면 종료
                    cancel()
                    return
                }
                if (!targetEntity.isValid || targetEntity.isDead || targetEntity.velocity.length() <= 0.3) {
                    cancel()
                    return
                }

                // 엔티티가 다른 엔티티와 충돌했을 때
                val nearbyEntities = targetEntity.getNearbyEntities(6.0, 6.0, 6.0)
                for (entity in nearbyEntities) {
                    if (entity !is Damageable || entity == player || entity == targetEntity) continue
                    if (!entity.boundingBox.overlaps(targetEntity.boundingBox)) continue

                    // TNT, 크리퍼의 경우, 즉시 폭발
                    if (config!!.getBoolean("tntAndCreeperExplodeImmediately")) {
                        if (targetEntity is TNTPrimed) {
                            targetEntity.fuseTicks = 0
                        }
                        if (targetEntity is Creeper) {
                            targetEntity.explode()
                        }
                    }

                    // 팬텀의 경우, 1초간 부딪힌 적을 붙잡고 하늘로 솟아오름
                    if (targetEntity is Phantom) {
                        object: BukkitRunnable() {
                            var timer1 = 0.0
                            override fun run() {
                                if (timer1++ >= 20) {
                                    cancel() // 1초 후에 반복문 종료
                                    return
                                }

                                targetEntity.velocity = (Vector(0.0, timer1 / 20.0, 0.0))
                                val hangingPoint = Location(
                                    targetEntity.world,
                                    targetEntity.boundingBox.centerX,
                                    targetEntity.boundingBox.minY - entity.boundingBox.height - config!!.getDouble("heldHeight"),
                                    targetEntity.boundingBox.centerZ
                                )
                                entity.velocity = hangingPoint.subtract(entity.location).toVector().multiply(0.5)
                            }
                        }.runTaskTimer(plugin, 0L, 1L)
                    }


                    // 데미지 주기
                    val damage = config!!.getDouble("collisionDamage")
                    entity.damage(damage, player)
                    if (targetEntity is Damageable) {
                        targetEntity.damage(damage, player)
                    }

                    // 부딪히고 넉백
                    val vector = targetEntity.velocity.normalize()
                    entity.velocity = vector.multiply(0.2)
                    targetEntity.velocity = vector.multiply(-0.2)
                    player.world.playSound(entity.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
                    cancel() // 충돌 후 반복문 종료
                    return
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }


    //이하는 restrictHand 아이템 제한

    // 플레이어 죽었을 때 제거
    @EventHandler
    fun onDropItem(event: ItemSpawnEvent) {
        if (event.entity.itemStack.isSimilar(getRestrictHand())) {
            event.entity.remove()
            return
        }
    }

    //Q키로 아이템 버리기 제한
    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (event.itemDrop.itemStack.isSimilar(getRestrictHand())) {
            event.isCancelled = true
        }
    }

    //인벤토리 내 클릭, 이동 금지
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        if (!item.isSimilar(getRestrictHand())) return

        event.isCancelled = true
    }

    //안잡고 있는데 움직이면 삭제(왜 움직일때냐면 이거 하나때문에 bukkitrunnable 쓰기는 너무 귀찮아서)
    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (heldEntity.containsKey(player)) return
        clearRestrictHand(player)
    }
}