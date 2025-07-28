package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.abilities.HarmonySword.sword
import com.github.lupi13.limpi.quests.LineBetweenSwordAndArrow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import kotlin.math.pow
import kotlin.random.Random

object Transcendence : Ability(
    grade = Grade.LEGENDARY,
    element = Element.NONE,
    displayName = Component.text("통달", NamedTextColor.LIGHT_PURPLE),
    codeName = "transcendence",
    material = Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
    description = listOf(
        Component.text("검과 화살로 피해를 누적하면", NamedTextColor.WHITE),
        Component.text("잠시동안 두 무기가 조화를 이루고", NamedTextColor.WHITE),
        Component.text("강력한 효과를 얻습니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(LineBetweenSwordAndArrow.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("검으로 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("sword_required_damage")}", NamedTextColor.GREEN))
                .append(Component.text(", 화살로 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("arrow_required_damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 누적시켜 ", NamedTextColor.WHITE))
                .append(Component.text("통달", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" 게이지를 채웁니다.", NamedTextColor.WHITE)),
            Component.text("통달", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" 게이지가 다 찼을 때, 검을 들고 ", NamedTextColor.WHITE))
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 ", NamedTextColor.WHITE))
                .append(Component.text("통달 상태", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("에 돌입합니다.", NamedTextColor.WHITE)),
            Component.text("\n통달 상태", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" (", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("duration_ticks") / 20.0}", NamedTextColor.GREEN))
                .append(Component.text("초)", NamedTextColor.WHITE)),
            Component.text("  -검과 화살의 피해가 각각 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("sword_damage_amplifier")}", NamedTextColor.GREEN))
                .append(Component.text("배, ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("arrow_damage_amplifier")}", NamedTextColor.GREEN))
                .append(Component.text("배 증가합니다.", NamedTextColor.WHITE)),
            Component.text("  -", NamedTextColor.WHITE)
                .append(Component.text("저항 ${config!!.getInt("resistance_level")}", NamedTextColor.BLUE))
                .append(Component.text(", ", NamedTextColor.WHITE))
                .append(Component.text("신속 ${config!!.getInt("speed_level")}", NamedTextColor.BLUE))
                .append(Component.text(" 효과를 얻습니다.", NamedTextColor.WHITE)),
            Component.text("  -검을 휘두를 때 무작위 편차를 가진 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getInt("additional_arrow_count")}", NamedTextColor.GREEN))
                .append(Component.text("개의 화살을 발사합니다.", NamedTextColor.WHITE)),
            Component.text("  -화살에 무한한 ", NamedTextColor.WHITE)
                .append(Component.text("관통", NamedTextColor.AQUA))
                .append(Component.text(" 효과가 부여됩니다.", NamedTextColor.WHITE)),
            Component.text("  -화살이 날아가는 동안 검격이 발생하여 주변의 ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("additional_sword_damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 추가 피해를 줍니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("sword_required_damage", 30.0)
            config?.set("arrow_required_damage", 30.0)
            config?.set("sword_damage_amplifier", 1.3)
            config?.set("arrow_damage_amplifier", 1.3)
            config?.set("duration_ticks", 200)
            config?.set("resistance_level", 1)
            config?.set("speed_level", 1)
            config?.set("additional_arrow_count", 3)
            config?.set("additional_arrow_velocity", 2.0)
            config?.set("additional_arrow_spread", 30.0)
            config?.set("additional_arrow_cooldown", 10)
            config?.set("additional_sword_damage", 2.0)
            config?.set("additional_sword_range", 3.0)
            config?.set("additional_sword_ticks", 3)
        }
        saveConfig()
    }


    var swordGauge: MutableMap<Player, Double> = mutableMapOf()
    var arrowGauge: MutableMap<Player, Double> = mutableMapOf()

    var transcending: MutableMap<Player, Boolean> = mutableMapOf()


    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        var player: Player? = null
        if (event.damager !is Player && event.damager !is AbstractArrow) return

        // 플레이어
        if (event.damager is Player) {
            player = event.damager as Player
            if (player.ability != this) return

            // 플레이어가 검을 들고 있는지 확인
            if (!sword.contains(player.inventory.itemInMainHand.type)) return


            // 게이지 채우기
            if (!transcending.contains(player)) {
                swordGauge[player] = ((swordGauge[player] ?: 0.0) + event.finalDamage).coerceAtMost(config!!.getDouble("sword_required_damage"))
            }

            // 통달상태
            else {
                val entity = event.entity as? LivingEntity ?: return
                event.damage *= config!!.getDouble("sword_damage_amplifier")
                player.playSound(
                    player.location,
                    Sound.ENTITY_WARDEN_DEATH,
                    0.7f, 2.0f
                )
                entity.world.spawnParticle(
                    Particle.DRAGON_BREATH,
                    entity.boundingBox.center.toLocation(entity.world),
                    30, 0.1, 0.1, 0.1, 0.2
                )
                if (player.getCooldown(player.inventory.itemInMainHand.type) == 0) {
                    shootArrow(player, config!!.getInt("additional_arrow_count"))
                }
            }
        }


        // 화살
        if (event.damager is AbstractArrow) {
            player = (event.damager as AbstractArrow).shooter as? Player ?: return
            if (player.ability != this) return


            if (!transcending.contains(player)) {
                arrowGauge[player] = ((arrowGauge[player] ?: 0.0) + event.finalDamage).coerceAtMost(config!!.getDouble("arrow_required_damage"))
            }

            // 통달상태
            else {
                event.damage *= config!!.getDouble("arrow_damage_amplifier")
                player.playSound(
                    player.location,
                    Sound.ENTITY_WARDEN_DEATH,
                    0.7f, 2.0f
                )
                event.damager.world.spawnParticle(
                    Particle.DRAGON_BREATH,
                    event.damager.location,
                    30, 0.1, 0.1, 0.1, 0.2
                )
            }
        }

        // 게이지 프린트
        if (!transcending.contains(player)) {
            var actionBar = Component.text("TRANSCENDENCE ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
            for (i in 0 until ((swordGauge[player] ?: 0.0) / config!!.getDouble("sword_required_damage") * 30.0).toInt()) {
                actionBar = actionBar.append(Component.text("|", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
            }
            for (i in 0 until (30 - ((swordGauge[player] ?: 0.0) / config!!.getDouble("sword_required_damage") * 30.0).toInt())) {
                actionBar = actionBar.append(Component.text("|", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
            }

            actionBar = actionBar.append(Component.text("|", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))

            for (i in 0 until (30 - ((arrowGauge[player] ?: 0.0) / config!!.getDouble("arrow_required_damage") * 30.0).toInt())) {
                actionBar = actionBar.append(Component.text("|", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
            }
            for (i in 0 until ((arrowGauge[player] ?: 0.0) / config!!.getDouble("arrow_required_damage") * 30.0).toInt()) {
                actionBar = actionBar.append(Component.text("|", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, false))
            }

            player?.sendActionBar(actionBar)
        }
    }

    // F키로 통달 상태로 전환
    @EventHandler
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this) return

        if (!sword.contains(player.inventory.itemInMainHand.type) &&
            !sword.contains(player.inventory.itemInOffHand.type)) return

        if (transcending.contains(player)) return
        if ((swordGauge[player] ?: 0.0) < config!!.getDouble("sword_required_damage") ||
            (arrowGauge[player] ?: 0.0) < config!!.getDouble("arrow_required_damage")) return

        event.isCancelled = true
        transcending[player] = false

        object: BukkitRunnable() {
            var ticks = 0
            val itemDisplay = player.world.spawn(
                player.eyeLocation
                    .add(player.location.direction.rotateAroundY(-90.0).multiply(0.15))
                    .add(player.location.direction.multiply(0.15)),
                ItemDisplay::class.java
            )

            init {
                itemDisplay.setItemStack(ItemStack(Material.ARROW))
                itemDisplay.transformation = Transformation(
                    Vector3f(0.0f, 0.0f, 0.0f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                itemDisplay.interpolationDelay = 0
                itemDisplay.interpolationDuration = 0
            }
            override fun run() {
                // 선딜레이 모션
                if (ticks <= 20) {
                    val angle = (Math.PI * (ticks * 4.0 / 20.0)).toFloat()
                    val scale = (ticks / 100.0).toFloat()
                    val x = (ticks.toDouble().pow(2.0) / 1800.0).toFloat()
                    val y = (0.07-((ticks - 15).toDouble().pow(2.0) / 500.0)).toFloat()

                    itemDisplay.transformation = Transformation(
                        Vector3f(x, y, 0f),
                        AxisAngle4f(0f, 0f, 0f, 0f),
                        Vector3f(scale, scale, scale),
                        AxisAngle4f(angle, 0f, 1f, 0f)
                    )
                    itemDisplay.interpolationDelay = 0
                    itemDisplay.interpolationDuration = 1

                    player.playSound(
                        player.location,
                        Sound.UI_TOAST_IN,
                        1.0f, 0.5f + (ticks / 20.0).toFloat() * 1.5f
                    )
                    player.playSound(
                        player.location,
                        Sound.UI_TOAST_OUT,
                        1.0f, 0.5f + (ticks / 20.0).toFloat() * 1.5f
                    )
                }

                if (ticks == 20) {
                    transcending[player] = true
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.RESISTANCE,
                        config!!.getInt("duration_ticks"),
                        config!!.getInt("resistance_level") - 1,
                        true, true, true)
                    )
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.SPEED,
                        config!!.getInt("duration_ticks"),
                        config!!.getInt("speed_level") - 1,
                        true, true, true)
                    )

                    player.playSound(
                        player.location,
                        Sound.BLOCK_AMETHYST_BLOCK_HIT,
                        1f, 1.7f
                    )
                    player.spawnParticle(
                        Particle.DRAGON_BREATH,
                        player.eyeLocation.add(player.location.direction.multiply(0.15)).add(player.location.direction.rotateAroundY(90.0).multiply(0.15)),
                        30, 0.01, 0.01, 0.01, 0.01
                    )

                    for (nearbyPlayer in player.world.getNearbyEntities(player.location, 100.0, 100.0, 100.0)
                        .filterIsInstance<Player>()) {
                        nearbyPlayer.sendMessage(
                            Functions.playerDisplay(player)
                                .append(Component.text("님이 통달 상태에 돌입합니다...", NamedTextColor.BLUE))
                        )
                    }
                }

                if (ticks <= 25) {
                    itemDisplay.teleport(player.eyeLocation
                        .add(player.location.direction.rotateAroundY(-90.0).multiply(0.15))
                        .add(player.location.direction.multiply(0.15)))
                }
                if (ticks == 25) {
                    itemDisplay.remove()
                }

                ticks++
                if ((ticks >= 21) && (ticks <= (config!!.getInt("duration_ticks") + 21))) {
                    // 게이지 감소
                    val color = TextColor.color(255, (120 - ticks * 3).coerceAtLeast(0), 255)
                    var actionBar = Component.text("TRANSCENDENCE ", color, TextDecoration.BOLD)
                    for (i in 0 until (61 - ((ticks - 21.0) * (61.0 / config!!.getInt("duration_ticks")))).toInt()) {
                        actionBar = actionBar.append(Component.text("|", color).decoration(TextDecoration.BOLD, false))
                    }
                    for (i in 0 until ((ticks - 21.0) * (61.0 / config!!.getInt("duration_ticks"))).toInt()) {
                        actionBar = actionBar.append(Component.text("|", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                    }
                    player.sendActionBar(actionBar)
                }
                if (ticks >= config!!.getInt("duration_ticks") + 21) {
                    transcending.remove(player)
                    swordGauge[player] = 0.0
                    arrowGauge[player] = 0.0
                    cancel()
                    return
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }


    fun shootArrow(player: Player, count: Int = 1) {
        for (i in 0 until count) {
            val arrow = player.world.spawnArrow(
                player.eyeLocation.add(player.location.direction.multiply(0.5)),
                player.location.direction,
                config!!.getDouble("additional_arrow_velocity").toFloat(),
                config!!.getDouble("additional_arrow_spread").toFloat()
            )
            arrow.pickupStatus = AbstractArrow.PickupStatus.CREATIVE_ONLY
            arrow.shooter = player
            arrow.pierceLevel = 126
            player.playSound(
                player.location,
                Sound.ENTITY_ARROW_SHOOT,
                1f, 1.5f + Random.nextFloat() * 0.5f
            )
        }
        for (s in sword) {
            player.setCooldown(s, config!!.getInt("additional_arrow_cooldown"))
        }
    }


    // 검격에 화살 발사
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if ((event.action != Action.LEFT_CLICK_AIR) && (event.action != Action.LEFT_CLICK_BLOCK)) return
        if (player.ability != this) return
        if (transcending[player] != true) return

        // 검을 들고 있지 않으면 리턴
        if (!sword.contains(player.inventory.itemInMainHand.type)) return
        if (player.getCooldown(player.inventory.itemInMainHand.type) != 0) return


        shootArrow(player, config!!.getInt("additional_arrow_count"))
    }


    // 화살 발사부터 검격 발생
    @EventHandler
    fun onArrowShoot(event: ProjectileLaunchEvent) {
        val arrow = event.entity as? AbstractArrow ?: return
        if (arrow.shooter !is Player) return
        val player = arrow.shooter as Player
        if (player.ability != this || transcending[player] != true) return


        arrow.pierceLevel = 126

        object: BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks % config!!.getInt("additional_sword_ticks") == 0) {
                    val damage = config!!.getDouble("additional_sword_damage")
                    val range = config!!.getDouble("additional_sword_range")

                    val entities = arrow.world.getNearbyEntities(arrow.location, range, range, range)
                        .filterIsInstance<Damageable>()
                        .filter { it != player && it != arrow.shooter && it != arrow && AbilityManager().isEnemy(player, it) }
                    for (entity in entities) {
                        entity.damage(damage, player)
                    }

                    arrow.world.spawnParticle(
                        Particle.SWEEP_ATTACK,
                        arrow.location,
                        10, range / 5.0, range / 5.0, range / 5.0, 0.0
                    )
                    arrow.world.playSound(
                        arrow.location,
                        Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                        1f, 1.0f + Random.nextFloat() * 0.5f
                    )
                }
                ticks++

                if (arrow.isInBlock || arrow.isOnGround || arrow.isDead || !arrow.isValid) {
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}