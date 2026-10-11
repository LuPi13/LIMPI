package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.DoEndermansDreamOfFreeingEnd
import com.github.lupi13.limpi.quests.ExpAbsorber
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Damageable
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Enderman
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import kotlin.random.Random
import kotlin.random.nextInt

object OverBalance : Ability(
    grade = Grade.LEGENDARY,
    element = Element.MAGIC,
    displayName = Component.text("밸런스 붕괴", NamedTextColor.YELLOW),
    codeName = "over_balance",
    material = Material.ECHO_SHARD,
    description = listOf(
        Component.text("왼손이 1회용 레일건으로 고정됩니다.", NamedTextColor.WHITE),
        Component.text("모든 지형을 관통하는 거대한", NamedTextColor.WHITE),
        Component.text("탄환을 한 발 쏩니다.", NamedTextColor.WHITE),
    ),
    relatedQuest = listOf(DoEndermansDreamOfFreeingEnd),
    needFile = true,
    restrictedSlot = 40
){

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 ", NamedTextColor.WHITE).append(Component.text("레일건", NamedTextColor.YELLOW)).append(Component.text(" 사격을 시작합니다.", NamedTextColor.WHITE))),
            Component.text("${config!!.getInt("shootingDelayTicks") / 20.0}", NamedTextColor.GREEN)
                .append(Component.text("초 간 움직이지 못하고 ", NamedTextColor.WHITE).append(Component.text("레일건", NamedTextColor.YELLOW)).append(Component.text(" 사격을 준비합니다.", NamedTextColor.WHITE))),
            Component.text("이 후, ", NamedTextColor.WHITE)
                .append(Component.text("적", NamedTextColor.RED))
                .append(Component.text("에게 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("damage")}", NamedTextColor.GREEN))
                .append(Component.text("의 피해를 주는 거대한 탄환을 발사합니다.", NamedTextColor.WHITE)),
            Component.text("이 능력은 1회용으로, 사격 후 능력이 제거됩니다.", NamedTextColor.YELLOW)
        )
    }



    fun getRailGunItem(): ItemStack {
        val item = ItemStack(Material.ECHO_SHARD)
        val meta = item.itemMeta
        meta.itemName(Component.text("1회용 레일건", NamedTextColor.WHITE))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 레일건을 사격합니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
            Component.text("1회용으로, 사격 후 능력이 제거됩니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta
        return item
    }


    override val activeItem: ItemStack by lazy {
        getRailGunItem()
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.ability != this || com.github.lupi13.limpi.FileManager.delivery.blocked(player.uniqueId)) return
        event.isCancelled = true // Reserved offhand is a custom input; its vanilla swap is intentionally cancelled.
        if (player.getCooldown(material) > 0) { AbilityManager.showCooldown(player, material); return }
        try {
            val settings = com.github.lupi13.limpi.ability.RailgunSettings.read(requireNotNull(config).asConfiguration(), healing = false)
            com.github.lupi13.limpi.platform.RailgunEffect.start(player, this, settings, consumed = true)
            QuestManager.recordUse(player, DoEndermansDreamOfFreeingEnd)
        } catch (failure: Exception) {
            plugin.logger.log(java.util.logging.Level.WARNING, "Unable to start railgun $codeName for ${player.uniqueId}", failure)
        }
    }
}
