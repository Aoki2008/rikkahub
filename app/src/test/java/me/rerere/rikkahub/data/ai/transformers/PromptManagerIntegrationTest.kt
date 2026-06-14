package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.CharacterCard
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PresetPrompt
import me.rerere.rikkahub.data.model.PromptMarkers
import me.rerere.rikkahub.data.model.PromptOrderEntry
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.PromptPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端验证：助手绑定预设后，真实流水线核心 [buildPromptManagerMessages] 能把
 * 角色卡字段 / 世界书(经 collectInjections 激活) / 人设 / 聊天记录 按预设顺序组装成发送列表。
 */
class PromptManagerIntegrationTest {

    private fun textOf(m: UIMessage) =
        m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private val preset = PromptPreset(
        name = "Test Preset",
        prompts = listOf(
            PresetPrompt(identifier = PromptMarkers.MAIN, role = "system", content = "Main rules"),
            PresetPrompt(identifier = PromptMarkers.WORLD_INFO_BEFORE, marker = true),
            PresetPrompt(identifier = PromptMarkers.CHAR_DESCRIPTION, marker = true),
            PresetPrompt(identifier = PromptMarkers.PERSONA_DESCRIPTION, marker = true),
            PresetPrompt(identifier = PromptMarkers.CHAT_HISTORY, marker = true),
            PresetPrompt(identifier = PromptMarkers.JAILBREAK, role = "system", content = "Stay IC"),
        ),
        promptOrder = listOf(
            PromptOrderEntry(PromptMarkers.MAIN, true),
            PromptOrderEntry(PromptMarkers.WORLD_INFO_BEFORE, true),
            PromptOrderEntry(PromptMarkers.CHAR_DESCRIPTION, true),
            PromptOrderEntry(PromptMarkers.PERSONA_DESCRIPTION, true),
            PromptOrderEntry(PromptMarkers.CHAT_HISTORY, true),
            PromptOrderEntry(PromptMarkers.JAILBREAK, true),
        ),
    )

    private val lorebook = Lorebook(
        name = "Lore",
        enabled = true,
        entries = listOf(
            PromptInjection.RegexInjection(
                name = "fact",
                content = "The kingdom is at war.",
                constantActive = true, // 常驻 -> 必激活
                position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
            ),
        ),
    )

    private val persona = Persona(name = "Hero", description = "I am the Hero.", enabled = true)

    private val assistant = Assistant(
        promptPresetId = preset.id,
        characterCard = CharacterCard(description = "A wise mage."),
        lorebookIds = setOf(lorebook.id),
    )

    private val settings = Settings(
        promptPresets = listOf(preset),
        lorebooks = listOf(lorebook),
        personas = listOf(persona),
        selectedPersonaId = persona.id,
    )

    private val incoming = listOf(
        UIMessage.system("legacy system prompt (should be dropped)"),
        UIMessage.user("Hello"),
        UIMessage.assistant("Greetings, traveler."),
    )

    @Test
    fun `bound preset assembles full prompt in order`() {
        val result = buildPromptManagerMessages(assistant, settings, incoming)!!
        assertEquals(
            listOf(
                "Main rules",
                "The kingdom is at war.",   // worldInfoBefore (constant entry activated via collectInjections)
                "A wise mage.",             // charDescription from characterCard
                "I am the Hero.",           // personaDescription from selected persona
                "Hello",                    // chat history preserved...
                "Greetings, traveler.",
                "Stay IC",                  // jailbreak literal block
            ),
            result.map { textOf(it) },
        )
    }

    @Test
    fun `legacy system prompt is replaced not kept`() {
        val result = buildPromptManagerMessages(assistant, settings, incoming)!!
        assertTrue(result.none { textOf(it).contains("legacy system prompt") })
    }

    @Test
    fun `no preset bound returns null (legacy path preserved)`() {
        val plain = assistant.copy(promptPresetId = null)
        assertNull(buildPromptManagerMessages(plain, settings, incoming))
    }

    @Test
    fun `missing preset id returns null`() {
        val dangler = assistant.copy(promptPresetId = kotlin.uuid.Uuid.random())
        assertNull(buildPromptManagerMessages(dangler, settings, incoming))
    }

    @Test
    fun `sampler-only preset keeps legacy messages`() {
        val samplerPreset = PromptPreset(name = "Sampler", temperature = 0.7f)
        val result = buildPromptManagerMessages(
            assistant = assistant.copy(promptPresetId = samplerPreset.id),
            settings = settings.copy(promptPresets = listOf(samplerPreset)),
            messages = incoming,
        )

        assertEquals(incoming, result)
    }

    @Test
    fun `disabled persona is omitted`() {
        val s = settings.copy(personas = listOf(persona.copy(enabled = false)))
        val result = buildPromptManagerMessages(assistant, s, incoming)!!
        assertTrue(result.none { textOf(it) == "I am the Hero." })
    }
}
