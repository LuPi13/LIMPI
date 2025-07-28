package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.quests.LineBetweenSwordAndArrow
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent

object HarmonySword : Ability(
    grade = Grade.RARE,
    element = Element.NONE,
    displayName = Component.text("조화: 검", NamedTextColor.RED),
    codeName = "harmony_sword",
    material = Material.BLADE_POTTERY_SHERD,
    description = listOf(
        Component.text("검으로 피해를 누적시키면", NamedTextColor.WHITE),
        Component.text("다음 화살이 강화됩니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    relatedQuest = listOf(LineBetweenSwordAndArrow)
) {

    override val details: List<Component> by lazy {
        listOf(
            Component.text("검으로 ", NamedTextColor.WHITE)
                .append(Component.text("${config!!.getDouble("required_damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 누적하면, 다음에 발사하는 화살이 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage_amplifier")}", NamedTextColor.GREEN))
                .append(Component.text("배의 피해를 입힙니다.", NamedTextColor.WHITE))
        )
    }

    override fun setUpFile() {
        super.setUpFile()
        if (file != null && file!!.length() == 0L) {
            config?.set("required_damage", 20.0)
            config?.set("damage_amplifier", 1.2)
        }
        saveConfig()
    }


    val sword = listOf(
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.IRON_SWORD,
        Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD
    )
    var amplified: MutableMap<Player, Double> = mutableMapOf()


    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (event.damager !is Player && event.damager !is AbstractArrow) return

        // 플레이어
        if (event.damager is Player) {
            val player = event.damager as Player
            if (player.ability != this) return
            val entity = event.entity as? LivingEntity ?: return

            // 플레이어가 검을 들고 있는지 확인
            if (!sword.contains(player.inventory.itemInMainHand.type)) return


            val showToast: Boolean = ((amplified[player] ?: 0.0) < config!!.getDouble("required_damage"))
            amplified[player] = (amplified[player] ?: 0.0) + event.finalDamage
            if (!showToast) return
            if (amplified[player]!! < config!!.getDouble("required_damage")) {
                var actionBar = Component.text("HARMONY ", NamedTextColor.RED, TextDecoration.BOLD)

                for (i in 0 until (amplified[player]!! / config!!.getDouble("required_damage") * 40.0).toInt()) {
                    actionBar = actionBar.append(Component.text("|", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                }
                for (i in 0 until (40 - amplified[player]!! / config!!.getDouble("required_damage") * 40.0).toInt()) {
                    actionBar = actionBar.append(Component.text("·", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                }

                player.sendActionBar(actionBar)
            }
            else {
                player.sendActionBar(
                    Component.text("HARMONY: 화살 강화됨.", NamedTextColor.RED, TextDecoration.BOLD)
                )
                player.playSound(
                    player.location,
                    Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                    0.5f, 2.0f
                )
            }
        }

        // 화살
        if (event.damager is AbstractArrow) {
            val arrow = event.damager as AbstractArrow
            if (arrow.shooter !is Player) return

            val player = arrow.shooter as Player
            if (player.ability != this || !amplified.contains(player) || amplified[player]!! < config!!.getDouble("required_damage")) return

            // 화살 피해량 증가
            val amplifier = config!!.getDouble("damage_amplifier")
            event.damage *= amplifier
            player.world.playSound(
                arrow.location,
                Sound.ENTITY_WARDEN_DEATH,
                0.7f, 2.0f
            )
            arrow.world.spawnParticle(
                Particle.DRAGON_BREATH,
                arrow.location,
                30, 0.1, 0.1, 0.1, 0.2
            )
            amplified[player] = 0.0 // 강화된 화살 사용 후 제거

            // 퀘스트
            val playerConfig = FileManager.getPlayerData(player)
            val swordCount = playerConfig.getInt("quest.harmony_sword", 0) + 1
            playerConfig.set("quest.harmony_sword", swordCount)
            FileManager.savePlayerData(player, playerConfig)

            val arrowCount = playerConfig.getInt("quest.harmony_arrow", 0)

            if ((swordCount >= 10) && (arrowCount >= 10)) {
                QuestManager.clearQuests(player, LineBetweenSwordAndArrow)
            }
        }
    }

    @EventHandler
    fun onArrowHitBlock(event: ProjectileHitEvent) {
        if (event.entity !is AbstractArrow) return
        val arrow = event.entity as AbstractArrow
        if (arrow.shooter !is Player) return

        val player = arrow.shooter as Player
        if (player.ability != this || !amplified.contains(player) || amplified[player]!! < config!!.getDouble("required_damage")) return

        if ((event.hitBlock != null) && (event.hitEntity == null)) {
            player.world.playSound(
                arrow.location,
                Sound.ENTITY_WARDEN_DEATH,
                0.7f, 2.0f
            )
            arrow.world.spawnParticle(
                Particle.DRAGON_BREATH,
                arrow.location,
                30, 0.1, 0.1, 0.1, 0.2
            )
            amplified[player] = 0.0 // 강화된 화살 사용 후 제거
        }
    }
}