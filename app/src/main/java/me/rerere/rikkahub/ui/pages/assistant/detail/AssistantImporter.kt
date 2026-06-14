package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.CharacterCard
import me.rerere.rikkahub.data.model.DEFAULT_PROMPT_PRESET_ID
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.SelectiveLogic
import me.rerere.rikkahub.data.model.parseSillyTavernRegexScripts
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.R
import org.koin.compose.koinInject

@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    onUpdate: (Assistant) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SillyTavernImporter(onImport = onUpdate)
    }
}

@Composable
private fun SillyTavernImporter(
    onImport: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var isLoading by remember { mutableStateOf(false) }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    runCatching {
                        importAssistantFromUri(
                            context = context,
                            uri = uri,
                            onImport = onImport,
                            toaster = toaster,
                            filesManager = filesManager,
                            settingsStore = settingsStore,
                        )
                    }.onFailure { exception ->
                        exception.printStackTrace()
                        toaster.show(exception.message ?: context.getString(R.string.assistant_importer_import_failed))
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val pngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    runCatching {
                        importAssistantFromUri(
                            context = context,
                            uri = uri,
                            onImport = onImport,
                            toaster = toaster,
                            filesManager = filesManager,
                            settingsStore = settingsStore,
                        )
                    }.onFailure { exception ->
                        exception.printStackTrace()
                        toaster.show(exception.message ?: context.getString(R.string.assistant_importer_import_failed))
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                pngPickerLauncher.launch(arrayOf("image/png"))
            },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_png))
        }

        OutlinedButton(
            onClick = {
                jsonPickerLauncher.launch(arrayOf("application/json"))
            },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_json))
        }
    }
}

// region Parsing Strategy

/**
 * 解析结果：助手 + 可选的随角色卡导入的世界书(character_book)
 */
internal data class ParsedCard(
    val assistant: Assistant,
    val lorebook: Lorebook?,
)

/**
 * SillyTavern 角色卡解析器
 *
 * V2(chara_card_v2) 与 V3(chara_card_v3) 的 data 字段结构基本一致，
 * 使用同一套解析逻辑，并完整提取嵌入的 character_book、depth_prompt、
 * post_history_instructions、mes_example、alternate_greetings 等字段。
 */
