package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

private const val ST_REGEX_USER_INPUT = 1
private const val ST_REGEX_AI_OUTPUT = 2
private const val ST_REGEX_REASONING = 6

/**
 * Parses SillyTavern regex script exports into RikkaHub assistant regex rules.
 *
 * Supported input shapes mirror SillyTavern's importer/exporter:
 * - one regex script object
 * - a bulk-exported array of script objects
 * - an object containing `regex_scripts`
 * - a character/preset extension wrapper containing `extensions.regex_scripts`
 */
fun parseSillyTavernRegexScripts(element: JsonElement): List<AssistantRegex> =
    when (element) {
        is JsonArray -> element.flatMap(::parseSillyTavernRegexScripts)
        is JsonObject -> parseSillyTavernRegexObject(element)
        else -> emptyList()
    }

private fun parseSillyTavernRegexObject(json: JsonObject): List<AssistantRegex> {
    json["regex_scripts"]?.asArrayOrNull()?.let { scripts ->
        return scripts.flatMap(::parseSillyTavernRegexScripts)
    }
    json["extensions"]?.asObjectOrNull()
        ?.get("regex_scripts")
        ?.asArrayOrNull()
        ?.let { scripts -> return scripts.flatMap(::parseSillyTavernRegexScripts) }

    return parseSillyTavernRegexScript(json).orEmpty()
}

private fun parseSillyTavernRegexScript(json: JsonObject): List<AssistantRegex>? {
    val name = json.string("scriptName")?.takeIf { it.isNotBlank() } ?: return null
    val scopes = json.intList("placement").toAssistantRegexScopes()
    if (scopes.isEmpty()) return null

    val markdownOnly = json.boolean("markdownOnly") ?: false
    val promptOnly = json.boolean("promptOnly") ?: false
    val visualModes = when {
        markdownOnly && promptOnly -> listOf(false, true)
        markdownOnly -> listOf(true)
        else -> listOf(false)
    }

    return visualModes.map { visualOnly ->
        AssistantRegex(
            id = Uuid.random(),
            name = name,
            enabled = !(json.boolean("disabled") ?: false),
            findRegex = normalizeSillyTavernFindRegex(json.string("findRegex").orEmpty()),
            replaceString = normalizeSillyTavernReplaceString(json.string("replaceString").orEmpty()),
            trimStrings = json.stringList("trimStrings"),
            affectingScope = scopes,
            visualOnly = visualOnly,
        )
    }
}

private fun List<Int>.toAssistantRegexScopes(): Set<AssistantAffectScope> =
    buildSet {
        if (ST_REGEX_USER_INPUT in this@toAssistantRegexScopes) {
            add(AssistantAffectScope.USER)
        }
        if (ST_REGEX_AI_OUTPUT in this@toAssistantRegexScopes || ST_REGEX_REASONING in this@toAssistantRegexScopes) {
            add(AssistantAffectScope.ASSISTANT)
        }
    }

private fun normalizeSillyTavernFindRegex(value: String): String {
    val literal = parseJavaScriptRegexLiteral(value.trim()) ?: return value.trim()
    val kotlinFlags = buildString {
        if ('i' in literal.flags) append('i')
        if ('m' in literal.flags) append('m')
        if ('s' in literal.flags) append('s')
        if ('x' in literal.flags) append('x')
        if ('U' in literal.flags) append('U')
    }
    return if (kotlinFlags.isBlank()) {
        literal.pattern
    } else {
        "(?$kotlinFlags)${literal.pattern}"
    }
}

private fun normalizeSillyTavernReplaceString(value: String): String =
    value
        .replace(Regex("\\{\\{match\\}\\}", RegexOption.IGNORE_CASE), "\$0")
        .replace(Regex("\\$<([A-Za-z][A-Za-z0-9_]*)>")) { "\${${it.groupValues[1]}}" }

private data class JavaScriptRegexLiteral(
    val pattern: String,
    val flags: String,
)

private fun parseJavaScriptRegexLiteral(value: String): JavaScriptRegexLiteral? {
    if (!value.startsWith("/") || value.length < 2) return null

    val lastSlash = value.lastIndexOf('/')
    if (lastSlash <= 0) return null

    val flags = value.substring(lastSlash + 1)
    if (flags.any { !it.isLetter() }) return null

    return JavaScriptRegexLiteral(
        pattern = value.substring(1, lastSlash).replace("\\/", "/"),
        flags = flags,
    )
}

private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

private fun JsonObject.intList(key: String): List<Int> =
    this[key]?.asArrayOrNull()
        ?.mapNotNull { element -> runCatching { element.jsonPrimitive.intOrNull }.getOrNull() }
        .orEmpty()

private fun JsonObject.stringList(key: String): List<String> =
    this[key]?.asArrayOrNull()
        ?.mapNotNull { element -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull() }
        .orEmpty()

private fun JsonElement.asArrayOrNull(): JsonArray? =
    runCatching { jsonArray }.getOrNull()

private fun JsonElement.asObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()
