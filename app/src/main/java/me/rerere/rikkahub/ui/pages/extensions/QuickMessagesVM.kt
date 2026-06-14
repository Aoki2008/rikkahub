package me.rerere.rikkahub.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.parseSillyTavernQuickReplies
import kotlin.uuid.Uuid

class QuickMessagesVM(
    private val settingsStore: SettingsStore
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun addQuickMessage(title: String, content: String) {
        updateQuickMessages(
            settings.value.quickMessages + QuickMessage(
                title = title,
                content = content,
            )
        )
    }

    fun importSillyTavernQuickReplies(jsonText: String): Int {
        val quickMessages = parseSillyTavernQuickReplies(Json.parseToJsonElement(jsonText))
        if (quickMessages.isNotEmpty()) {
            updateQuickMessages(settings.value.quickMessages + quickMessages)
        }
        return quickMessages.size
    }

    fun updateQuickMessage(updated: QuickMessage) {
        updateQuickMessages(
            settings.value.quickMessages.map { quickMessage ->
                if (quickMessage.id == updated.id) updated else quickMessage
            }
        )
    }

    fun deleteQuickMessage(id: Uuid) {
        updateQuickMessages(
            settings.value.quickMessages.filterNot { quickMessage ->
                quickMessage.id == id
            }
        )
    }

    private fun updateQuickMessages(quickMessages: List<QuickMessage>) {
        val validIds = quickMessages.map { it.id }.toSet()
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    quickMessages = quickMessages,
                    assistants = settings.assistants.map { assistant ->
                        assistant.copy(
                            quickMessageIds = assistant.quickMessageIds.filter { it in validIds }.toSet()
                        )
                    }
                )
            }
        }
    }
}
