package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.AuthorsNote
import me.rerere.rikkahub.data.model.PromptInjection

/**
 * 作者注释 (Author's Note) 注入转换器
 *
 * 将助手配置的 Author's Note 按其位置/深度注入到消息中，复用 [applyInjections]。
 * 需在 [PlaceholderTransformer] 之前执行，以便注释中的宏被解析。
 */
object AuthorsNoteTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = applyAuthorsNote(messages, ctx.assistant.authorsNote)
}

/**
 * 核心逻辑（纯函数，便于测试）。
 *
 * - 未启用或内容为空 → 原样返回
 * - interval > 1 时，仅当非系统消息条数为 interval 的整数倍时插入
 */
internal fun applyAuthorsNote(
    messages: List<UIMessage>,
    note: AuthorsNote,
): List<UIMessage> {
    if (!note.enabled || note.content.isBlank()) return messages

    if (note.interval > 1) {
        val chatCount = messages.count { it.role != MessageRole.SYSTEM }
        if (chatCount == 0 || chatCount % note.interval != 0) return messages
    }

    val injection = PromptInjection.ModeInjection(
        name = "Author's Note",
        enabled = true,
        priority = 75,
        position = note.position,
        content = note.content,
        injectDepth = note.injectDepth,
        role = note.role,
    )
    return applyInjections(messages, mapOf(note.position to listOf(injection)))
}
