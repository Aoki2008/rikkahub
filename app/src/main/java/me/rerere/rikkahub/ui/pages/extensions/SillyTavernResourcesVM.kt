package me.rerere.rikkahub.ui.pages.extensions

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes
import me.rerere.rikkahub.data.model.SillyTavernMarketAsset
import me.rerere.rikkahub.data.model.SillyTavernResourceImport
import me.rerere.rikkahub.data.model.parseSillyTavernContentIndex
import me.rerere.rikkahub.data.model.parseSillyTavernResources
import me.rerere.rikkahub.ui.pages.assistant.detail.ParsedCard
import me.rerere.rikkahub.ui.pages.assistant.detail.parseTavernCard
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.PngTextChunk
import okhttp3.OkHttpClient
import okhttp3.Request

private const val SILLY_TAVERN_OFFICIAL_INDEX =
    "https://raw.githubusercontent.com/SillyTavern/SillyTavern/release/default/content/index.json"

data class SillyTavernResourcesUiState(
    val loading: Boolean = false,
    val importingAsset: String? = null,
    val assets: List<SillyTavernMarketAsset> = emptyList(),
    val searchQuery: String = "",
    val selectedType: String? = null,
)

data class SillyTavernImportReport(
    val assistantCount: Int = 0,
    val resources: SillyTavernResourceImport = SillyTavernResourceImport(),
) {
    val appliedCount: Int
        get() = assistantCount + resources.globalResourceCount + resources.regexes.size

    fun summary(): String = buildList {
        if (assistantCount > 0) add("characters $assistantCount")
        if (resources.promptPresets.isNotEmpty()) add("chat presets ${resources.promptPresets.size}")
        if (resources.contextPresets.isNotEmpty()) add("context presets ${resources.contextPresets.size}")
        if (resources.instructPresets.isNotEmpty()) add("instruct presets ${resources.instructPresets.size}")
        if (resources.systemPromptPresets.isNotEmpty()) add("system prompts ${resources.systemPromptPresets.size}")
        if (resources.lorebooks.isNotEmpty()) add("lorebooks ${resources.lorebooks.size}")
        if (resources.quickMessages.isNotEmpty()) add("quick replies ${resources.quickMessages.size}")
        if (resources.regexes.isNotEmpty()) add("regex scripts ${resources.regexes.size}")
    }.joinToString()
}

