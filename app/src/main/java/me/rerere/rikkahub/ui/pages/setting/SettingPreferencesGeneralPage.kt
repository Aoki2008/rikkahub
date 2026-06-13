package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.ChatGroup
import me.rerere.rikkahub.data.model.GroupActivationStrategy
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesGeneralPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_preferences_general))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                    "create_new_conversation_on_start",
                    true
                )
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc)) },
                        trailingContent = {
                            Switch(
                                checked = createNewConversationOnStart,
                                onCheckedChange = { createNewConversationOnStart = it }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.sendOnEnter,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(sendOnEnter = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showMessageJumper,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showMessageJumper = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.showMessageJumper) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_title)) },
                            supportingContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = displaySetting.messageJumperOnLeft,
                                    onCheckedChange = {
                                        updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it))
                                    }
                                )
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableAutoScroll,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableAutoScroll = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_title)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.useAppIconStyleLoadingIndicator,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(useAppIconStyleLoadingIndicator = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableBlurEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableBlurEffect = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableMessageGenerationHapticEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.skipCropImage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(skipCropImage = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.pasteLongTextAsFile,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.pasteLongTextAsFile) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_threshold_title)) },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = displaySetting.pasteLongTextThreshold.toFloat(),
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it.toInt()))
                                        },
                                        valueRange = 100f..10000f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${displaySetting.pasteLongTextThreshold}")
                                }
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableVolumeKeyScroll,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.enableVolumeKeyScroll) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_ratio)) },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = displaySetting.volumeKeyScrollRatio,
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                                        },
                                        valueRange = 0.25f..1.0f,
                                        steps = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%")
                                }
                            }
                        )
                    }
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_tts_settings)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.ttsOnlyReadQuoted,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.autoPlayTTSAfterGeneration,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it))
                                }
                            )
                        },
                    )
                }
            }

            item {
                PersonaSection(
                    settings = settings,
                    onUpdateSettings = { vm.updateSettings(it) },
                )
            }

            item {
                ChatGroupSection(
                    settings = settings,
                    onUpdateSettings = { vm.updateSettings(it) },
                )
            }
        }
    }
}

/**
 * 用户角色 (Persona) 管理区。
 *
 * 支持新增/编辑/删除 Persona，并选择当前生效的 Persona。
 * 选中的 Persona 名称用于 {{user}} 宏，描述用于 {{persona}} 宏并注入提示词。
 */
@Composable
private fun PersonaSection(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
) {
    var editing by remember { mutableStateOf<Persona?>(null) }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("User Personas") },
    ) {
        // "无" 选项：清除当前选择
        item(
            headlineContent = { Text("None") },
            supportingContent = { Text("Use global nickname only") },
            leadingContent = {
                RadioButton(
                    selected = settings.selectedPersonaId == null,
                    onClick = { onUpdateSettings(settings.copy(selectedPersonaId = null)) },
                )
            },
            onClick = { onUpdateSettings(settings.copy(selectedPersonaId = null)) },
        )

        settings.personas.forEach { persona ->
            item(
                headlineContent = { Text(persona.name.ifBlank { "Unnamed persona" }) },
                supportingContent = {
                    if (persona.description.isNotBlank()) {
                        Text(persona.description.take(80), maxLines = 2)
                    }
                },
                leadingContent = {
                    RadioButton(
                        selected = settings.selectedPersonaId == persona.id,
                        onClick = {
                            onUpdateSettings(settings.copy(selectedPersonaId = persona.id))
                        },
                    )
                },
                trailingContent = {
                    IconButton(onClick = {
                        onUpdateSettings(
                            settings.copy(
                                personas = settings.personas.filterNot { it.id == persona.id },
                                selectedPersonaId = settings.selectedPersonaId
                                    ?.takeIf { it != persona.id },
                            )
                        )
                    }) {
                        Icon(Lucide.Trash2, contentDescription = "Delete")
                    }
                },
                onClick = { editing = persona },
            )
        }

        item(
            headlineContent = { Text("Add persona") },
            leadingContent = { Icon(Lucide.Plus, contentDescription = null) },
            onClick = { editing = Persona() },
        )
    }

    editing?.let { current ->
        PersonaEditDialog(
            persona = current,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                val exists = settings.personas.any { it.id == updated.id }
                val newPersonas = if (exists) {
                    settings.personas.map { if (it.id == updated.id) updated else it }
                } else {
                    settings.personas + updated
                }
                onUpdateSettings(settings.copy(personas = newPersonas))
                editing = null
            },
        )
    }
}

