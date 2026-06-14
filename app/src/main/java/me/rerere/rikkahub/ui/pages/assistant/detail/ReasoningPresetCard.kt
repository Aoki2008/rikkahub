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
import me.rerere.rikkahub.data.model.looksLikeReasoningPreset
import me.rerere.rikkahub.data.model.parseSillyTavernReasoningPreset
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun ReasoningPresetCard(
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
                    text to queryReasoningPresetDisplayName(context, uri)
                }
                val root = Json.parseToJsonElement(jsonText).jsonObject
                require(root.looksLikeReasoningPreset()) {
                    "Not a SillyTavern reasoning preset"
                }
                val preset = parseSillyTavernReasoningPreset(root, name)
                settingsStore.update { it.copy(reasoningPresets = it.reasoningPresets + preset) }
                onUpdate(assistant.copy(reasoningPresetId = preset.id))
                preset.name.ifBlank { name }
            }.onSuccess { name ->
                toaster.show("Reasoning preset imported & bound: $name", type = ToastType.Success)
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
            Text("Reasoning Preset", style = MaterialTheme.typography.titleSmall)
            Text(
                "Bind SillyTavern reasoning delimiters so model thinking is split from the final reply.",
                style = MaterialTheme.typography.bodySmall,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = assistant.reasoningPresetId == null,
                    onClick = { onUpdate(assistant.copy(reasoningPresetId = null)) },
                    label = { Text("Default <think>") },
                )
                settings.reasoningPresets.forEach { preset ->
                    FilterChip(
                        selected = preset.id == assistant.reasoningPresetId,
                        onClick = { onUpdate(assistant.copy(reasoningPresetId = preset.id)) },
                        label = { Text(preset.name.ifBlank { "Reasoning" }) },
                    )
                }
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import reasoning preset")
            }
        }
    }
}

private fun queryReasoningPresetDisplayName(context: Context, uri: Uri): String {
    var name = "Imported Reasoning Preset"
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
