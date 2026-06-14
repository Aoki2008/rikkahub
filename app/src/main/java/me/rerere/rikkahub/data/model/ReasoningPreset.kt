package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/**
 * SillyTavern reasoning preset.
 *
 * ST stores model-specific thinking delimiters as prefix/suffix/separator. In
 * the native client these delimiters are used to split assistant text into a
 * Reasoning part plus visible final text.
 */
@Serializable
data class ReasoningPreset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val separator: String = "",
)

fun parseSillyTavernReasoningPreset(
    json: JsonObject,
    fallbackName: String,
): ReasoningPreset {
    fun str(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    return ReasoningPreset(
        name = str("name") ?: fallbackName,
        prefix = str("prefix").orEmpty(),
        suffix = str("suffix").orEmpty(),
        separator = str("separator").orEmpty(),
    )
}

internal fun JsonObject.looksLikeReasoningPreset(): Boolean =
    containsKey("prefix") || containsKey("suffix") || containsKey("separator")
