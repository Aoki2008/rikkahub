package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TextCompletionPresetImport
import me.rerere.rikkahub.data.model.parseSillyTavernTextCompletionPreset
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun TextCompletionPresetCard(
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val settingsStore: SettingsStore = koinInject()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val (jsonText, name) = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        .use { it?.readText() } ?: error("Cannot read file")
                    text to queryTextCompletionPresetDisplayName(context, uri)
                }
                val imported = parseSillyTavernTextCompletionPreset(
                    Json.parseToJsonElement(jsonText).jsonObject,
                    name,
                )
                when (imported) {
                    is TextCompletionPresetImport.Context -> {
                        settingsStore.update { it.copy(contextPresets = it.contextPresets + imported.preset) }
                        onUpdate(
                            assistant.copy(
                                promptPresetId = null,
                                contextPresetId = imported.preset.id,
                            )
                        )
                        "Context preset imported & bound"
                    }

                    is TextCompletionPresetImport.Instruct -> {
                        settingsStore.update { it.copy(instructPresets = it.instructPresets + imported.preset) }
                        onUpdate(
                            assistant.copy(
                                promptPresetId = null,
                                instructPresetId = imported.preset.id,
                            )
                        )
                        "Instruct preset imported & bound"
                    }

                    is TextCompletionPresetImport.SystemPrompt -> {
                        settingsStore.update {
                            it.copy(systemPromptPresets = it.systemPromptPresets + imported.preset)
                        }
                        onUpdate(
                            assistant.copy(
                                promptPresetId = null,
                                systemPromptPresetId = imported.preset.id,
                            )
                        )
                        "System prompt preset imported & bound"
                    }

                    TextCompletionPresetImport.Unknown -> error("Not a SillyTavern text-completion preset")
                }
            }.onSuccess { message ->
                toaster.show(message, type = ToastType.Success)
            }.onFailure { error ->
                error.printStackTrace()
                toaster.show(error.message ?: "Import failed", type = ToastType.Error)
            }
        }
    }

    Card(
        colors = CustomColors.cardColorsOnSurfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Text Completion Presets", style = MaterialTheme.typography.titleSmall)
            Text(
                "Import and bind SillyTavern context, instruct, and system prompt presets for classic/textgen prompting.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Context", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = assistant.contextPresetId == null,
                    onClick = { onUpdate(assistant.copy(contextPresetId = null)) },
                    label = { Text("None") },
                )
                settings.contextPresets.forEach { preset ->
                    FilterChip(
                        selected = preset.id == assistant.contextPresetId,
                        onClick = {
                            onUpdate(
                                assistant.copy(
                                    promptPresetId = null,
                                    contextPresetId = preset.id,
                                )
                            )
                        },
                        label = { Text(preset.name.ifBlank { "Context" }) },
                    )
                }
            }

            Text("Instruct", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = assistant.instructPresetId == null,
                    onClick = { onUpdate(assistant.copy(instructPresetId = null)) },
                    label = { Text("None") },
                )
                settings.instructPresets.forEach { preset ->
                    FilterChip(
                        selected = preset.id == assistant.instructPresetId,
                        onClick = {
                            onUpdate(
                                assistant.copy(
                                    promptPresetId = null,
                                    instructPresetId = preset.id,
                                )
                            )
                        },
                        label = { Text(preset.name.ifBlank { "Instruct" }) },
                    )
                }
            }

            Text("System Prompt", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = assistant.systemPromptPresetId == null,
                    onClick = { onUpdate(assistant.copy(systemPromptPresetId = null)) },
                    label = { Text("None") },
                )
                settings.systemPromptPresets.forEach { preset ->
                    FilterChip(
                        selected = preset.id == assistant.systemPromptPresetId,
                        onClick = {
                            onUpdate(
                                assistant.copy(
                                    promptPresetId = null,
                                    systemPromptPresetId = preset.id,
                                )
                            )
                        },
                        label = { Text(preset.name.ifBlank { "System Prompt" }) },
                    )
                }
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import text-completion preset")
            }
        }
    }
}

private fun queryTextCompletionPresetDisplayName(context: Context, uri: Uri): String {
    var name = "Imported Preset"
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                            .removeSuffix(".json")
                            .removeSuffix(".settings")
                            .ifBlank { name }
                    }
                }
            }
    }
    return name
}
