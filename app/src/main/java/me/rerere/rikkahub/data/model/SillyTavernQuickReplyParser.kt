package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses SillyTavern Quick Reply exports into RikkaHub quick messages.
 *
 * Supported shapes:
 * - a settings wrapper with `quickReplyPresets`
 * - a v2 Quick Reply Set with `qrList`
 * - a legacy set with `quickReplySlots`
 * - a single exported Quick Reply object
 * - an array containing any of the above
 */
fun parseSillyTavernQuickReplies(element: JsonElement): List<QuickMessage> =
    when (element) {
        is JsonArray -> element.flatMap(::parseSillyTavernQuickReplies)
        is JsonObject -> parseSillyTavernQuickReplyObject(element)
        else -> emptyList()
    }

private fun parseSillyTavernQuickReplyObject(json: JsonObject): List<QuickMessage> {
    json["quickReplyPresets"]?.asArrayOrNull()?.let { presets ->
        return presets.flatMap(::parseSillyTavernQuickReplies)
    }
    json["extensions"]?.asObjectOrNull()
        ?.get("quickReplyPresets")
        ?.asArrayOrNull()
        ?.let { presets -> return presets.flatMap(::parseSillyTavernQuickReplies) }

    if (json.containsKey("qrList") || json.containsKey("quickReplySlots")) {
        return parseSillyTavernQuickReplySet(json)
    }

    return parseSillyTavernQuickReply(json, QuickReplySetDefaults.single())?.let(::listOf).orEmpty()
}

private fun parseSillyTavernQuickReplySet(json: JsonObject): List<QuickMessage> {
    val defaults = QuickReplySetDefaults(
        sourceSet = json.string("name").orEmpty(),
        sendImmediately = !(json.boolean("disableSend") ?: json.boolean("quickActionEnabled") ?: false),
        injectInput = json.boolean("injectInput") ?: json.boolean("AutoInputInject") ?: false,
        placeBeforeInput = json.boolean("placeBeforeInput") ?: json.boolean("placeBeforeInputEnabled") ?: false,
    )
    val entries = json["qrList"]?.asArrayOrNull()
        ?: json["quickReplySlots"]?.asArrayOrNull()
        ?: return emptyList()

    return entries.mapNotNull { entry ->
        entry.asObjectOrNull()?.let { parseSillyTavernQuickReply(it, defaults) }
    }
}

private fun parseSillyTavernQuickReply(
    json: JsonObject,
    defaults: QuickReplySetDefaults,
): QuickMessage? {
    val hidden = json.boolean("isHidden") ?: json.boolean("hidden") ?: false
    if (hidden) return null

    val content = json.string("message") ?: json.string("mes") ?: return null
    if (content.isBlank()) return null

    val label = json.string("label").orEmpty()
    val title = label
        .ifBlank { json.string("title").orEmpty() }
        .ifBlank { content.lineSequence().firstOrNull().orEmpty() }
        .take(80)

    return QuickMessage(
        title = title,
        content = content,
        sourceSet = defaults.sourceSet,
        sendImmediately = defaults.sendImmediately,
        injectInput = defaults.injectInput,
        placeBeforeInput = defaults.placeBeforeInput,
    )
}

private data class QuickReplySetDefaults(
    val sourceSet: String,
    val sendImmediately: Boolean,
    val injectInput: Boolean,
    val placeBeforeInput: Boolean,
) {
    companion object {
        fun single() = QuickReplySetDefaults(
            sourceSet = "",
            sendImmediately = false,
            injectInput = false,
            placeBeforeInput = false,
        )
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

private fun JsonElement.asArrayOrNull(): JsonArray? =
    runCatching { jsonArray }.getOrNull()

private fun JsonElement.asObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()
