package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object Alchemy : Ability(
    grade = Grade.RARE,
    element = Element.SMITH,
    displayName = Component.text("연금술", NamedTextColor.GOLD),
    codeName = "alchemy",
    material = Material.RAW_GOLD,
    description = listOf(
        Component.text("구리 주괴를 손에 들고 우클릭하면", NamedTextColor.WHITE),
        Component.text("10개를 소모하여 다른 광물로 변환합니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){
    override val relatedQuest by lazy { listOf(com.github.lupi13.limpi.quests.MasterSmith, com.github.lupi13.limpi.quests.Lotto) }
    override val details: List<Component> by settingsCached {
        // 광물 이름은 YELLOW, 확률 수치는 GREEN, 단위(%)는 WHITE
        fun chance(name: String, key: String): Component =
            Component.text(name, NamedTextColor.YELLOW)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(Component.text(java.math.BigDecimal(config!!.getDouble(key) * 100).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString(), NamedTextColor.GREEN))
                .append(Component.text("%", NamedTextColor.WHITE))
        val separator = Component.text(" / ", NamedTextColor.WHITE)
        listOf(
            Component.text("구리 주괴", NamedTextColor.YELLOW)
                .append(Component.text("를 손에 들고 우클릭하면", NamedTextColor.WHITE)),
            Component.text("10개를 소모하여 다른 광물로 변환합니다.", NamedTextColor.WHITE),
            Component.text("변환 가능한 광물은 다음과 같습니다:", NamedTextColor.WHITE),
            chance("석탄", "coal")
                .append(separator).append(chance("철 조각", "iron_nugget"))
                .append(separator).append(chance("철 주괴", "iron_ingot"))
                .append(separator).append(chance("금 조각", "gold_nugget")),
            chance("금 주괴", "gold_ingot")
                .append(separator).append(chance("레드스톤", "redstone"))
                .append(separator).append(chance("청금석", "lapis_lazuli"))
                .append(separator).append(chance("에메랄드", "emerald")),
            chance("다이아몬드", "diamond")
                .append(separator).append(chance("네더라이트 주괴", "netherite_ingot"))
        )
    }


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
            com.github.lupi13.limpi.platform.PaperItemCodec.grant(result), lottoEligible = true)
        val after = before.toMutableList().apply { this[slot] = if (source.count == 10) null else source.copy(count = source.count - 10) }
        if (!com.github.lupi13.limpi.FileManager.delivery.remove(player, java.util.UUID.randomUUID(),
                com.github.lupi13.limpi.ability.AlchemySettlement.KIND, terms.encode(), before, after)) return
        if (outcome == "netherite_ingot") com.github.lupi13.limpi.quests.QuestManager.clearQuests(player, com.github.lupi13.limpi.quests.Lotto)
        when (outcome) {
            "coal" -> player.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.7f)
            "netherite_ingot" -> player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 2.0f)
            else -> player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
        com.github.lupi13.limpi.FileManager.delivery.claim(player, quiet = true)
    }
}
