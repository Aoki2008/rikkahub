package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.parseToJsonElement
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.SelectiveLogic
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDateTime
import kotlin.uuid.Uuid

@Serializable
data class ExportData(
    val version: Int = 1,
    val type: String,
    val data: JsonElement
)

interface ExportSerializer<T> {
    val type: String

    fun export(data: T): ExportData
    fun import(context: Context, uri: Uri): Result<T>

    // 获取导出文件名
    fun getExportFileName(data: T): String = "${type}.json"

    // 便捷方法：直接导出为 JSON 字符串
    fun exportToJson(data: T, json: Json = DefaultJson): String {
        return json.encodeToString(ExportData.serializer(), export(data))
    }

    // 读取 URI 内容的便捷方法
    fun readUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Failed to read file")
    }

    fun getUriFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) cursor.getString(nameIndex) else null
            } else null
        }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}

object ModeInjectionSerializer : ExportSerializer<PromptInjection.ModeInjection> {
    override val type = "mode_injection"

    override fun getExportFileName(data: PromptInjection.ModeInjection): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: PromptInjection.ModeInjection): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<PromptInjection.ModeInjection> {
        return runCatching {
            val json = readUri(context, uri)
            // 首先尝试解析为自己的格式
            tryImportNative(json)
                ?: throw IllegalArgumentException("Unsupported format")
        }
    }

    private fun tryImportNative(json: String): PromptInjection.ModeInjection? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            ExportSerializer.DefaultJson
                .decodeFromJsonElement<PromptInjection.ModeInjection>(exportData.data)
                .copy(id = Uuid.random())
        }.getOrNull()
    }
}

object LorebookSerializer : ExportSerializer<Lorebook> {
    override val type = "lorebook"

    override fun getExportFileName(data: Lorebook): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: Lorebook): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<Lorebook> {
        return runCatching {
            val json = readUri(context, uri)
            // 首先尝试解析为自己的格式
            tryImportNative(json)
            // 然后尝试解析为 SillyTavern 格式
                ?: tryImportSillyTavern(json, getUriFileName(context, uri)?.removeSuffix(".json"))
                ?: throw IllegalArgumentException("Unsupported format")
        }
    }

    private fun tryImportNative(json: String): Lorebook? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            ExportSerializer.DefaultJson
                .decodeFromJsonElement<Lorebook>(exportData.data)
                .copy(
                    id = Uuid.random(),
                    entries = ExportSerializer.DefaultJson
                        .decodeFromJsonElement<Lorebook>(exportData.data)
                        .entries.map { it.copy(id = Uuid.random()) }
                )
        }.getOrNull()
    }

    private fun tryImportSillyTavern(json: String, fileName: String?): Lorebook? {
        return runCatching {
            parseSillyTavernLorebook(json, fileName)
        }.getOrNull()
    }
}

internal fun parseSillyTavernLorebook(json: String, fileName: String?): Lorebook {
    val root = ExportSerializer.DefaultJson.parseToJsonElement(json).jsonObjectOrNull()
        ?: throw IllegalArgumentException("SillyTavern lorebook root must be an object")
    val entries = root["entries"]?.let(::parseSillyTavernEntries).orEmpty()

    return Lorebook(
        id = Uuid.random(),
        name = root.stringValue("name") ?: fileName ?: LocalDateTime.now().toLocalString(),
        description = root.stringValue("description").orEmpty(),
        enabled = true,
        tokenBudget = root.intValue("token_budget", "tokenBudget") ?: 0,
        entries = entries,
    )
}

private fun parseSillyTavernEntries(element: JsonElement): List<PromptInjection.RegexInjection> {
    val objects = element.jsonArrayOrNull()
        ?.mapNotNull { it.jsonObjectOrNull() }
        ?: element.jsonObjectOrNull()?.values?.mapNotNull { it.jsonObjectOrNull() }
        ?: emptyList()

    return objects.mapNotNull(::parseSillyTavernEntry)
}

