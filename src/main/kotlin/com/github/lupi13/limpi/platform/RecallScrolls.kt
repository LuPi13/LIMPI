package com.github.lupi13.limpi.platform

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * 귀환서는 PDC로만 알아본다. 이름·로어가 같은 현수막 무늬는 귀환서가 아니다.
 * 현수막 무늬는 우클릭 동작·조합 재료·능력 재료로 쓰이지 않아 다른 기능과 겹치지 않는다.
 */
object RecallScrolls {
    /** 상점 코드(shopPrice.yml의 `recall_scroll`). */
    const val CODE = "RECALL_SCROLL"
    private val key = NamespacedKey("limpi", "recall_scroll")
    private val material = Material.GLOBE_BANNER_PATTERN
    private const val STACK = 16

    private fun line(vararg parts: Pair<String, NamedTextColor>): Component =
        parts.fold(Component.empty().decoration(TextDecoration.ITALIC, false)) { line, (text, color) -> line.append(Component.text(text, color)) }

    val name: Component = Component.text("귀환서")

    fun item(): ItemStack = ItemStack(material).apply {
        val meta = itemMeta
        meta.itemName(Component.text("귀환서", NamedTextColor.AQUA))
        meta.lore(listOf(
            line("우클릭한 뒤 " to NamedTextColor.WHITE, "3초" to NamedTextColor.GREEN, " 동안 가만히 있으면" to NamedTextColor.WHITE),
            line("침대나 리스폰 정박기(없으면 월드 스폰)로 돌아갑니다." to NamedTextColor.WHITE),
            line("움직이거나 피해를 입으면 취소되며, 귀환서는 소모되지 않습니다." to NamedTextColor.GRAY)))
        meta.setMaxStackSize(STACK)
        meta.setEnchantmentGlintOverride(true)
        meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, 1)
        itemMeta = meta
        // 현수막 무늬 설명("지구본")을 숨긴다.
        setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.PROVIDES_BANNER_PATTERNS).build())
    }

    fun identify(item: ItemStack?): Boolean =
        item != null && item.type == material && item.persistentDataContainer.get(key, PersistentDataType.INTEGER) == 1

    /** 상점 화면의 아이콘은 같은 모양이지만 귀환서가 아니다(화면 밖으로 새어 나가도 쓸 수 없다). */
    fun unmark(item: ItemStack): ItemStack {
        val meta = item.itemMeta
        meta.persistentDataContainer.remove(key)
        item.itemMeta = meta
        return item
    }
}
