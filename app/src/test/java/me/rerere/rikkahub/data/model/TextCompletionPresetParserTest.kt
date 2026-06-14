package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCompletionPresetParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse context preset maps SillyTavern fields`() {
        val raw = """
            {
              "name":"Adventure",
              "story_string":"{{#if system}}{{system}}{{/if}}{{trim}}",
              "example_separator":"---",
              "chat_start":"[Start]",
              "use_stop_strings":true,
              "names_as_stop_strings":false,
              "story_string_position":1,
              "story_string_depth":3,
              "story_string_role":2,
              "always_force_name2":true,
              "trim_sentences":true,
              "single_line":false
            }
        """.trimIndent()

        val preset = parseSillyTavernContextPreset(json.parseToJsonElement(raw).jsonObject, "Fallback")

        assertEquals("Adventure", preset.name)
        assertEquals("---", preset.exampleSeparator)
        assertEquals("[Start]", preset.chatStart)
        assertTrue(preset.useStopStrings)
        assertFalse(preset.namesAsStopStrings)
        assertEquals(1, preset.storyStringPosition)
        assertEquals(3, preset.storyStringDepth)
        assertEquals(2, preset.storyStringRole)
        assertTrue(preset.alwaysForceName2)
        assertTrue(preset.trimSentences)
        assertFalse(preset.singleLine)
    }

    @Test
    fun `parse instruct preset maps sequences and switches`() {
        val raw = """
            {
              "name":"Alpaca",
              "input_sequence":"### Instruction:\n",
              "output_sequence":"### Response:\n",
              "last_output_sequence":"### Final:\n",
              "system_sequence":"### System:\n",
              "stop_sequence":"###",
              "wrap":false,
              "macro":false,
              "names_behavior":"always",
              "activation_regex":"alpaca",
              "first_output_sequence":"### First:\n",
              "skip_examples":true,
              "output_suffix":"</s>",
              "input_suffix":"\n",
              "system_suffix":"\n",
              "user_alignment_message":"align",
              "system_same_as_user":true,
              "last_system_sequence":"### Last System:\n",
              "first_input_sequence":"### First Input:\n",
              "last_input_sequence":"### Last Input:\n",
              "sequences_as_stop_strings":false,
              "story_string_prefix":"<s>",
              "story_string_suffix":"\n"
            }
        """.trimIndent()

        val preset = parseSillyTavernInstructPreset(json.parseToJsonElement(raw).jsonObject, "Fallback")

        assertEquals("Alpaca", preset.name)
        assertEquals("### Instruction:\n", preset.inputSequence)
        assertEquals("### Response:\n", preset.outputSequence)
        assertEquals("always", preset.namesBehavior)
        assertFalse(preset.wrap)
        assertTrue(preset.skipExamples)
        assertTrue(preset.systemSameAsUser)
        assertFalse(preset.sequencesAsStopStrings)
        assertEquals("<s>", preset.storyStringPrefix)
    }

    @Test
    fun `parse system prompt preset maps content and post history`() {
        val raw = """{"name":"Actor","content":"Act as {{char}}","post_history":"Stay in character"}"""

        val preset = parseSillyTavernSystemPromptPreset(json.parseToJsonElement(raw).jsonObject, "Fallback")

        assertEquals("Actor", preset.name)
        assertEquals("Act as {{char}}", preset.content)
        assertEquals("Stay in character", preset.postHistory)
    }

    @Test
    fun `classify text completion preset file as context preset`() {
        val raw = """
            {
              "story_string":"{{#if system}}{{system}}{{/if}}{{trim}}",
              "example_separator":"",
              "chat_start":"",
              "name":"Alpaca"
            }
        """.trimIndent()

        val imported = parseSillyTavernTextCompletionPreset(
            json.parseToJsonElement(raw).jsonObject,
            "Fallback",
        )

        val context = imported as TextCompletionPresetImport.Context
        assertEquals("Alpaca", context.preset.name)
        assertEquals("{{#if system}}{{system}}{{/if}}{{trim}}", context.preset.storyString)
    }

    @Test
    fun `classify text completion preset file as instruct preset`() {
        val raw = """
            {
              "input_sequence":"### Instruction:",
              "output_sequence":"### Response:",
              "names_behavior":"force",
              "name":"Alpaca"
            }
        """.trimIndent()

        val imported = parseSillyTavernTextCompletionPreset(
            json.parseToJsonElement(raw).jsonObject,
            "Fallback",
        )

        val instruct = imported as TextCompletionPresetImport.Instruct
        assertEquals("Alpaca", instruct.preset.name)
        assertEquals("### Instruction:", instruct.preset.inputSequence)
        assertEquals("### Response:", instruct.preset.outputSequence)
    }

    @Test
    fun `classify text completion preset file as system prompt preset`() {
        val raw = """
            {
              "name":"Actor",
              "content":"Act as {{char}}",
              "post_history":"Stay in character"
            }
        """.trimIndent()

        val imported = parseSillyTavernTextCompletionPreset(
            json.parseToJsonElement(raw).jsonObject,
            "Fallback",
        )

        val systemPrompt = imported as TextCompletionPresetImport.SystemPrompt
        assertEquals("Actor", systemPrompt.preset.name)
        assertEquals("Act as {{char}}", systemPrompt.preset.content)
        assertEquals("Stay in character", systemPrompt.preset.postHistory)
    }

    @Test
    fun `classify blank system prompt preset by field presence`() {
        val raw = """{"name":"Blank","content":"","post_history":""}"""

        val imported = parseSillyTavernTextCompletionPreset(
            json.parseToJsonElement(raw).jsonObject,
            "Fallback",
        )

        val systemPrompt = imported as TextCompletionPresetImport.SystemPrompt
        assertEquals("Blank", systemPrompt.preset.name)
        assertEquals("", systemPrompt.preset.content)
        assertEquals("", systemPrompt.preset.postHistory)
    }

    @Test
    fun `reject unknown text completion preset shape`() {
        val raw = """{"name":"Not a preset","temperature":0.7}"""

        val imported = parseSillyTavernTextCompletionPreset(
            json.parseToJsonElement(raw).jsonObject,
            "Fallback",
        )

        assertTrue(imported is TextCompletionPresetImport.Unknown)
    }
}
