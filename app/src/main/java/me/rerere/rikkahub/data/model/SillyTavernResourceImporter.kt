package me.rerere.rikkahub.data.model

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.export.parseSillyTavernLorebook

private const val SILLY_TAVERN_CONTENT_BASE_URL =
    "https://raw.githubusercontent.com/SillyTavern/SillyTavern/release/default/content"

private const val SILLY_TAVERN_CONTENTS_API_BASE_URL =
    "https://api.github.com/repos/SillyTavern/SillyTavern/contents/default/content"

private val ResourceJson = Json {
    ignoreUnknownKeys = true
}

data class SillyTavernResourceImport(
    val promptPresets: List<PromptPreset> = emptyList(),
    val contextPresets: List<ContextPreset> = emptyList(),
    val instructPresets: List<InstructPreset> = emptyList(),
    val systemPromptPresets: List<SystemPromptPreset> = emptyList(),
    val reasoningPresets: List<ReasoningPreset> = emptyList(),
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val regexes: List<AssistantRegex> = emptyList(),
    val personas: List<SillyTavernPersonaResource> = emptyList(),
) {
    val globalResourceCount: Int
        get() = promptPresets.size +
            contextPresets.size +
            instructPresets.size +
            systemPromptPresets.size +
            reasoningPresets.size +
            lorebooks.size +
            quickMessages.size +
            personas.size

    val detectedResourceCount: Int
        get() = globalResourceCount + regexes.size

    fun plus(other: SillyTavernResourceImport): SillyTavernResourceImport =
        SillyTavernResourceImport(
            promptPresets = promptPresets + other.promptPresets,
            contextPresets = contextPresets + other.contextPresets,
            instructPresets = instructPresets + other.instructPresets,
            systemPromptPresets = systemPromptPresets + other.systemPromptPresets,
            reasoningPresets = reasoningPresets + other.reasoningPresets,
            lorebooks = lorebooks + other.lorebooks,
            quickMessages = quickMessages + other.quickMessages,
            regexes = regexes + other.regexes,
            personas = personas + other.personas,
        )
}

data class SillyTavernPersonaResource(
    val sourceId: String,
    val persona: Persona,
    val defaultSelected: Boolean = false,
)

data class SillyTavernMarketAsset(
    val filename: String,
    val type: String,
    val displayName: String,
    val downloadUrl: String,
    val description: String? = null,
)

data class SillyTavernSpriteFile(
    val label: String,
    val fileName: String,
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

fun parseSillyTavernSpriteFiles(jsonText: String): List<SillyTavernSpriteFile> =
    ResourceJson.parseToJsonElement(jsonText)
        .asArrayOrNull()
        .orEmpty()
        .mapNotNull { it.asObjectOrNull() }
        .mapNotNull(::parseSpriteFile)
        .sortedWith(
            compareBy<SillyTavernSpriteFile> { it.label != "neutral" }
                .thenBy { it.label.lowercase() }
                .thenBy { it.fileName.lowercase() }
        )

fun sillyTavernSpritesDirectoryApiUrl(filename: String): String =
    "$SILLY_TAVERN_CONTENTS_API_BASE_URL/${filename.toUrlPath()}?ref=release"

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
    parseSillyTavernPersonaBackup(json)?.let { personas ->
        return SillyTavernResourceImport(personas = personas)
    }

    val name = json.stringValue("name")
        ?: json.stringValue("display_name")
        ?: fallbackName

    val promptPreset = parsePromptPresetOrNull(json, name)
    val textCompletionPreset = parseTextCompletionPresetOrNull(json, name)
    val reasoningPreset = parseReasoningPresetOrNull(json, name)
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
        reasoningPresets = listOfNotNull(reasoningPreset),
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

        "openai_preset",
        "chat_completion_preset",
        "prompt_preset",
        "textgen_preset",
        "kobold_preset",
        "novel_preset" -> data.asObjectOrNull()
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

        "reasoning" -> data.asObjectOrNull()
            ?.let { parseSillyTavernReasoningPreset(it, name) }
            ?.let { SillyTavernResourceImport(reasoningPresets = listOf(it)) }

        "quick_reply", "quick_replies" -> SillyTavernResourceImport(
            quickMessages = parseSillyTavernQuickReplies(data)
        )

        "regex", "regex_scripts" -> SillyTavernResourceImport(
            regexes = parseSillyTavernRegexScripts(data)
        )

        "persona", "personas" -> data.asObjectOrNull()
            ?.let(::parseSillyTavernPersonaBackup)
            ?.let { SillyTavernResourceImport(personas = it) }

        else -> parseSillyTavernResourceElement(data, name).takeIf {
            it.detectedResourceCount > 0
        }
    }
}

private fun parseSillyTavernPersonaBackup(json: JsonObject): List<SillyTavernPersonaResource>? {
    val personas = json["personas"]?.asObjectOrNull() ?: return null
    val descriptions = json["persona_descriptions"]?.asObjectOrNull()
    val defaultPersona = json.stringValue("default_persona")

    return personas.mapNotNull { (sourceId, nameElement) ->
        val normalizedSourceId = sourceId.trim()
        if (normalizedSourceId.isBlank()) return@mapNotNull null

        val descriptor = descriptions?.get(sourceId)?.asObjectOrNull()
        val position = descriptor?.intValue("position")
        val name = nameElement.contentStringOrNull()
            ?.trim()
            ?.ifBlank { null }
            ?: "[Unnamed Persona]"

        SillyTavernPersonaResource(
            sourceId = normalizedSourceId,
            persona = Persona(
                name = name,
                description = descriptor?.stringValue("description").orEmpty(),
                enabled = position != 9,
                position = position.toPersonaInjectionPosition(),
                injectDepth = descriptor?.intValue("depth") ?: 2,
                role = descriptor?.intValue("role").toPersonaRole(),
            ),
            defaultSelected = sourceId == defaultPersona,
        )
    }.takeIf { it.isNotEmpty() }
}

