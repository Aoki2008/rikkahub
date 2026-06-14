package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.ContextPreset
import me.rerere.rikkahub.data.model.InstructPreset
import me.rerere.rikkahub.data.model.SystemPromptPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCompletionPromptAssemblerTest {
    @Test
    fun `story string renders conditional blocks and variables`() {
        val rendered = renderSillyTavernTemplate(
            template = "{{#if system}}SYS: {{system}}\n{{/if}}{{#if empty}}NO{{/if}}{{description}}{{trim}}",
            values = mapOf("system" to "rules", "empty" to "", "description" to "desc"),
        ).trim()

        assertEquals("SYS: rules\ndesc", rendered)
    }

    @Test
    fun `assemble renders context fields before chat history`() {
        val prompt = TextCompletionPromptAssembler.assemble(
            TextCompletionAssemblyInput(
                contextPreset = ContextPreset(
                    storyString = "{{#if system}}{{system}}\n{{/if}}{{#if wiBefore}}{{wiBefore}}\n{{/if}}{{#if description}}{{description}}\n{{/if}}{{#if persona}}{{persona}}\n{{/if}}{{trim}}",
                    chatStart = "[Chat Start]",
                ),
                instructPreset = InstructPreset(
                    inputSequence = "User: ",
                    outputSequence = "Bot: ",
                ),
                systemPromptPreset = SystemPromptPreset(content = "Act well"),
                description = "Character desc",
                persona = "User persona",
                worldInfoBefore = listOf("Lore A"),
                chatHistory = listOf(
                    UIMessage.user("Hello"),
                    UIMessage.assistant("Hi"),
                ),
            )
        )

        assertTrue(prompt.indexOf("Act well") < prompt.indexOf("Character desc"))
        assertTrue(prompt.indexOf("Character desc") < prompt.indexOf("[Chat Start]"))
        assertTrue(prompt.indexOf("[Chat Start]") < prompt.indexOf("User: Hello"))
        assertTrue(prompt.contains("Bot: Hi"))
        assertFalse(prompt.contains("{{trim}}"))
    }

    @Test
    fun `instruct names behavior prefixes user and character names`() {
        val history = TextCompletionPromptAssembler.renderHistory(
            messages = listOf(UIMessage.user("Hello"), UIMessage.assistant("Hi")),
            instruct = InstructPreset(
                inputSequence = "<|user|>",
                outputSequence = "<|assistant|>",
                namesBehavior = "always",
            ),
            input = TextCompletionAssemblyInput(
                contextPreset = ContextPreset(),
                instructPreset = InstructPreset(),
                userName = "Alice",
                characterName = "Bot",
            ),
        )

        assertTrue(history.contains("<|user|>Alice: Hello"))
        assertTrue(history.contains("<|assistant|>Bot: Hi"))
    }

    @Test
    fun `first and last sequences override normal sequences`() {
        val history = TextCompletionPromptAssembler.renderHistory(
            messages = listOf(
                UIMessage.user("First"),
                UIMessage.user("Last"),
            ),
            instruct = InstructPreset(
                inputSequence = "I:",
                firstInputSequence = "FIRST:",
                lastInputSequence = "LAST:",
            ),
            input = TextCompletionAssemblyInput(
                contextPreset = ContextPreset(),
                instructPreset = InstructPreset(),
            ),
        )

        assertTrue(history.contains("FIRST:First"))
        assertTrue(history.contains("LAST:Last"))
        assertFalse(history.contains("I:First"))
    }

    @Test
    fun `post history is appended after turns`() {
        val prompt = TextCompletionPromptAssembler.assemble(
            TextCompletionAssemblyInput(
                contextPreset = ContextPreset(storyString = "{{#if system}}{{system}}{{/if}}{{trim}}"),
                instructPreset = InstructPreset(inputSequence = "U:"),
                systemPromptPreset = SystemPromptPreset(content = "System", postHistory = "After history"),
                chatHistory = listOf(UIMessage.user("Hello")),
            )
        )

        assertTrue(prompt.indexOf("U:Hello") < prompt.indexOf("After history"))
    }
}
