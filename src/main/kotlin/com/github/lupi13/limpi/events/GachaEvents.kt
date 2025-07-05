package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.Grade
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import kotlin.random.Random

class GachaEvents : Listener {
    companion object {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        /**
         * 능력 뽑기권 아이템을 생성합니다.
         * @return 능력 뽑기권 아이템
         */
        fun getGachaTicketItem(): ItemStack {
            val item = ItemStack(Material.NETHER_STAR)
            val meta = item.itemMeta
            meta.itemName(Component.text("능력 뽑기권", NamedTextColor.GOLD))
            meta.lore(
                listOf(
                    Component.text("능력을 뽑을 수 있는 티켓입니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                    Component.text("손에 들고 우클릭하여 능력을 뽑습니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                )
            )
            meta.itemFlags.add(ItemFlag.HIDE_ENCHANTS)
            item.itemMeta = meta
            return item
        }
        /**
         * 플레이어가 뽑기권을 사용하여 능력을 뽑을 때, 등급을 결정합니다. (픽업 계산 안함)
         * @param player 능력을 뽑는 플레이어
         * @return 뽑힌 능력의 등급
         */
        fun getGradeForGacha(player: Player): Grade {
            val grade: Grade

            val config = plugin.config

            val lBase = config.getDouble("LEGENDARYProbability.Base")
            val lIncrement = config.getDouble("LEGENDARYProbability.Increment")
            val lIncreaseCount = config.getInt("LEGENDARYProbability.IncreaseCount")
            val lPickUp= config.getDouble("LEGENDARYProbability.PickUp")
            val lSemiCeiling = config.getInt("LEGENDARYProbability.SemiCeiling")

            val eBase = config.getDouble("EPICProbability.Base")
            val eIncrement = config.getDouble("EPICProbability.Increment")
            val eIncreaseCount = config.getInt("EPICProbability.IncreaseCount")
            val ePickUp = config.getDouble("EPICProbability.PickUp")
            val eSemiCeiling = config.getInt("EPICProbability.SemiCeiling")

            val tBase = config.getDouble("TROLLProbability")
            val rBase = config.getDouble("RAREProbability")

            val playerConfig = FileManager.getPlayerData(player)

            val playerLCount = playerConfig.getInt("LEGENDARY.Count", 0)

            val playerECount = playerConfig.getInt("EPIC.Count", 0)

            val lP = lBase + lIncrement * (playerLCount - lIncreaseCount + 1).coerceAtLeast(0)
            val eP = eBase + eIncrement * (playerECount - eIncreaseCount + 1).coerceAtLeast(0)


            // LEGENDARY 확률 계산
            if (Random.nextDouble() <= lP) {
                grade = Grade.LEGENDARY
            }
            // EPIC 확률 계산
            else if (Random.nextDouble() <= eP) {
                grade = Grade.EPIC
            }
            else {
                // TROLL 확률 계산
                if (Random.nextDouble() <= tBase) {
                    grade = Grade.TROLL
                }
                // RARE 확률 계산
                else if (Random.nextDouble() <= rBase) {
                    grade = Grade.RARE
                }
                else {
                    grade = Grade.COMMON
                }
            }
            return grade
        }
    }


    @EventHandler
    fun onClickGacha(event: PlayerInteractEvent) {
        val player = event.player
        if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK || event.action == Action.PHYSICAL) return
        if (event.item == null || !event.item!!.isSimilar(getGachaTicketItem())) return
        else {
            event.isCancelled = true
            event.item!!.amount -= 1

            val config = FileManager.getPlayerData(player)

            val grade = getGradeForGacha(player)

            var ability = AbilityManager().getRandomAbilityByGrade(grade)
            // 픽업 능력 및 config 처리
            when (grade) {
                Grade.LEGENDARY -> {
                    var isPickUp = false
                    // 확률적으로 픽업 등장
                    if (Random.nextDouble() <= plugin.config.getDouble("LEGENDARYProbability.PickUp")) {
                        isPickUp = true
                    }
                    // 천장에 의해 픽업 등장
                    if (config.getInt("LEGENDARY.Ceiling",0) >= plugin.config.getInt("LEGENDARYProbability.SemiCeiling")) {
                        isPickUp = true
                    }

                    if (isPickUp) {
                        ability = AbilityPickUp.getLegendaryPickUp(AbilityPickUp.getRoundedTime())
                    }
                    config["LEGENDARY.Count"] = 0
                    config["EPIC.Count"] = config.getInt("EPIC.Count") + 1
                }
                Grade.EPIC -> {
                    var isPickUp = false
                    // 확률적으로 픽업 등장
                    if (Random.nextDouble() <= plugin.config.getDouble("EPICProbability.PickUp")) {
                        isPickUp = true
                    }
                    // 천장에 의해 픽업 등장
                    if (config.getInt("EPIC.Ceiling",0) >= plugin.config.getInt("EPICProbability.SemiCeiling")) {
                        isPickUp = true
                    }

                    if (isPickUp) {
                        ability = AbilityPickUp.getEpicPickUp(AbilityPickUp.getRoundedTime())
                    }
                    config["EPIC.Count"] = 0
                    config["LEGENDARY.Count"] = config.getInt("LEGENDARY.Count") + 1
                }
                else -> {
                    config["LEGENDARY.Count"] = config.getInt("LEGENDARY.Count") + 1
                    config["EPIC.Count"] = config.getInt("EPIC.Count") + 1
                }
            }

            // 천장 처리
            if (grade == Grade.LEGENDARY) {
                if (ability == AbilityPickUp.getLegendaryPickUp(AbilityPickUp.getRoundedTime())) {
                    config["LEGENDARY.Ceiling"] = 0
                } else {
                    config["LEGENDARY.Ceiling"] = (config.getInt("LEGENDARY.Ceiling", 0) + 1)
                }
            }
            else if (grade == Grade.EPIC) {
                if (ability == AbilityPickUp.getEpicPickUp(AbilityPickUp.getRoundedTime())) {
                    config["EPIC.Ceiling"] = 0
                } else {
                    config["EPIC.Ceiling"] = (config.getInt("EPIC.Ceiling", 0) + 1)
                }
            }

            FileManager.savePlayerData(player, config)


            val gachaLocation = player.eyeLocation.add(player.location.direction)
            val gachaVisual = player.world.spawn(gachaLocation, ItemDisplay::class.java)
            gachaVisual.transformation = Transformation(
                Vector3f(0f, -0.5f, 0f),
                AxisAngle4f(0f, 0f, 0f, 0f),
                Vector3f(0.5f, 0.5f, 0.5f),
                AxisAngle4f(0f, 0f, 0f, 0f)
            )
            gachaVisual.interpolationDelay = 0
            gachaVisual.interpolationDuration = 0

            val dust = Particle.DustOptions(Color.WHITE, 0.5f)
            val dustTransition = when (grade) {
                Grade.LEGENDARY -> Particle.DustTransition(Color.WHITE, Color.ORANGE, 1f)
                Grade.EPIC -> Particle.DustTransition(Color.WHITE, Color.PURPLE, 1f)
                Grade.RARE -> Particle.DustTransition(Color.WHITE, Color.TEAL, 1f)
                Grade.COMMON -> Particle.DustTransition(Color.WHITE, Color.GREEN, 1f)
                else -> Particle.DustTransition(Color.WHITE, Color.fromRGB(1, 1, 1), 1f)
            }

            val initialYaw = player.yaw

            gachaVisual.setItemStack(getGachaTicketItem())
            gachaVisual.setRotation(initialYaw, 0f)
            player.world.spawnParticle(Particle.CLOUD, gachaLocation, 50, 0.3, 0.3, 0.3, 0.1)
            player.world.playSound(gachaLocation, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 0.5f)
            player.world.playSound(gachaLocation, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 1.0f, 0.5f)

            object: BukkitRunnable() {
                var timer = 0
                override fun run() {
                    if (timer == 1) {
                        gachaVisual.transformation = Transformation(
                            Vector3f(0f, 0f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(1f, 1f, 1f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        gachaVisual.interpolationDelay = 0
                        gachaVisual.interpolationDuration = 100
                    }
                    // 5초간 회전, 파티클, 점점 빨라짐
                    if (timer <= 100) {
                        gachaVisual.setRotation(gachaVisual.yaw + (timer / 2f), gachaVisual.pitch)
                        if (timer <= 60) {
                            gachaVisual.world.spawnParticle(
                                Particle.DUST,
                                gachaLocation,
                                (timer / 10).coerceAtLeast(1),
                                0.3, 0.3, 0.3,
                                0.1, dust
                            )
                        }
                        else {
                            gachaVisual.world.spawnParticle(
                                Particle.DUST_COLOR_TRANSITION,
                                gachaLocation,
                                ((timer-60) / 2).coerceAtLeast(1),
                                0.3, 0.3, 0.3,
                                0.1, dustTransition
                            )
                        }
                    }

                    // 감속, 아이템 표시
                    else if (timer <= 140) {
                        val abilityItem = ability!!.getItem()
                        gachaVisual.setItemStack(abilityItem)
                        gachaVisual.setRotation(gachaVisual.yaw + ((140 - timer) / 1.09f), gachaVisual.pitch)
                    }

                    // 뽑기 완료, 능력 표시
                    else {
                        gachaVisual.setRotation(initialYaw, 0f)
                        gachaVisual.transformation = Transformation(
                            Vector3f(0f, 0f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(0.7f, 0.7f, 0.7f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        gachaVisual.interpolationDelay = 0
                        gachaVisual.interpolationDuration = 10


                        val gachaName = gachaVisual.world.spawn(gachaLocation.setRotation(initialYaw + 180f, 0f), TextDisplay::class.java)
                        gachaName.text(ability!!.displayName
                            .append(Component.text(" "))
                            .append(ability.grade.displayGrade)
                            .append(Component.text(" "))
                            .append(ability.element.displayElement))

                        //초기상태: 크기 0
                        gachaName.transformation = Transformation(
                            Vector3f(0f, 0f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(0.0f, 0.0f, 0.0f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        gachaName.interpolationDelay = 0
                        gachaName.interpolationDuration = 0

                        // 1틱 후 크기 증가, 위치 위로
                        object: BukkitRunnable() {
                            override fun run() {
                                gachaName.transformation = Transformation(
                                    Vector3f(0f, 0.4f, 0f),
                                    AxisAngle4f(0f, 0f, 0f, 0f),
                                    Vector3f(0.5f, 0.5f, 0.5f),
                                    AxisAngle4f(0f, 0f, 0f, 0f)
                                )
                                gachaName.interpolationDelay = 0
                                gachaName.interpolationDuration = 10
                            }
                        }.runTaskLater(plugin, 1L)


                        val gachaLore = gachaVisual.world.spawn(gachaLocation.subtract(0.0, 0.4, 0.0).setRotation(initialYaw + 180f, 0f), TextDisplay::class.java)
                        val lore = Component.join(JoinConfiguration.separator(Component.text("\n")), ability.description)
                        gachaLore.text(lore)

                        //초기상태: 크기 0
                        gachaLore.transformation = Transformation(
                            Vector3f(0f, 0f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(0.0f, 0.0f, 0.0f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        gachaLore.interpolationDelay = 0
                        gachaLore.interpolationDuration = 10

                        // 1틱 후 크기 증가, 위치 아래로
                        object: BukkitRunnable() {
                            override fun run() {
                                gachaLore.transformation = Transformation(
                                    Vector3f(0f, -0.25f, 0f),
                                    AxisAngle4f(0f, 0f, 0f, 0f),
                                    Vector3f(0.4f, 0.4f, 0.4f),
                                    AxisAngle4f(0f, 0f, 0f, 0f)
                                )
                                gachaLore.interpolationDelay = 0
                                gachaLore.interpolationDuration = 10
                            }
                        }.runTaskLater(plugin, 1L)


                        // 5초 후 디스플레이 제거, 능력 획득
                        object: BukkitRunnable() {
                            override fun run() {
                                AbilityManager().unlockAbility(player, ability)

                                // TROLL 능력 강제 적용
                                if ((plugin.config.getBoolean("ForceTroll")) && ability.grade == Grade.TROLL) {
                                    player.sendMessage(Grade.TROLL.displayGrade.append(Component.text(" 능력이 강제 적용됩니다!", NamedTextColor.RED)))
                                    AbilityManager().applyAbility(player, ability)
                                }

                                gachaVisual.remove()
                                gachaName.remove()
                                gachaLore.remove()
                                player.world.playSound(gachaLocation, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 2f)
                                player.world.spawnParticle(Particle.CLOUD, gachaLocation.add(Vector(0.0, 0.4, 0.0)), 50, 0.3, 0.3, 0.3, 0.1)
                            }
                        }.runTaskLater(plugin, 100L)
                        cancel()
                    }
                    timer++
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }
    }
}