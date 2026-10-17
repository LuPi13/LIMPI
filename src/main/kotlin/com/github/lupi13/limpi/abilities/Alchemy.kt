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
        val outcome = transmute(player, event, config!!) ?: return
        if (outcome == "netherite_ingot") com.github.lupi13.limpi.quests.QuestManager.clearQuests(player, com.github.lupi13.limpi.quests.Lotto)
        when (outcome) {
            "coal" -> player.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.7f)
            "netherite_ingot" -> player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 2.0f)
            else -> player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
    }
}

/**
 * 연금술 공통: 손에 든 구리 주괴 10개를 무작위 광물 1개로 바꿉니다.
 * 값싼 물건이라 DB 기록 없이 게임 안에서만 처리합니다. 인벤토리가 가득 차면 결과물을 발밑에 떨어뜨립니다.
 */
internal fun transmute(player: org.bukkit.entity.Player, event: PlayerInteractEvent, config: com.github.lupi13.limpi.config.AbilitySettings): String? {
    if (com.github.lupi13.limpi.FileManager.delivery.blocked(player.uniqueId)) return null
    val observed = event.item?.takeIf { it.type == Material.COPPER_INGOT && it.amount >= 10 } ?: return null
    val hand = event.hand ?: return null
    event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
    val held = player.inventory.getItem(hand)
    if (!held.isSimilar(observed) || held.amount < 10) return null
    val policy = com.github.lupi13.limpi.ability.AlchemyPolicy(
        com.github.lupi13.limpi.ability.AlchemyPolicy.OUTPUTS.associateWith { config.getDouble(it) })
    val outcome = policy.choose(Random.nextDouble())
    held.amount -= 10
    player.inventory.setItem(hand, held)
    val result = ItemStack(Material.valueOf(outcome.uppercase(java.util.Locale.ROOT)))
    player.inventory.addItem(result).values.forEach { player.world.dropItem(player.location, it) }
    return outcome
}
