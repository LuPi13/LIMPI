package com.github.lupi13.limpi.notice

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * /notice write accepts the in-game JSON text format ({"text":...} or [{...},...]) or plain text.
 * Input that looks like JSON but does not parse is refused rather than broadcast raw; "[공지] ..." is plain text.
 */
object NoticeText {
    private val DECORATIONS = setOf("bold", "italic", "underlined", "strikethrough", "obfuscated")
    private val JSON_START = Regex("""^(\{|"|\[\s*[\[{"\]])""")

    fun looksLikeJson(input: String) = JSON_START.containsMatchIn(input.trim())

    /** Returns the component to store as serialized JSON. */
    fun parse(input: String): Component {
        val text = input.trim()
        if (text.isEmpty()) throw NoticeInputError("공지 내용을 입력하세요.")
        if (!looksLikeJson(text)) return Component.text(text.replace("\\n", "\n"))
        val tree = try { JsonParser.parseString(text) } catch (failure: Exception) {
            throw NoticeInputError("JSON 형식이 올바르지 않습니다: ${failure.message?.substringBefore('\n') ?: "해석 실패"}")
        }
        val component = try { GsonComponentSerializer.gson().deserializeFromTree(normalize(tree)) } catch (failure: Exception) {
            throw NoticeInputError("JSON 텍스트 형식이 올바르지 않습니다: ${failure.message?.substringBefore('\n') ?: "해석 실패"}")
        }
        if (PlainTextComponentSerializer.plainText().serialize(component).isBlank()) throw NoticeInputError("공지 내용이 비어 있습니다.")
        return component
    }

    fun serialize(component: Component): String = GsonComponentSerializer.gson().serialize(component)
    fun deserialize(json: String): Component = GsonComponentSerializer.gson().deserialize(json)

    /** Accept "bold":"true" as written in many examples; the serializer itself wants a boolean. */
    private fun normalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject().also { copy ->
            for ((key, value) in element.entrySet()) {
                val flag = (value as? JsonPrimitive)?.takeIf { key in DECORATIONS && it.isString }?.asString?.lowercase()
                copy.add(key, when (flag) { "true" -> JsonPrimitive(true); "false" -> JsonPrimitive(false); else -> normalize(value) })
            }
        }
        is JsonArray -> JsonArray().also { copy -> element.forEach { copy.add(normalize(it)) } }
        else -> element
    }
}
