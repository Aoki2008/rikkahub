package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.PromptAssemblyInput
import me.rerere.rikkahub.data.ai.PromptManagerAssembler
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import kotlin.uuid.Uuid

/**
 * Chat Completion 预设（酒馆预设）组装转换器。
 *
 * 当助手绑定了 [me.rerere.rikkahub.data.model.PromptPreset] 时，按预设的 promptOrder
 * 重建发送给 API 的消息列表：marker 块用角色卡字段/人设/世界书/聊天记录填充，文本块
 * （main/越狱等）原样输出。世界书激活复用 [collectInjections]。
 *
 * 需作为**第一个**输入转换器运行；绑定预设时 [PromptInjectionTransformer] 与
 * [PersonaTransformer] 会自动跳过（其职责由预设的 marker 承担）。其余转换器
 * （AuthorsNote/Placeholder/Document/Ocr）照常在其后运行。
 */
object PromptManagerTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = buildPromptManagerMessages(
        assistant = ctx.assistant,
        settings = ctx.settings,
        messages = messages,
        conversationModeInjectionIds = ctx.conversationModeInjectionIds,
        conversationLorebookIds = ctx.conversationLorebookIds,
    ) ?: messages
}

/**
 * 核心组装逻辑（纯函数，便于测试）。
 *
 * @return 绑定了预设时返回组装后的消息列表；未绑定/找不到预设时返回 null（调用方保持原样）。
 */
internal fun buildPromptManagerMessages(
    assistant: Assistant,
    settings: Settings,
    messages: List<UIMessage>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
): List<UIMessage>? {
    val presetId = assistant.promptPresetId ?: return null
    val preset = settings.promptPresets.firstOrNull { it.id == presetId } ?: return null

    // 聊天记录 = 去掉(由 systemPrompt 生成的)系统消息后的全部消息
    val chatHistory = messages.filterNot { it.role == MessageRole.SYSTEM }
    val card = assistant.characterCard

    val persona = settings.selectedPersonaId
        ?.let { id -> settings.personas.firstOrNull { it.id == id } }
        ?.takeIf { it.enabled }

    // 复用既有世界书激活逻辑，按位置拆分为 before / after
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

    return PromptManagerAssembler.assemble(
        PromptAssemblyInput(
            preset = preset,
            description = card?.description.orEmpty(),
            personality = card?.personality.orEmpty(),
            scenario = card?.scenario.orEmpty(),
            exampleMessages = card?.mesExample.orEmpty(),
            personaDescription = persona?.description.orEmpty(),
            worldInfoBefore = before,
            worldInfoAfter = after,
            chatHistory = chatHistory,
        )
    )
}

