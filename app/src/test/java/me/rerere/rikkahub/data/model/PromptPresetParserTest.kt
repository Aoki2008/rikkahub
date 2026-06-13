package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPresetParserTest {

    // 仿真实酒馆 openai 预设结构（节选）
    private val presetJson = """
        {
          "temperature": 0.9,
          "top_p": 0.95,
          "frequency_penalty": 0.1,
          "presence_penalty": 0.2,
          "openai_max_context": 32000,
          "openai_max_tokens": 800,
          "scenario_format": "[Scenario] {{scenario}}",
          "personality_format": "{{personality}}",
          "wi_format": "{0}",
          "new_chat_prompt": "[Start a new Chat]",
          "new_example_chat_prompt": "[Example Chat]",
          "squash_system_messages": true,
          "names_behavior": 0,
          "prompts": [
            {"name":"Main Prompt","system_prompt":true,"role":"system","content":"Write {{char}}'s reply.","identifier":"main"},
            {"name":"Auxiliary","system_prompt":true,"role":"system","content":"","identifier":"nsfw"},
            {"identifier":"charDescription","name":"Char Description","system_prompt":true,"marker":true},
            {"identifier":"chatHistory","name":"Chat History","system_prompt":true,"marker":true},
            {"name":"Jailbreak","system_prompt":true,"role":"system","content":"Stay in character.","identifier":"jailbreak"}
          ],
          "prompt_order": [
            {"character_id": 99999, "order": [{"identifier":"main","enabled":false}]},
            {"character_id": 100000, "order": [
              {"identifier":"main","enabled":true},
              {"identifier":"charDescription","enabled":true},
              {"identifier":"nsfw","enabled":false},
              {"identifier":"chatHistory","enabled":true},
              {"identifier":"jailbreak","enabled":true}
            ]}
          ]
        }
    """.trimIndent()

    private fun parse() =
        parseSillyTavernPreset(Json.parseToJsonElement(presetJson).jsonObject, "My Preset")

    @Test
    fun `parses name samplers and formats`() {
        val p = parse()
        assertEquals("My Preset", p.name)
        assertEquals(0.9f, p.temperature)
        assertEquals(0.95f, p.topP)
        assertEquals(32000, p.maxContext)
        assertEquals(800, p.maxTokens)
        assertEquals("[Scenario] {{scenario}}", p.scenarioFormat)
        assertEquals("[Example Chat]", p.newExampleChatPrompt)
        assertTrue(p.squashSystemMessages)
    }

    @Test
    fun `parses prompt blocks with markers`() {
        val p = parse()
        assertEquals(5, p.prompts.size)
        val desc = p.prompts.first { it.identifier == "charDescription" }
        assertTrue(desc.marker)
        val main = p.prompts.first { it.identifier == "main" }
        assertEquals("Write {{char}}'s reply.", main.content)
        assertTrue(!main.marker)
    }

    @Test
    fun `picks default character order 100000`() {
        val p = parse()
        // should use the 100000 entry (5 items), not the 99999 one (1 item)
        assertEquals(
            listOf("main", "charDescription", "nsfw", "chatHistory", "jailbreak"),
            p.promptOrder.map { it.identifier }
        )
        assertEquals(false, p.promptOrder.first { it.identifier == "nsfw" }.enabled)
    }

    @Test
    fun `orderedEnabledPrompts respects order and enabled flags`() {
        val p = parse()
        val ordered = p.orderedEnabledPrompts().map { it.identifier }
        // nsfw disabled -> excluded; order preserved
        assertEquals(listOf("main", "charDescription", "chatHistory", "jailbreak"), ordered)
    }
}
