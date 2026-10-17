package com.github.lupi13.limpi.abilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractEvent

object AlchemyPlus : Ability(
    grade = Grade.EPIC,
    acquisition = AcquisitionType.QUEST,
    displayName = Component.text("연금술+...?", NamedTextColor.GOLD),
    codeName = "alchemy_plus",
    material = Material.RAW_GOLD,
    description = listOf(
        Component.text("연금술", NamedTextColor.GOLD).append(Component.text(" 능력을 계승합니다.", NamedTextColor.WHITE)),
        Component.text("석탄 확률이 크게 줄고, 금 확률이 크게 늘어납니다.", NamedTextColor.WHITE)
    ),
    needFile = true
){
    override val glint: Boolean get() = true

    override val howToGet by lazy { Component.text("퀘스트 ", NamedTextColor.WHITE).append(com.github.lupi13.limpi.quests.Lotto.displayName).append(Component.text(" 달성", NamedTextColor.WHITE)) }
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
        when (outcome) {
            "coal" -> player.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.4f, 1.7f)
            "diamond", "netherite_ingot" -> {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
                player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.0f)
            }
            else -> player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.5f)
        }
    }
}
