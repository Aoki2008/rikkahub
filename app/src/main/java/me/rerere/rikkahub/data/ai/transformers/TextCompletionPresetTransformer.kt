package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.TextCompletionAssemblyInput
import me.rerere.rikkahub.data.ai.TextCompletionPromptAssembler
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import kotlin.uuid.Uuid

/**
 * SillyTavern Text Completion 预设转换器。
 *
 * 当助手绑定 context + instruct 预设时，将聊天补全消息转换为 classic/textgen
 * 使用的一整段 prompt。Chat Completion Prompt Manager 优先级更高；若助手已绑定
 * Chat Completion 预设，本转换器保持原样。
 */
object TextCompletionPresetTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = buildTextCompletionPresetMessages(
        assistant = ctx.assistant,
        settings = ctx.settings,
        messages = messages,
        conversationModeInjectionIds = ctx.conversationModeInjectionIds,
        conversationLorebookIds = ctx.conversationLorebookIds,
    ) ?: messages
}

internal fun hasResolvedTextCompletionPresetBinding(
    assistant: Assistant,
    settings: Settings,
): Boolean {
    if (assistant.promptPresetId != null) return false
    val contextId = assistant.contextPresetId ?: return false
    val instructId = assistant.instructPresetId ?: return false
    return settings.contextPresets.any { it.id == contextId } &&
        settings.instructPresets.any { it.id == instructId }
}

internal fun buildTextCompletionStopSequences(
    assistant: Assistant,
    settings: Settings,
): List<String> {
    if (assistant.promptPresetId != null) return emptyList()
    val contextPresetId = assistant.contextPresetId ?: return emptyList()
    val instructPresetId = assistant.instructPresetId ?: return emptyList()
    val contextPreset = settings.contextPresets.firstOrNull { it.id == contextPresetId } ?: return emptyList()
    val instructPreset = settings.instructPresets.firstOrNull { it.id == instructPresetId } ?: return emptyList()
    val persona = settings.selectedPersonaId
        ?.let { id -> settings.personas.firstOrNull { it.id == id } }
        ?.takeIf { it.enabled }
    val userName = persona?.name?.ifBlank { null } ?: "User"
    val characterName = assistant.name.ifBlank { "Assistant" }
    val result = linkedSetOf<String>()

    fun renderMacros(value: String): String =
        value
            .replace("{{user}}", userName, ignoreCase = true)
            .replace("{{char}}", characterName, ignoreCase = true)

    fun replaceNameMacro(value: String, name: String): String =
        value.replace(Regex("\\{\\{name\\}\\}", RegexOption.IGNORE_CASE), name)

    fun addStop(value: String) {
        if (value.trim().isNotEmpty()) {
            result += value
        }
    }

    fun addInstructSequence(value: String) {
        if (value.isEmpty() || value.trim().isEmpty()) return
        val wrapped = if (instructPreset.wrap) "\n$value" else value
        addStop(if (instructPreset.macro) renderMacros(wrapped) else wrapped)
    }

    val instructSequences = buildList {
        add(instructPreset.stopSequence)
        if (instructPreset.sequencesAsStopStrings) {
            add(replaceNameMacro(instructPreset.inputSequence, userName))
            add(replaceNameMacro(instructPreset.outputSequence, characterName))
            add(replaceNameMacro(instructPreset.firstOutputSequence, characterName))
            add(replaceNameMacro(instructPreset.lastOutputSequence, characterName))
            add(replaceNameMacro(instructPreset.systemSequence, "System"))
            add(replaceNameMacro(instructPreset.lastSystemSequence, "System"))
        }
    }
    instructSequences
        .joinToString("\n")
        .split("\n")
        .distinct()
        .forEach(::addInstructSequence)

    if (contextPreset.useStopStrings) {
        if (contextPreset.chatStart.isNotBlank()) {
            addStop("\n${renderMacros(contextPreset.chatStart)}")
        }
        if (contextPreset.exampleSeparator.isNotBlank()) {
            addStop("\n${renderMacros(contextPreset.exampleSeparator)}")
        }
    }

    if (contextPreset.namesAsStopStrings) {
        addStop("\n$userName:")
        addStop("\n$characterName:")
    }

    return result.toList()
}

/**
 * 核心组装逻辑（纯函数，便于测试）。
 *
 * @return 已绑定并解析到 text-completion 预设时返回单条 user prompt；未绑定/找不到预设时返回 null。
 */
internal fun buildTextCompletionPresetMessages(
    assistant: Assistant,
    settings: Settings,
    messages: List<UIMessage>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
): List<UIMessage>? {
    if (assistant.promptPresetId != null) return null
    val contextPresetId = assistant.contextPresetId ?: return null
    val instructPresetId = assistant.instructPresetId ?: return null
    val contextPreset = settings.contextPresets.firstOrNull { it.id == contextPresetId } ?: return null
    val instructPreset = settings.instructPresets.firstOrNull { it.id == instructPresetId } ?: return null
    val systemPromptPreset = assistant.systemPromptPresetId
        ?.let { id -> settings.systemPromptPresets.firstOrNull { it.id == id } }

    val chatHistory = messages.filterNot { it.role == MessageRole.SYSTEM }
    val card = assistant.characterCard
    val persona = settings.selectedPersonaId
        ?.let { id -> settings.personas.firstOrNull { it.id == id } }
        ?.takeIf { it.enabled }

    val injections = collectInjections(
        messages = chatHistory,
        assistant = assistant,
        modeInjections = settings.modeInjections,
        lorebooks = settings.lorebooks,
        conversationModeInjectionIds = conversationModeInjectionIds,
        conversationLorebookIds = conversationLorebookIds,
    )
    val before = injections
        .filter { it.position == InjectionPosition.BEFORE_SYSTEM_PROMPT }
        .map { it.content }
    val after = injections
        .filter { it.position != InjectionPosition.BEFORE_SYSTEM_PROMPT }
        .map { it.content }

    val prompt = TextCompletionPromptAssembler.assemble(
        TextCompletionAssemblyInput(
            contextPreset = contextPreset,
            instructPreset = instructPreset,
            systemPromptPreset = systemPromptPreset,
            assistantSystemPrompt = assistant.systemPrompt,
            description = card?.description.orEmpty(),
            personality = card?.personality.orEmpty(),
            scenario = card?.scenario.orEmpty(),
            persona = persona?.description.orEmpty(),
            worldInfoBefore = before,
            worldInfoAfter = after,
            chatHistory = chatHistory,
            userName = persona?.name?.ifBlank { null } ?: "{{user}}",
            characterName = assistant.name.ifBlank { "{{char}}" },
        )
    )
    return listOf(UIMessage.user(prompt))
}