private fun parseSillyTavernEntry(entry: JsonObject): PromptInjection.RegexInjection? {
    val extensions = entry.objectValue("extensions")
    val content = entry.stringValue("content") ?: return null
    val keywords = entry.stringListValue("key", "keys")
    val secondaryKeys = entry.stringListValue("keysecondary", "secondary_keys")
    val selective = entry.boolValue("selective") ?: secondaryKeys.isNotEmpty()

    return PromptInjection.RegexInjection(
        id = Uuid.random(),
        name = entry.stringValue("comment", "name").orEmpty().ifEmpty { keywords.firstOrNull().orEmpty() },
        enabled = entry.boolValue("enabled") ?: !(entry.boolValue("disable") ?: false),
        priority = entry.intValue("order", "insertion_order", "priority") ?: 100,
        position = mapSillyTavernPosition(
            entry.stringValue("position") ?: extensions?.stringValue("position")
        ),
        injectDepth = entry.intValue("depth") ?: extensions?.intValue("depth") ?: 4,
        role = mapSillyTavernRole(entry.stringValue("role") ?: extensions?.stringValue("role")),
        content = content,
        keywords = keywords,
        useRegex = entry.boolValue("use_regex") ?: extensions?.boolValue("use_regex") ?: false,
        caseSensitive = entry.boolValue("caseSensitive", "case_sensitive")
            ?: extensions?.boolValue("caseSensitive", "case_sensitive")
            ?: false,
        scanDepth = entry.intValue("scanDepth", "scan_depth")
            ?: extensions?.intValue("scanDepth", "scan_depth")
            ?: 4,
        constantActive = entry.boolValue("constant") ?: false,
        secondaryKeys = if (selective) secondaryKeys else emptyList(),
        selectiveLogic = mapSillyTavernSelectiveLogic(
            entry.intValue("selectiveLogic") ?: extensions?.intValue("selectiveLogic")
        ),
        matchWholeWords = entry.boolValue("matchWholeWords", "match_whole_words")
            ?: extensions?.boolValue("matchWholeWords", "match_whole_words")
            ?: false,
        probability = (entry.intValue("probability") ?: extensions?.intValue("probability") ?: 100)
            .coerceIn(0, 100),
        useProbability = entry.boolValue("useProbability", "use_probability")
            ?: extensions?.boolValue("useProbability", "use_probability")
            ?: false,
        inclusionGroup = entry.stringValue("group", "inclusion_group")
            ?: extensions?.stringValue("group", "inclusion_group")
            ?: "",
        excludeRecursion = entry.boolValue("excludeRecursion", "exclude_recursion")
            ?: extensions?.boolValue("excludeRecursion", "exclude_recursion")
            ?: false,
        preventRecursion = entry.boolValue("preventRecursion", "prevent_recursion")
            ?: extensions?.boolValue("preventRecursion", "prevent_recursion")
            ?: false,
        delayUntilRecursion = entry.boolValue("delayUntilRecursion", "delay_until_recursion")
            ?: extensions?.boolValue("delayUntilRecursion", "delay_until_recursion")
            ?: false,
    )
}

private fun mapSillyTavernPosition(position: String?): InjectionPosition = when (position) {
    "0", "before_char" -> InjectionPosition.BEFORE_SYSTEM_PROMPT
    "1", "after_char" -> InjectionPosition.AFTER_SYSTEM_PROMPT
    "2" -> InjectionPosition.TOP_OF_CHAT
    "3" -> InjectionPosition.BOTTOM_OF_CHAT
    "4" -> InjectionPosition.AT_DEPTH
    else -> InjectionPosition.AFTER_SYSTEM_PROMPT
}

private fun mapSillyTavernRole(role: String?): MessageRole = when (role) {
    "2", "assistant" -> MessageRole.ASSISTANT
    else -> MessageRole.USER
}

private fun mapSillyTavernSelectiveLogic(value: Int?): SelectiveLogic = when (value) {
    1 -> SelectiveLogic.NOT_ALL
    2 -> SelectiveLogic.NOT_ANY
    3 -> SelectiveLogic.AND_ALL
    else -> SelectiveLogic.AND_ANY
}

private fun JsonObject.objectValue(key: String): JsonObject? =
    this[key]?.jsonObjectOrNull()

private fun JsonObject.stringValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.let { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
        }
    }

private fun JsonObject.intValue(vararg keys: String): Int? =
    stringValue(*keys)?.toIntOrNull()

private fun JsonObject.boolValue(vararg keys: String): Boolean? =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.let { element ->
            runCatching { element.jsonPrimitive.booleanOrNull }.getOrNull()
                ?: runCatching { element.jsonPrimitive.contentOrNull?.toBooleanStrictOrNull() }.getOrNull()
        }
    }

private fun JsonObject.stringListValue(vararg keys: String): List<String> =
    keys.firstNotNullOfOrNull { key ->
        val element = this[key] ?: return@firstNotNullOfOrNull null
        element.jsonArrayOrNull()
            ?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            ?: runCatching { element.jsonPrimitive.contentOrNull?.let(::listOf) }.getOrNull()
    }.orEmpty()

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

private fun JsonElement.jsonArrayOrNull() =
    runCatching { jsonArray }.getOrNull()
