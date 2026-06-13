package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.PromptInjection

/**
 * 用户角色 (Persona) 注入转换器
 *
 * 将当前选中的 Persona 描述按其配置的位置注入提示词，
 * 复用 [applyInjections] 的定位逻辑。Persona 名称/描述同时通过
 * [PlaceholderTransformer] 的 {{user}}/{{persona}} 宏生效。
 *
 * 需在 [PlaceholderTransformer] 之前执行，以便描述中的宏可被解析。
 */
object PersonaTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val selectedId = ctx.settings.selectedPersonaId ?: return messages
        val persona = ctx.settings.personas.firstOrNull { it.id == selectedId } ?: return messages
        if (!persona.enabled || persona.description.isBlank()) return messages

        val injection = PromptInjection.ModeInjection(
            name = "Persona: ${persona.name}",
            enabled = true,
            priority = 50,
            position = persona.position,
            content = persona.description,
            injectDepth = persona.injectDepth,
            role = persona.role,
        )

        return applyInjections(messages, mapOf(persona.position to listOf(injection)))
    }
}
