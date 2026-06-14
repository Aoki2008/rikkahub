package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import me.rerere.rikkahub.data.model.PromptPreset
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
    val bound = presets.firstOrNull { it.id == assistant.promptPresetId }
    var editing by remember { mutableStateOf<PromptPreset?>(null) }

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

            if (bound != null) {
                OutlinedButton(
                    onClick = { editing = bound },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit blocks of \"${bound.name.ifBlank { "Preset" }}\"")
                }
            }
        }
    }

    editing?.let { current ->
        PromptPresetEditorDialog(
            preset = current,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    settingsStore.update { s ->
                        s.copy(promptPresets = s.promptPresets.map { if (it.id == updated.id) updated else it })
                    }
                }
                editing = null
            },
        )
    }
}

/**
 * 预设块编辑器：按顺序列出各块，可开关、上移/下移；文本块（非 marker）可直接编辑内容。
 */
@Composable
private fun PromptPresetEditorDialog(
    preset: PromptPreset,
    onDismiss: () -> Unit,
    onSave: (PromptPreset) -> Unit,
) {
    var draft by remember(preset.id) { mutableStateOf(preset) }
    val blocksById = draft.prompts.associateBy { it.identifier }

    fun moveOrder(index: Int, delta: Int) {
        val target = index + delta
        if (target !in draft.promptOrder.indices) return
        val newOrder = draft.promptOrder.toMutableList()
        val tmp = newOrder[index]; newOrder[index] = newOrder[target]; newOrder[target] = tmp
        draft = draft.copy(promptOrder = newOrder)
    }

    fun toggle(index: Int, enabled: Boolean) {
        val newOrder = draft.promptOrder.toMutableList()
        newOrder[index] = newOrder[index].copy(enabled = enabled)
        draft = draft.copy(promptOrder = newOrder)
    }

    fun editContent(identifier: String, content: String) {
        draft = draft.copy(prompts = draft.prompts.map {
            if (it.identifier == identifier) it.copy(content = content) else it
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.name.ifBlank { "Preset" }) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.promptOrder.forEachIndexed { index, entry ->
                    val block = blocksById[entry.identifier] ?: return@forEachIndexed
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = { toggle(index, it) },
                            )
                            Text(
                                text = block.name.ifBlank { block.identifier } +
                                    if (block.marker) "  ·marker" else "",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { moveOrder(index, -1) }, enabled = index > 0) { Text("↑") }
                            TextButton(
                                onClick = { moveOrder(index, 1) },
                                enabled = index < draft.promptOrder.lastIndex,
                            ) { Text("↓") }
                        }
                        if (!block.marker) {
                            OutlinedTextField(
                                value = block.content,
                                onValueChange = { editContent(block.identifier, it) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = 6,
                                enabled = entry.enabled,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
