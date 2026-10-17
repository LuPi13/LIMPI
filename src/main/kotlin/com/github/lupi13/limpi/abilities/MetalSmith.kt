package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.quests.MasterSmith
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.meta.Damageable

/** 연금술, 뜨거운 곡괭이, 에메랄드 수리를 모두 사용합니다. 연금술 변환은 연금술과 같은 회수/지급 기록을 사용합니다. */
object MetalSmith : Ability(
    grade = Grade.EPIC,
    acquisition = AcquisitionType.QUEST,
    displayName = Component.text("광석 세공사", NamedTextColor.DARK_AQUA),
    codeName = "metal_smith",
    material = Material.SMITHING_TABLE,
    description = listOf(
        Component.text("연금술", NamedTextColor.GOLD)
            .append(Component.text(", ", NamedTextColor.WHITE))
            .append(Component.text("뜨거운 곡괭이", NamedTextColor.DARK_RED))
            .append(Component.text(", ", NamedTextColor.WHITE)),
        Component.text("에메랄드 수리", NamedTextColor.GREEN)
            .append(Component.text(" 능력을 모두 사용합니다.", NamedTextColor.WHITE))
    ),
    needFile = true
) {
    override val howToGet: Component by lazy {
        Component.text("퀘스트 ", NamedTextColor.WHITE)
            .append(MasterSmith.displayName)
            .append(Component.text(" 달성", NamedTextColor.WHITE))
    }

    /** Round away floating-point noise (0.009 * 100 = 0.8999999999...) before showing a percentage. */
    private fun percent(key: String) = Component.text("${java.math.BigDecimal(config!!.getDouble(key) * 100)
        .round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()}", NamedTextColor.GREEN).append(Component.text("%", NamedTextColor.WHITE))

    override val details: List<Component> get() = listOf(
        Component.text("연금술", NamedTextColor.GOLD)
            .append(Component.text(", ", NamedTextColor.WHITE))
            .append(Component.text("뜨거운 곡괭이", NamedTextColor.DARK_RED))
            .append(Component.text(", ", NamedTextColor.WHITE))
            .append(Component.text("에메랄드 수리", NamedTextColor.GREEN))
            .append(Component.text(" 능력을 모두 사용합니다.", NamedTextColor.WHITE)),
        Component.text("구리 주괴", NamedTextColor.YELLOW).append(Component.text("를 손에 들고 우클릭하면", NamedTextColor.WHITE)),
        Component.text("10개를 소모하여 다른 광물로 변환합니다:", NamedTextColor.WHITE),
        Component.text("석탄", NamedTextColor.YELLOW).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("coal"))
            .append(Component.text(" / ", NamedTextColor.WHITE)).append(Component.text("철 조각", NamedTextColor.YELLOW)).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("iron_nugget"))
            .append(Component.text(" / ", NamedTextColor.WHITE)).append(Component.text("철 주괴", NamedTextColor.YELLOW)).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("iron_ingot"))
            .append(Component.text(" / ", NamedTextColor.WHITE)).append(Component.text("금 조각", NamedTextColor.YELLOW)).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("gold_nugget")),
        Component.text("금 주괴", NamedTextColor.YELLOW).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("gold_ingot"))
            .append(Component.text(" / ", NamedTextColor.WHITE)).append(Component.text("레드스톤", NamedTextColor.YELLOW)).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("redstone"))
            .append(Component.text(" / ", NamedTextColor.WHITE)).append(Component.text("청금석", NamedTextColor.YELLOW)).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("lapis_lazuli"))
            .append(Component.text(" / ", NamedTextColor.WHITE)).append(Component.text("에메랄드", NamedTextColor.YELLOW)).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("emerald")),
        Component.text("다이아몬드", NamedTextColor.YELLOW).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("diamond"))
            .append(Component.text(" / ", NamedTextColor.WHITE)).append(Component.text("네더라이트 주괴", NamedTextColor.YELLOW)).append(Component.text(": ", NamedTextColor.WHITE)).append(percent("netherite_ingot")),
        Component.text("광물을 캐면 익혀서 나오고, ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("을 ", NamedTextColor.WHITE)).append(Component.text("곡괭이", NamedTextColor.YELLOW)).append(Component.text("로 공격하면 ", NamedTextColor.WHITE))
            .append(Component.text("${config!!.getInt("fireTicks") / 20.0}", NamedTextColor.GREEN).append(Component.text("초", NamedTextColor.WHITE)))
            .append(Component.text("간 불태웁니다.", NamedTextColor.WHITE)),
        Component.text("인벤토리에서 커서에 ", NamedTextColor.WHITE).append(Component.text("에메랄드", NamedTextColor.YELLOW)).append(Component.text("를 들고 아이템을 우클릭하면", NamedTextColor.WHITE)),
        Component.text("내구도를 ", NamedTextColor.WHITE)
            .append(Component.text("${RepairPolicy.amount(config?.get("repair"))}", NamedTextColor.GREEN))
            .append(Component.text(" 수리합니다.", NamedTextColor.WHITE))
    )

    private val smelted = mapOf(
        Material.RAW_COPPER to Material.COPPER_INGOT,
        Material.RAW_COPPER_BLOCK to Material.COPPER_BLOCK,
        Material.RAW_IRON to Material.IRON_INGOT,
        Material.RAW_IRON_BLOCK to Material.IRON_BLOCK,
        Material.RAW_GOLD to Material.GOLD_INGOT,
        Material.RAW_GOLD_BLOCK to Material.GOLD_BLOCK
    )

    // 연금술: 구리 주괴 10개 → 무작위 광물 1개 (연금술과 같은 게임 안 처리)
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || !event.action.isRightClick || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND && event.hand != org.bukkit.inventory.EquipmentSlot.OFF_HAND) return
        val outcome = transmute(player, event, config!!) ?: return
        when (outcome) {
            "coal" -> player.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.4f, 1.7f)
            "diamond", "netherite_ingot" -> {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
                player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.0f)
            }
            else -> player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
    }

    // 뜨거운 곡괭이: 곡괭이로 공격한 적을 불태웁니다.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (event.finalDamage <= 0 || com.github.lupi13.limpi.combat.DamageContext.current != null) return
        val player = event.damager as? Player ?: return
        if (player.ability != this) return
        if (player.inventory.itemInMainHand.type !in HotPickaxe.pickaxes) return
        val target = event.entity as? org.bukkit.entity.Damageable ?: return
        if (!AbilityManager.isEnemy(player, target)) return
        event.entity.fireTicks = config!!.getInt("fireTicks")
    }

    // 뜨거운 곡괭이: 캔 광물(원석, 원석 블록)이 익혀서 나옵니다.
    @EventHandler(ignoreCancelled = true)
    fun onBreakBlock(event: BlockDropItemEvent) {
        if (event.player.ability != this) return
        event.items.forEach { item ->
            val newType = smelted[item.itemStack.type] ?: return@forEach
            item.itemStack = item.itemStack.withType(newType)
        }
    }

    // 에메랄드 수리: 커서의 에메랄드로 우클릭한 아이템을 수리합니다.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (player.ability != this) return
        // A result slot is only a preview that is rebuilt from its inputs; repairing it would spend the emerald for nothing.
        if (event.slotType == org.bukkit.event.inventory.InventoryType.SlotType.RESULT) return
        val cursorItem = event.cursor
        if (cursorItem.type != Material.EMERALD) return
        val clickedItem = event.currentItem ?: return
        val data = clickedItem.itemMeta as? Damageable ?: return
        if (!data.hasDamage() || !event.isRightClick) return
        val repairAmount = RepairPolicy.amount(config?.get("repair"))
        if (data.damage == 0 || repairAmount == 0) return
        event.isCancelled = true
        data.damage = RepairPolicy.damageAfter(data.damage, repairAmount)
        clickedItem.itemMeta = data
        event.currentItem = clickedItem
        event.view.setCursor(cursorItem.clone().apply { amount-- })
        player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 2f)
    }
}
