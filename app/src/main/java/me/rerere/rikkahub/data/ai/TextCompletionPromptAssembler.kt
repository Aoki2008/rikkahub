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
        val formattedStory = if (storyInChat) "" else formatStoryString(story, input)
        val chatHistory = if (storyInChat) {
            insertInChatStoryString(input.chatHistory, story, input.contextPreset)
        } else {
            input.chatHistory
        }.withUserAlignmentMessage(input)
        val history = renderHistory(chatHistory, input.instructPreset, input)
        val replyCue = renderReplyCue(input.instructPreset, input)

        return buildString {
            if (!storyInChat) {
                append(formattedStory)
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
            if (replyCue.isNotBlank()) {
                append(replyCue)
            }
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
                content = text,
                suffix = instruct.inputSuffix,
                name = input.userName,
                wrap = instruct.wrap,
                macro = instruct.macro,
                input = input,
                includeName = shouldIncludeName(instruct),
            )

            MessageRole.ASSISTANT -> renderTurn(
                sequence = when {
                    first && instruct.firstOutputSequence.isNotBlank() -> instruct.firstOutputSequence
                    last && instruct.lastOutputSequence.isNotBlank() -> instruct.lastOutputSequence
                    else -> instruct.outputSequence
                },
                content = text,
                suffix = instruct.outputSuffix,
                name = input.characterName,
                wrap = instruct.wrap,
                macro = instruct.macro,
                input = input,
                includeName = shouldIncludeName(instruct),
            )

            MessageRole.SYSTEM -> renderTurn(
                sequence = when {
                    last && instruct.lastSystemSequence.isNotBlank() -> instruct.lastSystemSequence
                    instruct.systemSameAsUser -> instruct.inputSequence
                    else -> instruct.systemSequence
                },
                content = text,
                suffix = if (instruct.systemSameAsUser) instruct.inputSuffix else instruct.systemSuffix,
                name = "System",
                wrap = instruct.wrap,
                macro = instruct.macro,
                input = input,
                includeName = false,
            )

            else -> text
        }
    }.joinToString("")

    private fun renderTurn(
        sequence: String,
        content: String,
        suffix: String,
        name: String,
        wrap: Boolean,
        macro: Boolean,
        input: TextCompletionAssemblyInput,
        includeName: Boolean,
    ): String {
        val prefix = sequence.renderInstructMacros(input, name, macro)
        var renderedSuffix = suffix.renderInstructMacros(input, name, macro)
        if (renderedSuffix.isEmpty() && wrap) {
            renderedSuffix = "\n"
        }
        val separator = if (wrap) "\n" else ""
        val body = if (includeName && name.isNotBlank()) {
            "$name: $content$renderedSuffix"
        } else {
            "$content$renderedSuffix"
        }
        return listOf(prefix, body).filter { it.isNotEmpty() }.joinToString(separator)
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

    private fun List<UIMessage>.withUserAlignmentMessage(input: TextCompletionAssemblyInput): List<UIMessage> {
        val alignment = input.instructPreset.userAlignmentMessage
            .renderInstructMacros(input, input.userName, input.instructPreset.macro)
            .trim()
        if (alignment.isBlank()) return this
        if (lastOrNull()?.role == MessageRole.USER) return this
        return listOf(UIMessage.user(alignment)) + this
    }

    private fun formatStoryString(story: String, input: TextCompletionAssemblyInput): String {
        if (story.isBlank()) return ""
        val separator = if (input.instructPreset.wrap) "\n" else ""
        var result = story
        if (input.instructPreset.storyStringPrefix.isNotBlank()) {
            val prefix = input.instructPreset.storyStringPrefix.renderInstructMacros(input, "System", macro = true)
            result = prefix + separator + result
        }
        if (input.instructPreset.storyStringSuffix.isNotBlank()) {
            val suffix = input.instructPreset.storyStringSuffix.renderInstructMacros(input, "System", macro = true)
            result += suffix
        }
        return result
    }

    private fun renderReplyCue(instruct: InstructPreset, input: TextCompletionAssemblyInput): String {
        val sequence = (instruct.lastOutputSequence.ifBlank { instruct.outputSequence })
            .renderInstructMacros(input, input.characterName, instruct.macro)
        val separator = if (instruct.wrap) "\n" else ""
        val includeName = shouldIncludeName(instruct) && input.characterName.isNotBlank()
        val text = if (includeName) {
            separator + sequence + separator + "${input.characterName}:"
        } else {
            separator + sequence
        }
        val base = if (instruct.wrap) {
            text.trimEnd()
        } else {
            text
        }
        return base + if (includeName) "" else separator
    }

    private fun shouldIncludeName(instruct: InstructPreset): Boolean = when (instruct.namesBehavior.lowercase()) {
        "always", "1", "2" -> true
        else -> false
    }

    private fun String.renderInstructMacros(
        input: TextCompletionAssemblyInput,
        name: String,
        macro: Boolean,
    ): String {
        if (!macro) return this
        return replace("{{user}}", input.userName, ignoreCase = true)
            .replace("{{char}}", input.characterName, ignoreCase = true)
            .replace("{{name1}}", input.userName, ignoreCase = true)
            .replace("{{name2}}", input.characterName, ignoreCase = true)
            .replace(Regex("""\{\{name\}\}""", RegexOption.IGNORE_CASE), name)
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
    var result = SILLY_TAVERN_IF_BLOCK_REGEX
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

private val SILLY_TAVERN_IF_BLOCK_REGEX =
    Regex("""\{\{#if\s+([A-Za-z0-9_]+)\}\}(.*?)\{\{/if\}\}""", RegexOption.DOT_MATCHES_ALL)
