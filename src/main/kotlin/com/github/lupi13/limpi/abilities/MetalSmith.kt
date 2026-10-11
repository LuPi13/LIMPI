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
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import kotlin.random.Random

/** 연금술, 뜨거운 곡괭이, 에메랄드 수리를 모두 사용합니다. 연금술 변환은 연금술과 같은 회수/지급 기록을 사용합니다. */
object MetalSmith : Ability(
    grade = Grade.EPIC,
    acquisition = AcquisitionType.QUEST,
    element = Element.SMITH,
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

    private fun percent(key: String) = Component.text("${config!!.getDouble(key) * 100}%", NamedTextColor.GREEN)

    override val details: List<Component> get() = listOf(
        Component.text("연금술", NamedTextColor.GOLD)
            .append(Component.text(", ", NamedTextColor.WHITE))
            .append(Component.text("뜨거운 곡괭이", NamedTextColor.DARK_RED))
            .append(Component.text(", ", NamedTextColor.WHITE))
            .append(Component.text("에메랄드 수리", NamedTextColor.GREEN))
            .append(Component.text(" 능력을 모두 사용합니다.", NamedTextColor.WHITE)),
        Component.text("구리 주괴를 손에 들고 우클릭하면", NamedTextColor.WHITE),
        Component.text("10개를 소모하여 다른 광물로 변환합니다:", NamedTextColor.WHITE),
        Component.text("석탄: ", NamedTextColor.WHITE).append(percent("coal"))
            .append(Component.text(" / 철 조각: ", NamedTextColor.WHITE)).append(percent("iron_nugget"))
            .append(Component.text(" / 철 주괴: ", NamedTextColor.WHITE)).append(percent("iron_ingot"))
            .append(Component.text(" / 금 조각: ", NamedTextColor.WHITE)).append(percent("gold_nugget")),
        Component.text("금 주괴: ", NamedTextColor.WHITE).append(percent("gold_ingot"))
            .append(Component.text(" / 레드스톤: ", NamedTextColor.WHITE)).append(percent("redstone"))
            .append(Component.text(" / 청금석: ", NamedTextColor.WHITE)).append(percent("lapis_lazuli"))
            .append(Component.text(" / 에메랄드: ", NamedTextColor.WHITE)).append(percent("emerald")),
        Component.text("다이아몬드: ", NamedTextColor.WHITE).append(percent("diamond"))
            .append(Component.text(" / 네더라이트 주괴: ", NamedTextColor.WHITE)).append(percent("netherite_ingot")),
        Component.text("광물을 캐면 익혀서 나오고, ", NamedTextColor.WHITE)
            .append(Component.text("적", NamedTextColor.RED))
            .append(Component.text("을 곡괭이로 공격하면 ", NamedTextColor.WHITE))
            .append(Component.text("${config!!.getInt("fireTicks") / 20.0}초", NamedTextColor.GREEN))
            .append(Component.text("간 불태웁니다.", NamedTextColor.WHITE)),
        Component.text("인벤토리에서 커서에 에메랄드를 들고 아이템을 우클릭하면", NamedTextColor.WHITE),
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

    // 연금술: 구리 주괴 10개 → 무작위 광물 1개 (연금술과 같은 ALCHEMY 회수 정산과 지급 기록)
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.ability != this || !event.action.isRightClick || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND && event.hand != org.bukkit.inventory.EquipmentSlot.OFF_HAND) return
        if (com.github.lupi13.limpi.FileManager.delivery.blocked(player.uniqueId)) return
        val observed = event.item?.takeIf { it.type == Material.COPPER_INGOT && it.amount >= 10 } ?: return
        val original = com.github.lupi13.limpi.platform.PaperItemCodec.payload(observed)
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        if (!com.github.lupi13.limpi.FileManager.delivery.claim(player)) return
        val before = com.github.lupi13.limpi.platform.PaperItemCodec.snapshot(player, true)
        val slot = if (event.hand == org.bukkit.inventory.EquipmentSlot.HAND) player.inventory.heldItemSlot else 36
        val source = before[slot] ?: return
        if (player.ability != this || source.payload != original || source.count < 10) return
        val policy = com.github.lupi13.limpi.ability.AlchemyPolicy(
            com.github.lupi13.limpi.ability.AlchemyPolicy.OUTPUTS.associateWith { config!!.getDouble(it) })
        val outcome = policy.choose(Random.nextDouble())
        val result = ItemStack(Material.valueOf(outcome.uppercase(java.util.Locale.ROOT)))
        val terms = com.github.lupi13.limpi.ability.AlchemyTerms(slot, source.payload, outcome,
            com.github.lupi13.limpi.platform.PaperItemCodec.grant(result))
        val after = before.toMutableList().apply { this[slot] = if (source.count == 10) null else source.copy(count = source.count - 10) }
        if (!com.github.lupi13.limpi.FileManager.delivery.remove(player, java.util.UUID.randomUUID(),
                com.github.lupi13.limpi.ability.AlchemySettlement.KIND, terms.encode(), before, after)) return
        when (outcome) {
            "coal" -> player.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.4f, 1.7f)
            "diamond", "netherite_ingot" -> {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
                player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.0f)
            }
            else -> player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        com.github.lupi13.limpi.FileManager.delivery.claim(player, quiet = true)
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
