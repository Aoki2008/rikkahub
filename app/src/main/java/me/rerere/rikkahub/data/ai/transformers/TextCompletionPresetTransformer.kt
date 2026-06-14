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
