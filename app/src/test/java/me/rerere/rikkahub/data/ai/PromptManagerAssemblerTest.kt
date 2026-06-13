package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.PresetPrompt
import me.rerere.rikkahub.data.model.PromptMarkers
import me.rerere.rikkahub.data.model.PromptOrderEntry
import me.rerere.rikkahub.data.model.PromptPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptManagerAssemblerTest {

    private fun textOf(m: UIMessage) =
        m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private fun preset(
        prompts: List<PresetPrompt>,
        order: List<String>,
        squash: Boolean = false,
    ) = PromptPreset(
        name = "T",
        prompts = prompts,
        promptOrder = order.map { PromptOrderEntry(it, true) },
        squashSystemMessages = squash,
    )

    private fun marker(id: String) = PresetPrompt(identifier = id, marker = true)
    private fun text(id: String, role: String, content: String) =
        PresetPrompt(identifier = id, role = role, content = content)

    @Test
    fun `assembles blocks in prompt_order with markers filled`() {
        val p = preset(
            prompts = listOf(
                text(PromptMarkers.MAIN, "system", "Main rules"),
                marker(PromptMarkers.CHAR_DESCRIPTION),
                marker(PromptMarkers.SCENARIO),
                marker(PromptMarkers.CHAT_HISTORY),
                text(PromptMarkers.JAILBREAK, "system", "Stay in character"),
            ),
            order = listOf(
                PromptMarkers.MAIN, PromptMarkers.CHAR_DESCRIPTION,
                PromptMarkers.SCENARIO, PromptMarkers.CHAT_HISTORY, PromptMarkers.JAILBREAK,
            ),
        )
        val result = PromptManagerAssembler.assemble(
            PromptAssemblyInput(
                preset = p,
                description = "A brave knight.",
                scenario = "A dark forest.",
                chatHistory = listOf(UIMessage.user("Hi"), UIMessage.assistant("Hello")),
            )
        )
        assertEquals(
            listOf("Main rules", "A brave knight.", "A dark forest.", "Hi", "Hello", "Stay in character"),
            result.map { textOf(it) }
        )
        // history keeps roles; surrounding blocks are system
        assertEquals(MessageRole.USER, result[3].role)
        assertEquals(MessageRole.ASSISTANT, result[4].role)
        assertEquals(MessageRole.SYSTEM, result[0].role)
    }

    @Test
    fun `disabled blocks are skipped`() {
        val p = PromptPreset(
            prompts = listOf(text("main", "system", "A"), text("nsfw", "system", "B")),
            promptOrder = listOf(PromptOrderEntry("main", true), PromptOrderEntry("nsfw", false)),
        )
        val result = PromptManagerAssembler.assemble(PromptAssemblyInput(preset = p))
        assertEquals(listOf("A"), result.map { textOf(it) })
    }

    @Test
    fun `blocks not in order are excluded`() {
        val p = PromptPreset(
            prompts = listOf(text("main", "system", "A"), text("extra", "system", "B")),
            promptOrder = listOf(PromptOrderEntry("main", true)),
        )
        val result = PromptManagerAssembler.assemble(PromptAssemblyInput(preset = p))
        assertEquals(listOf("A"), result.map { textOf(it) })
    }

    @Test
    fun `empty markers are omitted`() {
        val p = preset(
            prompts = listOf(marker(PromptMarkers.CHAR_DESCRIPTION), marker(PromptMarkers.SCENARIO)),
            order = listOf(PromptMarkers.CHAR_DESCRIPTION, PromptMarkers.SCENARIO),
        )
        // no description/scenario provided
        val result = PromptManagerAssembler.assemble(PromptAssemblyInput(preset = p))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `personality and scenario formats are applied`() {
        val p = PromptPreset(
            prompts = listOf(marker(PromptMarkers.CHAR_PERSONALITY), marker(PromptMarkers.SCENARIO)),
            promptOrder = listOf(
                PromptOrderEntry(PromptMarkers.CHAR_PERSONALITY, true),
                PromptOrderEntry(PromptMarkers.SCENARIO, true),
            ),
            personalityFormat = "Personality: {{personality}}",
            scenarioFormat = "[Scenario] {{scenario}}",
        )
        val result = PromptManagerAssembler.assemble(
            PromptAssemblyInput(preset = p, personality = "kind", scenario = "forest")
        )
        assertEquals(listOf("Personality: kind", "[Scenario] forest"), result.map { textOf(it) })
    }

    @Test
    fun `world info is joined with wi_format`() {
        val p = preset(
            prompts = listOf(marker(PromptMarkers.WORLD_INFO_BEFORE)),
            order = listOf(PromptMarkers.WORLD_INFO_BEFORE),
        ).copy(wiFormat = "<lore>{0}</lore>")
        val result = PromptManagerAssembler.assemble(
            PromptAssemblyInput(preset = p, worldInfoBefore = listOf("Entry A", "Entry B"))
        )
        assertEquals(listOf("<lore>Entry A\nEntry B</lore>"), result.map { textOf(it) })
    }

    @Test
    fun `dialogue examples are prefixed with new example chat prompt`() {
        val p = preset(
            prompts = listOf(marker(PromptMarkers.DIALOGUE_EXAMPLES)),
            order = listOf(PromptMarkers.DIALOGUE_EXAMPLES),
        ).copy(newExampleChatPrompt = "[Example Chat]")
        val result = PromptManagerAssembler.assemble(
            PromptAssemblyInput(preset = p, exampleMessages = "User: hi\nChar: hello")
        )
        assertEquals(listOf("[Example Chat]\nUser: hi\nChar: hello"), result.map { textOf(it) })
    }

    @Test
    fun `squash merges adjacent system messages`() {
        val p = preset(
            prompts = listOf(
                text("main", "system", "A"),
                text("nsfw", "system", "B"),
                marker(PromptMarkers.CHAT_HISTORY),
            ),
            order = listOf("main", "nsfw", PromptMarkers.CHAT_HISTORY),
            squash = true,
        )
        val result = PromptManagerAssembler.assemble(
            PromptAssemblyInput(preset = p, chatHistory = listOf(UIMessage.user("hi")))
        )
        assertEquals(listOf("A\n\nB", "hi"), result.map { textOf(it) })
        assertEquals(MessageRole.SYSTEM, result[0].role)
    }
}
