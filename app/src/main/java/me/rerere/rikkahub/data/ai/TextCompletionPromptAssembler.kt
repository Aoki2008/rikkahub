package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.ContextPreset
import me.rerere.rikkahub.data.model.InstructPreset
import me.rerere.rikkahub.data.model.SystemPromptPreset

data class TextCompletionAssemblyInput(
    val contextPreset: ContextPreset,
    val instructPreset: InstructPreset,
    val systemPromptPreset: SystemPromptPreset? = null,
    val assistantSystemPrompt: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val persona: String = "",
    val worldInfoBefore: List<String> = emptyList(),
    val worldInfoAfter: List<String> = emptyList(),
    val anchorBefore: String = "",
    val anchorAfter: String = "",
    val chatHistory: List<UIMessage> = emptyList(),
    val userName: String = "{{user}}",
    val characterName: String = "{{char}}",
)

object TextCompletionPromptAssembler {
    fun assemble(input: TextCompletionAssemblyInput): String {
        val story = renderStoryString(input)
        val storyInChat = input.contextPreset.storyStringPosition == STORY_STRING_POSITION_IN_CHAT && story.isNotBlank()
        val chatHistory = if (storyInChat) {
            insertInChatStoryString(input.chatHistory, story, input.contextPreset)
        } else {
            input.chatHistory
        }
        val history = renderHistory(chatHistory, input.instructPreset, input)

        return buildString {
            if (!storyInChat) {
                append(input.instructPreset.storyStringPrefix)
                append(story)
                append(input.instructPreset.storyStringSuffix)
            }
            if (input.contextPreset.chatStart.isNotBlank()) {
                appendSeparated(input.contextPreset.chatStart)
            }
            if (history.isNotBlank()) {
                appendSeparated(history)
            }
            input.systemPromptPreset?.postHistory
                ?.takeIf { it.isNotBlank() }
                ?.let { appendSeparated(it) }
        }.trim()
    }

    fun renderStoryString(input: TextCompletionAssemblyInput): String {
        val system = input.systemPromptPreset?.content
            ?.ifBlank { null }
            ?: input.assistantSystemPrompt
        val values = mapOf(
            "system" to system,
            "char" to input.characterName,
            "user" to input.userName,
            "wiBefore" to input.worldInfoBefore.filter { it.isNotBlank() }.joinToString("\n"),
            "loreBefore" to input.worldInfoBefore.filter { it.isNotBlank() }.joinToString("\n"),
            "description" to input.description,
            "personality" to input.personality,
            "scenario" to input.scenario,
            "wiAfter" to input.worldInfoAfter.filter { it.isNotBlank() }.joinToString("\n"),
            "loreAfter" to input.worldInfoAfter.filter { it.isNotBlank() }.joinToString("\n"),
            "persona" to input.persona,
            "anchorBefore" to input.anchorBefore,
            "anchorAfter" to input.anchorAfter,
        )
        return renderSillyTavernTemplate(input.contextPreset.storyString, values).trim()
    }

    fun renderHistory(
        messages: List<UIMessage>,
        instruct: InstructPreset,
        input: TextCompletionAssemblyInput,
    ): String = messages.mapIndexedNotNull { index, message ->
        val text = textOf(message).trim()
        if (text.isBlank()) return@mapIndexedNotNull null
        val first = index == 0
        val last = index == messages.lastIndex
        when (message.role) {
            MessageRole.USER -> renderTurn(
                sequence = when {
                    first && instruct.firstInputSequence.isNotBlank() -> instruct.firstInputSequence
                    last && instruct.lastInputSequence.isNotBlank() -> instruct.lastInputSequence
                    else -> instruct.inputSequence
                },
                content = namePrefix(input.userName, instruct, text),
                suffix = instruct.inputSuffix,
                wrap = instruct.wrap,
            )

            MessageRole.ASSISTANT -> renderTurn(
                sequence = when {
                    first && instruct.firstOutputSequence.isNotBlank() -> instruct.firstOutputSequence
                    last && instruct.lastOutputSequence.isNotBlank() -> instruct.lastOutputSequence
                    else -> instruct.outputSequence
                },
                content = namePrefix(input.characterName, instruct, text),
                suffix = instruct.outputSuffix,
                wrap = instruct.wrap,
            )

            MessageRole.SYSTEM -> renderTurn(
                sequence = when {
                    last && instruct.lastSystemSequence.isNotBlank() -> instruct.lastSystemSequence
                    instruct.systemSameAsUser -> instruct.inputSequence
                    else -> instruct.systemSequence
                },
                content = text,
                suffix = if (instruct.systemSameAsUser) instruct.inputSuffix else instruct.systemSuffix,
                wrap = instruct.wrap,
            )

            else -> text
        }
    }.joinToString("\n").trim()

    private fun renderTurn(
        sequence: String,
        content: String,
        suffix: String,
        wrap: Boolean,
    ): String {
        val body = if (wrap) content.trim() else content
        return "$sequence$body$suffix".trimEnd()
    }

    private fun namePrefix(name: String, instruct: InstructPreset, text: String): String {
        return when (instruct.namesBehavior.lowercase()) {
            "always", "1", "2" -> "$name: $text"
            else -> text
        }
    }

    private fun StringBuilder.appendSeparated(text: String) {
        if (isNotEmpty() && !endsWith("\n")) append('\n')
        append(text)
    }

    private fun textOf(message: UIMessage): String =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private fun insertInChatStoryString(
        messages: List<UIMessage>,
        story: String,
        contextPreset: ContextPreset,
    ): List<UIMessage> {
        val depth = contextPreset.storyStringDepth.coerceAtLeast(0)
        val insertIndex = (messages.size - depth).coerceIn(0, messages.size)
        val storyMessage = when (contextPreset.storyStringRole) {
            STORY_STRING_ROLE_USER -> UIMessage.user(story)
            STORY_STRING_ROLE_ASSISTANT -> UIMessage.assistant(story)
            else -> UIMessage.system(story)
        }
        return messages.take(insertIndex) + storyMessage + messages.drop(insertIndex)
    }
}

private const val STORY_STRING_POSITION_IN_CHAT = 1
private const val STORY_STRING_ROLE_USER = 1
private const val STORY_STRING_ROLE_ASSISTANT = 2

/**
 * Minimal SillyTavern story-string evaluator for context presets.
 *
 * Supports the constructs used by shipped context presets:
 * - {{#if key}}...{{/if}} conditional blocks
 * - {{key}} variable replacement
 * - {{trim}} marker (removed; final caller trims)
 */
internal fun renderSillyTavernTemplate(template: String, values: Map<String, String>): String {
    var result = Regex("""\{\{#if\s+([A-Za-z0-9_]+)}}(.*?)\{\{/if}}""", RegexOption.DOT_MATCHES_ALL)
        .replace(template) { match ->
            val key = match.groupValues[1]
            val body = match.groupValues[2]
            if (values[key].orEmpty().isNotBlank()) body else ""
        }
    values.forEach { (key, value) ->
        result = result.replace("{{$key}}", value)
    }
    return result.replace("{{trim}}", "")
}
