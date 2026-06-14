package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ChatGroup
import me.rerere.rikkahub.data.model.activeMembers

@Composable
fun rememberAssistantState(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit
): AssistantState {
    return remember(settings, onUpdateSettings) {
        AssistantState(settings, onUpdateSettings)
    }
}

class AssistantState(
    private val settings: Settings,
    private val onUpdateSettings: (Settings) -> Unit
) {
    private var _currentAssistant by mutableStateOf(
        settings.getCurrentAssistant()
    )
    val currentAssistant get() = _currentAssistant

    /** 当前选中的群聊（若处于群聊模式），否则为 null。 */
    val currentGroup: ChatGroup?
        get() = settings.selectedGroupId?.let { id -> settings.chatGroups.firstOrNull { it.id == id } }

    fun setSelectAssistant(assistant: Assistant) {
        onUpdateSettings(
            settings.copy(
                assistantId = assistant.id,
                // 选择单个助手即退出群聊模式
                selectedGroupId = null,
            )
        )
    }

    /**
     * 进入群聊模式：记录所选群组，并把 assistantId 指向首位有效成员
     * （供既有单人代码路径回退使用）。
     */
    fun setSelectGroup(group: ChatGroup) {
        val firstMember = group.activeMembers().firstOrNull { settings.getAssistantById(it) != null }
            ?: group.memberIds.firstOrNull { settings.getAssistantById(it) != null }
        onUpdateSettings(
            settings.copy(
                selectedGroupId = group.id,
                assistantId = firstMember ?: settings.assistantId,
            )
        )
    }
}
