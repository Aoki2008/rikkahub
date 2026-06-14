package me.rerere.rikkahub.data.model

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.export.parseSillyTavernLorebook

private const val SILLY_TAVERN_CONTENT_BASE_URL =
    "https://raw.githubusercontent.com/SillyTavern/SillyTavern/release/default/content"

private val ResourceJson = Json {
    ignoreUnknownKeys = true
}

data class SillyTavernResourceImport(
    val promptPresets: List<PromptPreset> = emptyList(),
    val contextPresets: List<ContextPreset> = emptyList(),
    val instructPresets: List<InstructPreset> = emptyList(),
    val systemPromptPresets: List<SystemPromptPreset> = emptyList(),
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val regexes: List<AssistantRegex> = emptyList(),
) {
    val globalResourceCount: Int
        get() = promptPresets.size +
            contextPresets.size +
            instructPresets.size +
            systemPromptPresets.size +
            lorebooks.size +
            quickMessages.size

    val detectedResourceCount: Int
        get() = globalResourceCount + regexes.size

    fun plus(other: SillyTavernResourceImport): SillyTavernResourceImport =
        SillyTavernResourceImport(
            promptPresets = promptPresets + other.promptPresets,
            contextPresets = contextPresets + other.contextPresets,
            instructPresets = instructPresets + other.instructPresets,
            systemPromptPresets = systemPromptPresets + other.systemPromptPresets,
            lorebooks = lorebooks + other.lorebooks,
            quickMessages = quickMessages + other.quickMessages,
            regexes = regexes + other.regexes,
        )
}

data class SillyTavernMarketAsset(
    val filename: String,
    val type: String,
    val displayName: String,
    val downloadUrl: String,
)

fun parseSillyTavernResources(
    jsonText: String,
    fallbackName: String,
): SillyTavernResourceImport =
    parseSillyTavernResourceElement(
        element = ResourceJson.parseToJsonElement(jsonText),
        fallbackName = fallbackName,
    )

fun parseSillyTavernContentIndex(jsonText: String): List<SillyTavernMarketAsset> =
    ResourceJson.parseToJsonElement(jsonText)
        .asArrayOrNull()
        .orEmpty()
        .mapNotNull { it.asObjectOrNull() }
        .mapNotNull(::parseContentIndexAsset)
        .filter { it.type in SillyTavernCompatibleAssetTypes }

private fun parseSillyTavernResourceElement(
    element: JsonElement,
    fallbackName: String,
): SillyTavernResourceImport = when (element) {
    is JsonArray -> element.fold(SillyTavernResourceImport()) { acc, item ->
        acc.plus(parseSillyTavernResourceElement(item, fallbackName))
    }

    is JsonObject -> parseSillyTavernResourceObject(element, fallbackName)
    else -> SillyTavernResourceImport()
}

private fun parseSillyTavernResourceObject(
    json: JsonObject,
    fallbackName: String,
): SillyTavernResourceImport {
    parseExplicitExportObject(json, fallbackName)?.let { return it }

    val name = json.stringValue("name")
        ?: json.stringValue("display_name")
        ?: fallbackName

    val promptPreset = parsePromptPresetOrNull(json, name)
    val textCompletionPreset = parseTextCompletionPresetOrNull(json, name)
    val lorebook = parseLorebookOrNull(json, name)
    val quickMessages = parseSillyTavernQuickReplies(json)
    val regexes = parseSillyTavernRegexScripts(json)

    return SillyTavernResourceImport(
        promptPresets = listOfNotNull(promptPreset),
        contextPresets = listOfNotNull(textCompletionPreset as? TextCompletionPresetImport.Context)
            .map { it.preset },
        instructPresets = listOfNotNull(textCompletionPreset as? TextCompletionPresetImport.Instruct)
            .map { it.preset },
        systemPromptPresets = listOfNotNull(textCompletionPreset as? TextCompletionPresetImport.SystemPrompt)
            .map { it.preset },
        lorebooks = listOfNotNull(lorebook),
        quickMessages = quickMessages,
        regexes = regexes,
    )
}

