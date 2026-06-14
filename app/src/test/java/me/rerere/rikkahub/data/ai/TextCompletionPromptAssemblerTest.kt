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
    fun `instruct chat turns follow SillyTavern wrap and macro formatting`() {
        val history = TextCompletionPromptAssembler.renderHistory(
            messages = listOf(
                UIMessage.user("Hello"),
                UIMessage.assistant("Hi"),
            ),
            instruct = InstructPreset(
                inputSequence = "<|user {{name}} {{char}}|>",
                inputSuffix = "<END {{name}}>",
                outputSequence = "<|assistant {{name}} {{user}}|>",
                namesBehavior = "always",
                wrap = true,
                macro = true,
            ),
            input = TextCompletionAssemblyInput(
                contextPreset = ContextPreset(),
                instructPreset = InstructPreset(),
                userName = "Alice",
                characterName = "Mira",
            ),
        )

        assertEquals(
            """
                <|user Alice Mira|>
                Alice: Hello<END Alice>
                <|assistant Mira Alice|>
                Mira: Hi
            """.trimIndent(),
            history,
        )
    }

    @Test
    fun `story string prefix and suffix follow SillyTavern wrap and macro formatting`() {
        val prompt = TextCompletionPromptAssembler.assemble(
            TextCompletionAssemblyInput(
                contextPreset = ContextPreset(storyString = "{{#if system}}{{system}}{{/if}}{{trim}}"),
                instructPreset = InstructPreset(
                    storyStringPrefix = "<SYSTEM {{name}} for {{char}}>",
                    storyStringSuffix = "</SYSTEM {{user}}>",
                    wrap = true,
                    macro = true,
                ),
                systemPromptPreset = SystemPromptPreset(content = "Act as Mira."),
                userName = "Hero",
                characterName = "Mira",
            )
        )

        assertEquals(
            """
                <SYSTEM System for Mira>
                Act as Mira.</SYSTEM Hero>
            """.trimIndent(),
            prompt,
        )
    }

    @Test
    fun `assemble appends SillyTavern assistant reply cue after user turn`() {
        val prompt = TextCompletionPromptAssembler.assemble(
            TextCompletionAssemblyInput(
                contextPreset = ContextPreset(storyString = ""),
                instructPreset = InstructPreset(
                    inputSequence = "USER",
                    outputSequence = "ASSISTANT",
                    lastOutputSequence = "FINAL ASSISTANT",
                    namesBehavior = "always",
                    wrap = true,
                    macro = true,
                ),
                chatHistory = listOf(UIMessage.user("Hello")),
                userName = "Hero",
                characterName = "Mira",
            )
        )

        assertEquals(
            """
                USER
                Hero: Hello
                FINAL ASSISTANT
                Mira:
            """.trimIndent(),
            prompt,
        )
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

    @Test
    fun `story string replaces SillyTavern user and character macros`() {
        val prompt = TextCompletionPromptAssembler.assemble(
            TextCompletionAssemblyInput(
                contextPreset = ContextPreset(storyString = "{{char}} greets {{user}}{{trim}}"),
                instructPreset = InstructPreset(),
                userName = "Hero",
                characterName = "Mira",
            )
        )

        assertEquals("Mira greets Hero", prompt)
    }

    @Test
    fun `in chat story string is injected by depth and role instead of prepended`() {
        val prompt = TextCompletionPromptAssembler.assemble(
            TextCompletionAssemblyInput(
                contextPreset = ContextPreset(
                    storyString = "{{#if system}}{{system}}{{/if}}{{trim}}",
                    chatStart = "[Start]",
                    storyStringPosition = 1,
                    storyStringDepth = 1,
                    storyStringRole = 0,
                ),
                instructPreset = InstructPreset(
                    inputSequence = "U:",
                    outputSequence = "A:",
                    systemSequence = "S:",
                ),
                systemPromptPreset = SystemPromptPreset(content = "Pinned story"),
                chatHistory = listOf(
                    UIMessage.user("Older"),
                    UIMessage.assistant("Recent"),
                    UIMessage.user("Latest"),
                ),
            )
        )

        assertInOrder(
            prompt,
            "[Start]",
            "U:Older",
            "A:Recent",
            "S:Pinned story",
            "U:Latest",
        )
    }

    private fun assertInOrder(text: String, vararg fragments: String) {
        var cursor = -1
        fragments.forEach { fragment ->
            val index = text.indexOf(fragment)
            assertTrue("Missing fragment: $fragment\n$text", index >= 0)
            assertTrue("Out of order fragment: $fragment\n$text", index > cursor)
            cursor = index
        }
    }

    @Test
    fun `user alignment message is forced before history when last turn is not user`() {
        val prompt = TextCompletionPromptAssembler.assemble(
            TextCompletionAssemblyInput(
                contextPreset = ContextPreset(
                    storyString = "",
                    chatStart = "[Start]",
                ),
                instructPreset = InstructPreset(
                    inputSequence = "U:",
                    firstInputSequence = "FIRST_U:",
                    outputSequence = "A:",
                    userAlignmentMessage = "{{user}} should answer as {{char}}.",
                ),
                chatHistory = listOf(UIMessage.assistant("Greeting.")),
                userName = "Hero",
                characterName = "Mira",
            )
        )

        assertInOrder(
            prompt,
            "[Start]",
            "FIRST_U:Hero should answer as Mira.",
            "A:Greeting.",
        )
    }
}