internal fun parseTavernCard(
    json: JsonObject,
    avatarImage: String?,
    missingNameMessage: String,
): ParsedCard {
    // V2/V3 将字段包在 data 下；V1 角色卡字段位于根对象。
    val data = json["data"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: json

    fun field(key: String): String? = data[key]?.jsonPrimitiveOrNull?.contentOrNull

    val name = field("name")
        ?: error(missingNameMessage)
    val description = field("description")
    val personality = field("personality")
    val scenario = field("scenario")
    val firstMessage = field("first_mes")
    val system = field("system_prompt")
    val mesExample = field("mes_example")
    val postHistory = field("post_history_instructions")
    val creator = field("creator")
    val creatorNotes = field("creator_notes")
    val characterVersion = field("character_version")

    val tags = data["tags"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
        ?: emptyList()
    val alternateGreetings = data["alternate_greetings"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
        ?: emptyList()

    val systemPrompt = buildString {
        appendLine("You are roleplaying as $name.")
        appendLine()
        if (!system.isNullOrBlank()) {
            appendLine(system.trim())
            appendLine()
        }
        appendLine("## Description of the character")
        appendLine(description?.ifBlank { "Empty" } ?: "Empty")
        appendLine()
        appendLine("## Personality of the character")
        appendLine(personality?.ifBlank { "Empty" } ?: "Empty")
        appendLine()
        appendLine("## Scenario")
        appendLine(scenario?.ifBlank { "Empty" } ?: "Empty")
        if (!mesExample.isNullOrBlank()) {
            appendLine()
            appendLine("## Example dialogue")
            append(mesExample.trim())
        }
    }.trimEnd()

    // 解析嵌入的世界书 character_book → Lorebook
    val characterBook = data["character_book"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val depthPromptEntry = parseDepthPrompt(data)
    val postHistoryEntry = parsePostHistory(postHistory)
    val regexScripts = parseSillyTavernRegexScripts(data)

    val bookEntries = buildList {
        characterBook?.let { addAll(parseCharacterBookEntries(it)) }
        depthPromptEntry?.let { add(it) }
        postHistoryEntry?.let { add(it) }
    }

    val lorebook = if (bookEntries.isNotEmpty()) {
        val bookName = characterBook
            ?.get("name")?.jsonPrimitiveOrNull?.contentOrNull
            ?.ifBlank { null }
            ?: "$name's World"
        Lorebook(
            name = bookName,
            description = "Imported with character: $name",
            enabled = true,
            entries = bookEntries,
            tokenBudget = characterBook?.get("token_budget")?.jsonPrimitiveOrNull?.intOrNull ?: 0,
        )
    } else null

    val assistant = Assistant(
        name = name,
        avatar = avatarImage?.let(Avatar::Image) ?: Avatar.Dummy,
        useAssistantAvatar = avatarImage != null,
        presetMessages = if (!firstMessage.isNullOrBlank()) {
            listOf(UIMessage.assistant(firstMessage))
        } else emptyList(),
        systemPrompt = systemPrompt,
        background = null,
        regexes = regexScripts,
        lorebookIds = lorebook?.let { setOf(it.id) } ?: emptySet(),
        // 绑定内置默认预设，使角色卡字段经 Prompt Manager 的 marker 正确组装，
        // 而非拍扁进单个 systemPrompt
        promptPresetId = DEFAULT_PROMPT_PRESET_ID,
        characterCard = CharacterCard(
            description = description.orEmpty(),
            personality = personality.orEmpty(),
            scenario = scenario.orEmpty(),
            mesExample = mesExample.orEmpty(),
            creatorNotes = creatorNotes.orEmpty(),
            creator = creator.orEmpty(),
            characterVersion = characterVersion.orEmpty(),
            tags = tags,
            firstMessage = firstMessage.orEmpty(),
            alternateGreetings = alternateGreetings,
            postHistoryInstructions = postHistory.orEmpty(),
        ),
    )

    return ParsedCard(assistant = assistant, lorebook = lorebook)
}

/**
 * SillyTavern world_info_position 枚举:
 * 0=before_char, 1=after_char, 2=AN top, 3=AN bottom, 4=at depth.
 * 同时兼容字符串形式 "before_char"/"after_char"。
 */
private fun mapBookPosition(positionValue: String?): InjectionPosition = when (positionValue) {
    "0", "before_char" -> InjectionPosition.BEFORE_SYSTEM_PROMPT
    "1", "after_char" -> InjectionPosition.AFTER_SYSTEM_PROMPT
    "2" -> InjectionPosition.TOP_OF_CHAT
    "3" -> InjectionPosition.BOTTOM_OF_CHAT
    "4" -> InjectionPosition.AT_DEPTH
    else -> InjectionPosition.AFTER_SYSTEM_PROMPT
}

/** SillyTavern role 枚举: 0=system,1=user,2=assistant。RikkaHub 仅支持 USER/ASSISTANT。 */
private fun mapBookRole(roleValue: String?): MessageRole = when (roleValue) {
    "2", "assistant" -> MessageRole.ASSISTANT
    else -> MessageRole.USER
}

/** SillyTavern world_info_logic: 0=AND_ANY, 1=NOT_ALL, 2=NOT_ANY, 3=AND_ALL */
private fun mapSelectiveLogic(value: Int?): SelectiveLogic = when (value) {
    1 -> SelectiveLogic.NOT_ALL
    2 -> SelectiveLogic.NOT_ANY
    3 -> SelectiveLogic.AND_ALL
    else -> SelectiveLogic.AND_ANY
}

private fun parseCharacterBookEntries(book: JsonObject): List<PromptInjection.RegexInjection> {
    val entries = book["entries"] ?: return emptyList()
    // entries 可能是数组，也可能是以 id 为键的对象
    val entryObjects: List<JsonObject> = when {
        runCatching { entries.jsonArray }.isSuccess ->
            entries.jsonArray.mapNotNull { runCatching { it.jsonObject }.getOrNull() }

        runCatching { entries.jsonObject }.isSuccess ->
            entries.jsonObject.values.mapNotNull { runCatching { it.jsonObject }.getOrNull() }

        else -> emptyList()
    }

    return entryObjects.mapNotNull { entry ->
        fun str(key: String) = entry[key]?.jsonPrimitiveOrNull?.contentOrNull
        fun bool(key: String) = entry[key]?.jsonPrimitiveOrNull?.booleanOrNull
        fun int(key: String) = entry[key]?.jsonPrimitiveOrNull?.intOrNull

        val content = str("content") ?: return@mapNotNull null
        val keys = entry["keys"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
            ?: emptyList()
        val secondaryKeys = entry["secondary_keys"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
            ?: emptyList()

        val ext = entry["extensions"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val positionValue = entry["position"]?.jsonPrimitiveOrNull?.contentOrNull
            ?: ext?.get("position")?.jsonPrimitiveOrNull?.contentOrNull
        val depth = ext?.get("depth")?.jsonPrimitiveOrNull?.intOrNull ?: 4
        val role = ext?.get("role")?.jsonPrimitiveOrNull?.contentOrNull
        val probability = ext?.get("probability")?.jsonPrimitiveOrNull?.intOrNull ?: 100
        val useProbability = ext?.get("useProbability")?.jsonPrimitiveOrNull?.booleanOrNull ?: false
        val group = ext?.get("group")?.jsonPrimitiveOrNull?.contentOrNull
            ?: ext?.get("inclusion_group")?.jsonPrimitiveOrNull?.contentOrNull
            ?: ""
        val selective = entry["selective"]?.jsonPrimitiveOrNull?.booleanOrNull ?: secondaryKeys.isNotEmpty()
        val selectiveLogic = mapSelectiveLogic(
            entry["selectiveLogic"]?.jsonPrimitiveOrNull?.intOrNull
                ?: ext?.get("selectiveLogic")?.jsonPrimitiveOrNull?.intOrNull
        )
        val excludeRecursion = ext?.get("exclude_recursion")?.jsonPrimitiveOrNull?.booleanOrNull ?: false
        val preventRecursion = ext?.get("prevent_recursion")?.jsonPrimitiveOrNull?.booleanOrNull ?: false
        val delayUntilRecursion = ext?.get("delay_until_recursion")?.jsonPrimitiveOrNull?.booleanOrNull ?: false

        PromptInjection.RegexInjection(
            name = str("name") ?: str("comment").orEmpty(),
            enabled = bool("enabled") ?: true,
            priority = int("insertion_order") ?: int("priority") ?: 0,
            position = mapBookPosition(positionValue),
            content = content,
            injectDepth = depth,
            role = mapBookRole(role),
            keywords = keys,
            useRegex = false,
            caseSensitive = bool("case_sensitive") ?: false,
            scanDepth = int("scan_depth") ?: 4,
            constantActive = bool("constant") ?: false,
            secondaryKeys = if (selective) secondaryKeys else emptyList(),
            selectiveLogic = selectiveLogic,
            probability = probability,
            useProbability = useProbability,
            inclusionGroup = group,
            excludeRecursion = excludeRecursion,
            preventRecursion = preventRecursion,
            delayUntilRecursion = delayUntilRecursion,
        )
    }
}

/** extensions.depth_prompt → 常驻深度注入 */
private fun parseDepthPrompt(data: JsonObject): PromptInjection.RegexInjection? {
    val ext = data["extensions"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
    val depthPrompt = ext["depth_prompt"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
    val prompt = depthPrompt["prompt"]?.jsonPrimitiveOrNull?.contentOrNull?.ifBlank { null } ?: return null
    val depth = depthPrompt["depth"]?.jsonPrimitiveOrNull?.intOrNull ?: 4
    val role = depthPrompt["role"]?.jsonPrimitiveOrNull?.contentOrNull
    return PromptInjection.RegexInjection(
        name = "Character Note (depth $depth)",
        enabled = true,
        priority = 100,
        position = InjectionPosition.AT_DEPTH,
        content = prompt,
        injectDepth = depth,
        role = when (role) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            else -> MessageRole.USER
        },
        constantActive = true,
    )
}

/** post_history_instructions → 历史末尾常驻注入 */
private fun parsePostHistory(postHistory: String?): PromptInjection.RegexInjection? {
    if (postHistory.isNullOrBlank()) return null
    return PromptInjection.RegexInjection(
        name = "Post-History Instructions",
        enabled = true,
        priority = 200,
        position = InjectionPosition.BOTTOM_OF_CHAT,
        content = postHistory.trim(),
        constantActive = true,
    )
}

// endregion

private suspend fun importAssistantFromUri(
    context: Context,
    uri: Uri,
    onImport: (Assistant) -> Unit,
    toaster: ToasterState,
    filesManager: FilesManager,
    settingsStore: SettingsStore,
) {
    try {
        val mime = withContext(Dispatchers.IO) { filesManager.getFileMimeType(uri) }
        val (jsonString, avatarImage) = withContext(Dispatchers.IO) {
            when (mime) {
                "image/png" -> {
                    val result = ImageUtils.getTavernCharacterMeta(context, uri)
                    result.map { base64Data ->
                        val json = String(Base64.decode(base64Data, Base64.DEFAULT))
                        val image = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
                        json to image
                    }.getOrElse { throw it }
                }

                "application/json" -> {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        .use { it?.readText() }
                        ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                    json to null
                }

                else -> error(context.getString(R.string.assistant_importer_unsupported_file_type, mime ?: "unknown"))
            }
        }
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val parsed = parseTavernCard(
            json = json,
            avatarImage = avatarImage,
            missingNameMessage = context.getString(R.string.assistant_importer_missing_name_field),
        )
        // 将随角色卡导入的世界书写入全局设置，助手通过 lorebookIds 关联
        parsed.lorebook?.let { lorebook ->
            settingsStore.update { settings ->
                settings.copy(lorebooks = settings.lorebooks + lorebook)
            }
        }
        onImport(parsed.assistant)
    } catch (exception: Exception) {
        exception.printStackTrace()
        toaster.show(
            message = exception.message ?: context.getString(R.string.assistant_importer_import_failed),
            type = ToastType.Error
        )
    }
}