private fun parseExplicitExportObject(
    json: JsonObject,
    fallbackName: String,
): SillyTavernResourceImport? {
    val type = json.stringValue("type") ?: return null
    val data = json["data"] ?: return null
    val name = json.stringValue("name")
        ?: data.asObjectOrNull()?.stringValue("name")
        ?: fallbackName

    return when (type) {
        "world", "lorebook" -> data.asObjectOrNull()
            ?.let { parseLorebookOrNull(it, name) }
            ?.let { SillyTavernResourceImport(lorebooks = listOf(it)) }

        "openai_preset", "chat_completion_preset", "prompt_preset" -> data.asObjectOrNull()
            ?.let { parsePromptPresetOrNull(it, name) }
            ?.let { SillyTavernResourceImport(promptPresets = listOf(it)) }

        "context" -> data.asObjectOrNull()
            ?.let { parseSillyTavernContextPreset(it, name) }
            ?.let { SillyTavernResourceImport(contextPresets = listOf(it)) }

        "instruct" -> data.asObjectOrNull()
            ?.let { parseSillyTavernInstructPreset(it, name) }
            ?.let { SillyTavernResourceImport(instructPresets = listOf(it)) }

        "system_prompt", "sysprompt" -> data.asObjectOrNull()
            ?.let { parseSillyTavernSystemPromptPreset(it, name) }
            ?.let { SillyTavernResourceImport(systemPromptPresets = listOf(it)) }

        "quick_reply", "quick_replies" -> SillyTavernResourceImport(
            quickMessages = parseSillyTavernQuickReplies(data)
        )

        "regex", "regex_scripts" -> SillyTavernResourceImport(
            regexes = parseSillyTavernRegexScripts(data)
        )

        else -> parseSillyTavernResourceElement(data, name).takeIf {
            it.detectedResourceCount > 0
        }
    }
}

private fun parsePromptPresetOrNull(json: JsonObject, fallbackName: String): PromptPreset? {
    if (!json.containsKey("prompts") || !json.containsKey("prompt_order")) return null
    return runCatching { parseSillyTavernPreset(json, fallbackName) }
        .getOrNull()
        ?.takeIf { it.prompts.isNotEmpty() || it.promptOrder.isNotEmpty() }
}

private fun parseTextCompletionPresetOrNull(
    json: JsonObject,
    fallbackName: String,
): TextCompletionPresetImport? =
    parseSillyTavernTextCompletionPreset(json, fallbackName)
        .takeUnless { it is TextCompletionPresetImport.Unknown }

private fun parseLorebookOrNull(json: JsonObject, fallbackName: String): Lorebook? {
    if (!json.containsKey("entries")) return null
    return runCatching {
        parseSillyTavernLorebook(json.toString(), fallbackName)
    }.getOrNull()?.takeIf { it.entries.isNotEmpty() }
}

private fun parseContentIndexAsset(json: JsonObject): SillyTavernMarketAsset? {
    val filename = json.stringValue("filename")?.takeIf { it.isNotBlank() } ?: return null
    val type = json.stringValue("type")?.takeIf { it.isNotBlank() } ?: return null
    return SillyTavernMarketAsset(
        filename = filename,
        type = type,
        displayName = filename.substringAfterLast('/').substringBeforeLast('.'),
        downloadUrl = "$SILLY_TAVERN_CONTENT_BASE_URL/${filename.toUrlPath()}",
    )
}

private val SillyTavernCompatibleAssetTypes = setOf(
    "world",
    "openai_preset",
    "context",
    "instruct",
    "system_prompt",
    "sysprompt",
    "quick_reply",
    "quick_replies",
    "regex",
    "regex_scripts",
    "character",
)

private fun String.toUrlPath(): String =
    split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
    }

private fun JsonObject.stringValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.let { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
        }
    }

private fun JsonElement.asArrayOrNull(): JsonArray? =
    runCatching { jsonArray }.getOrNull()

private fun JsonElement.asObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()
