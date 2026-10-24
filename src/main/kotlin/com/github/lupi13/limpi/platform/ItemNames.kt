package com.github.lupi13.limpi.platform

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 채팅에 보이는 아이템 이름. 이름을 붙이지 않은 아이템은 번역 키로 보내 각 플레이어의 클라이언트가 자기 언어로 보여 준다.
 * 스타일은 붙이지 않으므로 감싸는 컴포넌트의 색을 그대로 따른다.
 */
object ItemNames {
    fun of(material: Material): Component = Component.translatable(material.translationKey())

    /** 모루·플러그인이 붙인 이름은 예전처럼 글자만, 그 밖에는 번역 키(물약 종류 등 아이템별 키 포함). */
    fun of(item: ItemStack): Component {
        val meta = if (item.hasItemMeta()) item.itemMeta else null
        val named = meta?.customName() ?: meta?.takeIf { it.hasItemName() }?.itemName()
        return if (named != null) Component.text(PlainTextComponentSerializer.plainText().serialize(named))
            else Component.translatable(item.translationKey())
    }

    /** 저장된 아이템을 읽지 못하면 기록된 이름 글자를 쓴다. */
    fun of(payload: String, fallback: String): Component =
        runCatching { of(PaperItemCodec.item(payload, 1)) }.getOrElse { Component.text(fallback) }
}
