package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.ReasoningPreset
import kotlin.time.Clock

private val DEFAULT_REASONING_PRESET = ReasoningPreset(
    name = "Think XML",
    prefix = "<think>",
    suffix = "</think>",
    separator = "\n\n",
)

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformLastAssistantThinking(ctx.reasoningPreset(), finishOpenReasoning = false)
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformLastAssistantThinking(ctx.reasoningPreset(), finishOpenReasoning = true)
    }
}

private fun TransformerContext.reasoningPreset(): ReasoningPreset =
    assistant.reasoningPresetId
        ?.let { id -> settings.reasoningPresets.firstOrNull { it.id == id } }
        ?: DEFAULT_REASONING_PRESET

private fun List<UIMessage>.transformLastAssistantThinking(
    preset: ReasoningPreset,
    finishOpenReasoning: Boolean,
): List<UIMessage> {
    if (isEmpty() || preset.prefix.isEmpty()) return this

    val lastIndex = indexOfLast { message ->
        message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()
    }
    if (lastIndex < 0) return this

    val message = this[lastIndex]
    val transformed = message.extractThinking(preset, finishOpenReasoning)
    return if (transformed === message) {
        this
    } else {
        toMutableList().also { it[lastIndex] = transformed }
    }
}

private fun UIMessage.extractThinking(
    preset: ReasoningPreset,
    finishOpenReasoning: Boolean,
): UIMessage {
    if (parts.any { it is UIMessagePart.Reasoning }) return this

    var changed = false
    val nextParts = parts.flatMap { part ->
        if (part !is UIMessagePart.Text) return@flatMap listOf(part)
        val extracted = extractReasoning(part.text, preset) ?: return@flatMap listOf(part)
        changed = true
        listOf(
            UIMessagePart.Reasoning(
                reasoning = extracted.reasoning,
                createdAt = createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                finishedAt = if (extracted.closed || finishOpenReasoning) Clock.System.now() else null,
            ),
            part.copy(text = extracted.visibleText),
        )
    }

    return if (changed) copy(parts = nextParts) else this
}

internal data class ReasoningExtraction(
    val reasoning: String,
    val visibleText: String,
    val closed: Boolean,
)

internal fun extractReasoning(
    text: String,
    preset: ReasoningPreset,
): ReasoningExtraction? {
    if (preset.prefix.isEmpty()) return null

    val reasoningParts = mutableListOf<String>()
    val visible = StringBuilder(text.length)
    var cursor = 0
    var closed = true

    while (cursor < text.length) {
        val start = text.indexOf(preset.prefix, startIndex = cursor)
        if (start < 0) {
            visible.append(text, cursor, text.length)
            break
        }

        visible.append(text, cursor, start)
        val reasoningStart = start + preset.prefix.length
        val end = if (preset.suffix.isNotEmpty()) {
            text.indexOf(preset.suffix, startIndex = reasoningStart)
        } else {
            -1
        }

        if (end < 0) {
            reasoningParts += text.substring(reasoningStart).trim()
            closed = false
            cursor = text.length
        } else {
            reasoningParts += text.substring(reasoningStart, end).trim()
            cursor = end + preset.suffix.length
        }
    }

    if (reasoningParts.isEmpty()) return null
    return ReasoningExtraction(
        reasoning = reasoningParts
            .filter { it.isNotBlank() }
            .joinToString(preset.separator.ifEmpty { "\n\n" }),
        visibleText = visible.toString(),
        closed = closed,
    )
}