@Composable
private fun PersonaEditDialog(
    persona: Persona,
    onDismiss: () -> Unit,
    onConfirm: (Persona) -> Unit,
) {
    var name by remember(persona.id) { mutableStateOf(persona.name) }
    var description by remember(persona.id) { mutableStateOf(persona.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name ({{user}})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description ({{persona}})") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(persona.copy(name = name.trim(), description = description.trim()))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * 群聊管理区。
 *
 * 支持新增/编辑/删除群聊；编辑时可命名、选择成员(助手)、选择激活策略。
 * 生成逻辑(ChatService 集成)尚未接入，此处仅维护群聊配置。
 */
@Composable
private fun ChatGroupSection(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
) {
    var editing by remember { mutableStateOf<ChatGroup?>(null) }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("Group Chats") },
    ) {
        settings.chatGroups.forEach { group ->
            item(
                headlineContent = { Text(group.name.ifBlank { "Unnamed group" }) },
                supportingContent = {
                    Text("${group.memberIds.size} members · ${group.activationStrategy.name.lowercase()}")
                },
                trailingContent = {
                    IconButton(onClick = {
                        onUpdateSettings(
                            settings.copy(chatGroups = settings.chatGroups.filterNot { it.id == group.id })
                        )
                    }) {
                        Icon(Lucide.Trash2, contentDescription = "Delete")
                    }
                },
                onClick = { editing = group },
            )
        }

        item(
            headlineContent = { Text("Add group") },
            leadingContent = { Icon(Lucide.Plus, contentDescription = null) },
            onClick = { editing = ChatGroup() },
        )
    }

    editing?.let { current ->
        ChatGroupEditDialog(
            group = current,
            memberOptions = settings.assistants.map { it.id to it.name.ifBlank { "Unnamed assistant" } },
            onDismiss = { editing = null },
            onConfirm = { updated ->
                val exists = settings.chatGroups.any { it.id == updated.id }
                val newGroups = if (exists) {
                    settings.chatGroups.map { if (it.id == updated.id) updated else it }
                } else {
                    settings.chatGroups + updated
                }
                onUpdateSettings(settings.copy(chatGroups = newGroups))
                editing = null
            },
        )
    }
}

@Composable
private fun ChatGroupEditDialog(
    group: ChatGroup,
    memberOptions: List<Pair<kotlin.uuid.Uuid, String>>,
    onDismiss: () -> Unit,
    onConfirm: (ChatGroup) -> Unit,
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var memberIds by remember(group.id) { mutableStateOf(group.memberIds) }
    var strategy by remember(group.id) { mutableStateOf(group.activationStrategy) }
    var allowSelf by remember(group.id) { mutableStateOf(group.allowSelfResponses) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group Chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Members", style = MaterialTheme.typography.labelLarge)
                memberOptions.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                memberIds = if (id in memberIds) memberIds - id else memberIds + id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = id in memberIds,
                            onCheckedChange = {
                                memberIds = if (id in memberIds) memberIds - id else memberIds + id
                            },
                        )
                        Text(label)
                    }
                }

                Text("Activation strategy", style = MaterialTheme.typography.labelLarge)
                GroupActivationStrategy.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { strategy = option },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = strategy == option, onClick = { strategy = option })
                        Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { allowSelf = !allowSelf },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = allowSelf, onCheckedChange = { allowSelf = it })
                    Text("Allow consecutive self-responses")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    group.copy(
                        name = name.trim(),
                        memberIds = memberIds,
                        activationStrategy = strategy,
                        allowSelfResponses = allowSelf,
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
