package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.DoEndermansDreamOfFreeingEnd
import com.github.lupi13.limpi.quests.ExpAbsorber
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Damageable
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import kotlin.random.Random

object JudgementRay : Ability(
    grade = Grade.MYTHIC,
    acquisition = AcquisitionType.QUEST,
    element = Element.MAGIC,
    displayName = Component.text("심", NamedTextColor.RED)
        .append(Component.text("판", NamedTextColor.GREEN))
        .append(Component.text("의", NamedTextColor.BLUE))
        .append(Component.text(" 빛", NamedTextColor.WHITE)),
    codeName = "judgement_ray",
    material = Material.LIGHT,
    description = listOf(
        Component.text("", NamedTextColor.WHITE)
            .append(Component.text("밸런스 붕괴", NamedTextColor.YELLOW))
            .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("더이상 1회용이 아니며, ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("이 아닌 개체에게는", NamedTextColor.WHITE)),
        Component.text("회복을 부여합니다.", NamedTextColor.WHITE)
    ),
    needFile = true,
    restrictedSlot = 40
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(DoEndermansDreamOfFreeingEnd.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    override val details: List<Component> by lazy {
        listOf(
            Component.text("")
                .append(Component.text("밸런스 붕괴", NamedTextColor.YELLOW))
                .append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
            Component.text("적", NamedTextColor.RED)
                .append(Component.text("이 아닌 개체에게 체력을 ", NamedTextColor.WHITE))
                .append(Component.text("${config!!.getDouble("heal")}", NamedTextColor.GREEN))
                .append(Component.text("회복시킵니다.", NamedTextColor.WHITE)),
            Component.text("해당 능력은 더이상 1회용이 아닙니다.", NamedTextColor.YELLOW)
        )
    }



    fun getRailGunItem(): ItemStack {
        val item = ItemStack(Material.ECHO_SHARD)
        val meta = item.itemMeta
        meta.itemName(Component.text("심", NamedTextColor.RED)
        .append(Component.text("판", NamedTextColor.GREEN))
        .append(Component.text("의", NamedTextColor.BLUE))
        .append(Component.text(" 빛", NamedTextColor.WHITE)))
        meta.lore(listOf(
            Component.text("")
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.AQUA))
                .append(Component.text("키를 눌러 레일건을 사격합니다.", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false)
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
            val settings = com.github.lupi13.limpi.ability.RailgunSettings.read(requireNotNull(config).asConfiguration(), healing = true)
            com.github.lupi13.limpi.platform.RailgunEffect.start(player, this, settings, consumed = false)
        } catch (failure: Exception) {
            plugin.logger.log(java.util.logging.Level.WARNING, "Unable to start railgun $codeName for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("레일건 설정/장착 상태를 확인하세요.", NamedTextColor.RED))
        }
    }
}