private fun parsePromptPresetOrNull(json: JsonObject, fallbackName: String): PromptPreset? {
    if (
        !json.containsKey("prompts") &&
        !json.containsKey("prompt_order") &&
        !json.hasSupportedSamplerPresetFields()
    ) {
        return null
    }
    return runCatching { parseSillyTavernPreset(json, fallbackName) }
        .getOrNull()
        ?.takeIf { it.prompts.isNotEmpty() || it.promptOrder.isNotEmpty() || it.hasSupportedSamplerOverrides() }
}

private fun parseTextCompletionPresetOrNull(
    json: JsonObject,
    fallbackName: String,
): TextCompletionPresetImport? =
    parseSillyTavernTextCompletionPreset(json, fallbackName)
        .takeUnless { it is TextCompletionPresetImport.Unknown }

private fun parseReasoningPresetOrNull(json: JsonObject, fallbackName: String): ReasoningPreset? {
    if (!json.looksLikeReasoningPreset()) return null
    return parseSillyTavernReasoningPreset(json, fallbackName)
}

private fun parseLorebookOrNull(json: JsonObject, fallbackName: String): Lorebook? {
    if (!json.containsKey("entries")) return null
    return runCatching {
        parseSillyTavernLorebook(json.toString(), fallbackName)
    }.getOrNull()?.takeIf { it.entries.isNotEmpty() }
}

private fun parseContentIndexAsset(json: JsonObject): SillyTavernMarketAsset? {
    val filename = json.stringValue("filename", "id", "name")?.takeIf { it.isNotBlank() } ?: return null
    val type = json.stringValue("type")?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val explicitUrl = json.stringValue("url")?.takeIf { it.isNotBlank() }
    return SillyTavernMarketAsset(
        filename = filename,
        type = type,
        displayName = json.stringValue("name")
            ?: filename.substringAfterLast('/').substringBeforeLast('.'),
        downloadUrl = explicitUrl ?: "$SILLY_TAVERN_CONTENT_BASE_URL/${filename.toUrlPath()}",
        description = json.stringValue("description"),
    )
}

private fun parseSpriteFile(json: JsonObject): SillyTavernSpriteFile? {
    val name = json.stringValue("name")?.takeIf { it.isNotBlank() } ?: return null
    val type = json.stringValue("type")
    if (type != null && type != "file") return null
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (extension !in SillyTavernSpriteImageExtensions) return null
    val downloadUrl = json.stringValue("download_url")?.takeIf { it.isNotBlank() } ?: return null
    return SillyTavernSpriteFile(
        label = name.substringBeforeLast('.').ifBlank { name },
        fileName = name,
        downloadUrl = downloadUrl,
    )
}

private val SillyTavernCompatibleAssetTypes = setOf(
    "background",
    "world",
    "openai_preset",
    "textgen_preset",
    "kobold_preset",
    "novel_preset",
    "context",
    "instruct",
    "system_prompt",
    "sysprompt",
    "reasoning",
    "quick_reply",
    "quick_replies",
    "regex",
    "regex_scripts",
    "character",
    "persona",
    "personas",
    "sprites",
    "extension",
)

private fun Int?.toPersonaInjectionPosition(): InjectionPosition =
    when (this) {
        2 -> InjectionPosition.TOP_OF_CHAT
        3 -> InjectionPosition.BOTTOM_OF_CHAT
        4 -> InjectionPosition.AT_DEPTH
        else -> InjectionPosition.AFTER_SYSTEM_PROMPT
    }

private fun Int?.toPersonaRole(): MessageRole =
    when (this) {
        2 -> MessageRole.ASSISTANT
        else -> MessageRole.USER
    }

private val SillyTavernSpriteImageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif")

private fun String.toUrlPath(): String =
    split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
    }

private fun JsonObject.stringValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.contentStringOrNull()
    }

private fun JsonObject.intValue(key: String): Int? =
    this[key]?.let { element ->
        runCatching { element.jsonPrimitive.intOrNull }.getOrNull()
            ?: element.contentStringOrNull()?.toIntOrNull()
    }

private fun JsonElement.contentStringOrNull(): String? =
    runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private fun JsonObject.hasSupportedSamplerPresetFields(): Boolean =
    keys.any {
        it in setOf(
            "temperature",
            "top_p",
            "frequency_penalty",
            "presence_penalty",
            "openai_max_context",
            "openai_max_tokens",
            "max_context",
            "max_length",
            "max_tokens",
        )
    }

private fun PromptPreset.hasSupportedSamplerOverrides(): Boolean =
    temperature != null ||
        topP != null ||
        frequencyPenalty != null ||
        presencePenalty != null ||
        maxContext != null ||
        maxTokens != null

private fun JsonElement.asArrayOrNull(): JsonArray? =
    runCatching { jsonArray }.getOrNull()

private fun JsonElement.asObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()