class SillyTavernResourcesVM(
    private val settingsStore: SettingsStore,
    private val filesManager: FilesManager,
    private val httpClient: OkHttpClient,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(SillyTavernResourcesUiState())
    val uiState = _uiState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSelectedType(type: String?) {
        _uiState.update { it.copy(selectedType = type) }
    }

    fun importFromFile(
        context: Context,
        uri: Uri,
        onResult: (Boolean, String) -> Unit,
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loading = true) }
            try {
                val fileName = filesManager.getFileNameFromUri(uri).orEmpty()
                val mimeType = filesManager.getFileMimeType(uri).orEmpty()
                val report = if (mimeType == "image/png" || fileName.endsWith(".png", ignoreCase = true)) {
                    importCharacterPngFromUri(appContext, uri)
                } else {
                    val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read file")
                    importJsonText(text, fileName.substringBeforeLast('.', missingDelimiterValue = fileName).ifBlank { "import" })
                }
                withContext(Dispatchers.Main) {
                    onResult(true, report.summary().ifBlank { "no compatible resources" })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Unknown error")
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun loadOfficialAssets(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loading = true) }
            try {
                val text = downloadText(SILLY_TAVERN_OFFICIAL_INDEX)
                val assets = parseSillyTavernContentIndex(text)
                    .sortedWith(compareBy<SillyTavernMarketAsset> { it.type }.thenBy { it.displayName })
                _uiState.update { it.copy(assets = assets) }
                withContext(Dispatchers.Main) {
                    onResult(true, assets.size.toString())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "${e.message ?: "Unknown error"}; check GitHub access or proxy/VPN")
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun importAsset(
        asset: SillyTavernMarketAsset,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(importingAsset = asset.filename) }
            try {
                val report = if (asset.type == "character") {
                    importCharacterPngBytes(
                        bytes = downloadBytes(asset.downloadUrl),
                        displayName = asset.filename.substringAfterLast('/'),
                    )
                } else {
                    importJsonText(
                        jsonText = downloadText(asset.downloadUrl),
                        fallbackName = asset.displayName,
                    )
                }
                withContext(Dispatchers.Main) {
                    onResult(true, report.summary().ifBlank { asset.displayName })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Unknown error")
                }
            } finally {
                _uiState.update { it.copy(importingAsset = null) }
            }
        }
    }

    private suspend fun importJsonText(
        jsonText: String,
        fallbackName: String,
    ): SillyTavernImportReport {
        parseCharacterCardJsonOrNull(jsonText, avatarImage = null)?.let { parsed ->
            return applyParsedCard(parsed)
        }

        val resources = parseSillyTavernResources(jsonText, fallbackName)
        if (resources.detectedResourceCount == 0) {
            error("No compatible SillyTavern resources found")
        }
        applyResources(resources)
        return SillyTavernImportReport(resources = resources)
    }

    private suspend fun importCharacterPngFromUri(
        context: Context,
        uri: Uri,
    ): SillyTavernImportReport {
        val base64Data = ImageUtils.getTavernCharacterMeta(context, uri).getOrElse { throw it }
        val jsonText = String(Base64.decode(base64Data, Base64.DEFAULT))
        val avatarImage = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
        val parsed = parseCharacterCardJsonOrNull(jsonText, avatarImage)
            ?: error("No SillyTavern character card metadata found")
        return applyParsedCard(parsed)
    }

    private suspend fun importCharacterPngBytes(
        bytes: ByteArray,
        displayName: String,
    ): SillyTavernImportReport {
        val chunks = PngTextChunk.readTextChunks(bytes)
        val base64Data = chunks["ccv3"] ?: chunks["chara"] ?: error("No SillyTavern character card metadata found")
        val jsonText = String(Base64.decode(base64Data, Base64.DEFAULT))
        val saved = filesManager.saveUploadFromBytes(
            bytes = bytes,
            displayName = displayName,
            mimeType = "image/png",
        )
        val avatarImage = filesManager.getFile(saved).toUri().toString()
        val parsed = parseCharacterCardJsonOrNull(jsonText, avatarImage)
            ?: error("No SillyTavern character card metadata found")
        return applyParsedCard(parsed)
    }

    private fun parseCharacterCardJsonOrNull(
        jsonText: String,
        avatarImage: String?,
    ): ParsedCard? {
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null
        if (!looksLikeCharacterCard(root)) return null
        return runCatching {
            parseTavernCard(
                json = root,
                avatarImage = avatarImage,
                missingNameMessage = "Missing name field",
            )
        }.getOrNull()
    }

    private fun looksLikeCharacterCard(root: JsonObject): Boolean {
        val spec = root.stringValue("spec")
        val data = root["data"]?.asObjectOrNull() ?: root
        if (spec?.startsWith("chara_card") == true) return true
        if (!data.containsKey("name") || data.containsKey("entries")) return false
        return listOf(
            "first_mes",
            "personality",
            "scenario",
            "mes_example",
            "system_prompt",
            "alternate_greetings",
            "character_book",
        ).any(data::containsKey)
    }

    private suspend fun applyParsedCard(parsed: ParsedCard): SillyTavernImportReport {
        settingsStore.update { settings ->
            settings.copy(
                assistants = settings.assistants + parsed.assistant,
                lorebooks = parsed.lorebook?.let { settings.lorebooks + it } ?: settings.lorebooks,
                assistantId = parsed.assistant.id,
            )
        }
        return SillyTavernImportReport(
            assistantCount = 1,
            resources = SillyTavernResourceImport(lorebooks = listOfNotNull(parsed.lorebook)),
        )
    }

    private suspend fun applyResources(resources: SillyTavernResourceImport) {
        settingsStore.update { settings ->
            settings.copy(
                promptPresets = settings.promptPresets.mergeDistinctByName(resources.promptPresets) { it.name },
                contextPresets = settings.contextPresets.mergeDistinctByName(resources.contextPresets) { it.name },
                instructPresets = settings.instructPresets.mergeDistinctByName(resources.instructPresets) { it.name },
                systemPromptPresets = settings.systemPromptPresets.mergeDistinctByName(resources.systemPromptPresets) { it.name },
                lorebooks = settings.lorebooks.mergeDistinctByName(resources.lorebooks) { it.name },
                quickMessages = settings.quickMessages.mergeDistinctQuickMessages(resources.quickMessages),
                assistants = if (resources.regexes.isEmpty()) {
                    settings.assistants
                } else {
                    settings.assistants.map { assistant ->
                        if (assistant.id == settings.assistantId) {
                            assistant.copy(regexes = assistant.regexes + resources.regexes)
                        } else {
                            assistant
                        }
                    }
                },
            )
        }
    }

    private fun downloadText(url: String): String =
        downloadBytes(url).toString(Charsets.UTF_8)

    private fun downloadBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.github+json, text/plain, */*")
            .header("User-Agent", "RikkaHub-ST/${BuildConfig.VERSION_NAME}")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return response.body.bytes()
        }
    }
}

private fun <T> List<T>.mergeDistinctByName(
    incoming: List<T>,
    nameOf: (T) -> String,
): List<T> =
    incoming.fold(this) { acc, item ->
        val name = nameOf(item).trim()
        if (name.isNotEmpty() && acc.any { existing -> nameOf(existing).trim().equals(name, ignoreCase = true) }) {
            acc
        } else {
            acc + item
        }
    }

private fun List<me.rerere.rikkahub.data.model.QuickMessage>.mergeDistinctQuickMessages(
    incoming: List<me.rerere.rikkahub.data.model.QuickMessage>,
): List<me.rerere.rikkahub.data.model.QuickMessage> =
    incoming.fold(this) { acc, item ->
        if (acc.any { existing ->
                existing.sourceSet.equals(item.sourceSet, ignoreCase = true) &&
                    existing.title.equals(item.title, ignoreCase = true) &&
                    existing.content == item.content
            }
        ) {
            acc
        } else {
            acc + item
        }
    }

private fun JsonObject.stringValue(key: String): String? =
    this[key]?.let { element ->
        runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
    }

private fun JsonElement.asObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()
