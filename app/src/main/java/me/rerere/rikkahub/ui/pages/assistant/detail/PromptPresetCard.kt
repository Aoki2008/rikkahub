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
import me.rerere.rikkahub.data.model.parseSillyTavernPreset
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

/**
 * Chat Completion 预设（酒馆预设）卡片：导入 SillyTavern 预设 JSON、选择/解绑当前助手使用的预设。
 *
 * 绑定后，该助手发送给 API 的提示词将由 Prompt Manager 按预设组装。
 */
@Composable
fun PromptPresetCard(
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val settingsStore: SettingsStore = koinInject()

    val presets = settings.promptPresets

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val (jsonText, name) = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        .use { it?.readText() } ?: error("Cannot read file")
                    text to queryDisplayName(context, uri)
                }
                val preset = parseSillyTavernPreset(
                    Json.parseToJsonElement(jsonText).jsonObject, name
                )
                require(preset.prompts.isNotEmpty()) { "Not a valid Chat Completion preset" }
                settingsStore.update { it.copy(promptPresets = it.promptPresets + preset) }
                onUpdate(assistant.copy(promptPresetId = preset.id))
            }.onSuccess {
                toaster.show("Preset imported & bound", type = ToastType.Success)
            }.onFailure { e ->
                e.printStackTrace()
                toaster.show(e.message ?: "Import failed", type = ToastType.Error)
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
            Text("Chat Completion Preset (酒馆预设)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Import a SillyTavern preset to control the full prompt (ordered blocks, jailbreak, samplers). When bound, this assistant's prompt is assembled by the Prompt Manager.",
                style = MaterialTheme.typography.bodySmall,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = assistant.promptPresetId == null,
                    onClick = { onUpdate(assistant.copy(promptPresetId = null)) },
                    label = { Text("None") },
                )
                presets.forEach { preset ->
                    FilterChip(
                        selected = preset.id == assistant.promptPresetId,
                        onClick = { onUpdate(assistant.copy(promptPresetId = preset.id)) },
                        label = { Text(preset.name.ifBlank { "Preset" }) },
                    )
                }
            }

            OutlinedButton(
                onClick = { launcher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import preset (JSON)")
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
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
